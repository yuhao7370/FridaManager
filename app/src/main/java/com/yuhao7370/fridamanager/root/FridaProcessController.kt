package com.yuhao7370.fridamanager.root

import com.yuhao7370.fridamanager.data.local.ControllerLogger
import com.yuhao7370.fridamanager.data.local.FridaFileLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class ProcessInfo(
    val isRunning: Boolean,
    val pid: Int? = null,
    val message: String? = null
)

class FridaProcessController(
    private val shell: RootShellManager,
    private val fileLayout: FridaFileLayout,
    private val handleStore: ProcessHandleStore,
    private val logger: ControllerLogger
) {
    private val operationMutex = Mutex()

    suspend fun start(version: String, binaryPath: String, host: String, port: Int): ProcessInfo {
        return operationMutex.withLock {
            startLocked(version, binaryPath, host, port)
        }
    }

    suspend fun stop(): ProcessInfo = operationMutex.withLock {
        stopLocked(reason = "api_stop")
    }

    suspend fun restart(version: String, binaryPath: String, host: String, port: Int): ProcessInfo {
        return operationMutex.withLock {
            val stopped = stopLocked(reason = "api_restart")
            if (stopped.isRunning) {
                return@withLock stopped.copy(message = stopped.message ?: "Existing frida-server did not stop cleanly")
            }
            delay(RESTART_COOLDOWN_MS)
            startLocked(version, binaryPath, host, port)
        }
    }

    suspend fun status(): ProcessInfo = operationMutex.withLock {
        val handle = resolveRunningHandle(clearStale = true)
        if (handle == null) {
            ProcessInfo(isRunning = false, pid = null, message = "Stopped")
        } else {
            ProcessInfo(isRunning = true, pid = handle.pid, message = "Running")
        }
    }

    private suspend fun startLocked(
        version: String,
        binaryPath: String,
        host: String,
        port: Int
    ): ProcessInfo {
        val binaryFile = File(binaryPath)
        if (!binaryFile.exists()) {
            return ProcessInfo(false, null, "Binary does not exist: $binaryPath")
        }

        val current = resolveRunningHandle(clearStale = true)
            ?: discoverManagedProcess(binaryPath, version, host, port)?.also { discovered ->
                handleStore.write(discovered)
                logger.log("Process controller adopted existing pid=${discovered.pid} version=$version")
            }
        if (current != null &&
            current.binaryPath == binaryPath &&
            current.host == host &&
            current.port == port
        ) {
            return ProcessInfo(true, current.pid, "Already running")
        }
        if (current != null) {
            val stopped = stopHandle(current, reason = "switch_before_start")
            if (stopped.isRunning) {
                return stopped.copy(message = stopped.message ?: "Failed to stop the previous frida-server instance")
            }
            delay(RESTART_COOLDOWN_MS)
        }

        val launchResult = shell.execScript(
            listOf(
                "binary=${shellQuote(binaryPath)}",
                "host=${shellQuote(host)}",
                "port=${port}",
                "stdout_file=${shellQuote(fileLayout.fridaStdoutLogFile.absolutePath)}",
                "stderr_file=${shellQuote(fileLayout.fridaStderrLogFile.absolutePath)}",
                "if [ ! -f \"\$binary\" ]; then echo \"__FM_ERROR__=missing_binary\"; exit 11; fi",
                "chmod 0755 \"\$binary\" >/dev/null 2>&1 || true",
                "/system/bin/setsid -d \"\$binary\" -l \"\$host:\$port\" >>\"\$stdout_file\" 2>>\"\$stderr_file\" </dev/null &",
                "launcher_pid=\$!",
                "echo \"__FM_LAUNCHED__=1\"",
                "echo \"__FM_LAUNCHER_PID__=\$launcher_pid\""
            )
        )
        if (!launchResult.isSuccess) {
            val message = launchResult.stderr.ifBlank {
                parseTaggedValue(launchResult.stdout, "__FM_ERROR__") ?: "Root shell launch failed"
            }
            logger.log("Process controller start shell failure: $message")
            handleStore.clear()
            return ProcessInfo(false, null, message)
        }

        repeat(START_DISCOVERY_STEPS) {
            val discovered = discoverManagedProcess(binaryPath, version, host, port)
            if (discovered != null) {
                handleStore.write(discovered)
                logger.log("Process controller started pid=${discovered.pid} version=$version")
                return ProcessInfo(true, discovered.pid, "Started")
            }
            delay(START_DISCOVERY_STEP_MS)
        }

        handleStore.clear()
        val message = readRecentFridaError()
            ?: parseStartFailureMessage(binaryPath, launchResult.stdout)
            ?: "frida-server exited during startup"
        logger.log("Process controller start failed version=$version message=$message")
        return ProcessInfo(false, null, message)
    }

    private suspend fun stopLocked(reason: String): ProcessInfo {
        val handle = resolveRunningHandle(clearStale = true)
            ?: handleStore.read()?.let { discoverManagedProcess(it.binaryPath, it.version, it.host, it.port) }
        if (handle == null) {
            return ProcessInfo(false, null, "Already stopped")
        }
        return stopHandle(handle, reason)
    }

    private suspend fun stopHandle(handle: ManagedProcessHandle, reason: String): ProcessInfo {
        val verified = verifyHandle(handle)
        if (verified == null) {
            handleStore.clear()
            return ProcessInfo(false, null, "Already stopped")
        }

        val signalResult = shell.execScript(
            listOf(
                "pid=${handle.pid}",
                "expected_start=${handle.startTimeTicks}",
                "expected_binary=${shellQuote(handle.binaryPath)}",
                "if [ ! -d \"/proc/\$pid\" ]; then echo \"__FM_RESULT__=already_stopped\"; exit 0; fi",
                "actual_start=\$(awk '{print \$22}' \"/proc/\$pid/stat\" 2>/dev/null)",
                "actual_exe=\$(readlink \"/proc/\$pid/exe\" 2>/dev/null)",
                "if [ \"\$actual_start\" != \"\$expected_start\" ]; then echo \"__FM_RESULT__=stale_pid\"; exit 0; fi",
                "if [ \"\$actual_exe\" != \"\$expected_binary\" ] && [ \"\$actual_exe\" != \"\$expected_binary (deleted)\" ]; then echo \"__FM_RESULT__=stale_exe\"; exit 0; fi",
                // Avoid SIGKILL here. This device has shown severe instability under aggressive process termination.
                "kill -15 \"\$pid\" 2>/dev/null",
                "kill_exit=\$?",
                "if [ \"\$kill_exit\" -eq 0 ]; then echo \"__FM_RESULT__=signal_sent\"; else echo \"__FM_RESULT__=kill_failed\"; fi",
                "exit 0"
            )
        )
        val signalOutcome = parseTaggedValue(signalResult.stdout, "__FM_RESULT__")
        if (!signalResult.isSuccess) {
            logger.log("Process controller stop shell failure stderr=${signalResult.stderr}")
        }
        if (signalOutcome == "already_stopped" || signalOutcome == "stale_pid" || signalOutcome == "stale_exe") {
            handleStore.clear()
            return ProcessInfo(false, null, "Already stopped")
        }

        repeat(STOP_WAIT_STEPS) {
            if (verifyHandle(handle) == null) {
                handleStore.clear()
                logger.log("Process controller stopped pid=${handle.pid} version=${handle.version} reason=$reason")
                return ProcessInfo(false, null, "Stopped")
            }
            delay(STOP_WAIT_STEP_MS)
        }
        logger.log(
            "Process controller stop timed out without force kill pid=${handle.pid} version=${handle.version} reason=$reason"
        )
        return ProcessInfo(
            true,
            handle.pid,
            if (signalOutcome == "kill_failed") "Failed to stop frida-server" else "frida-server is still shutting down"
        )
    }

    private suspend fun resolveRunningHandle(clearStale: Boolean): ManagedProcessHandle? {
        val handle = handleStore.read() ?: return null
        val verified = verifyHandle(handle)
        if (verified == null && clearStale) {
            handleStore.clear()
        }
        return verified
    }

    private suspend fun discoverManagedProcess(
        binaryPath: String,
        version: String,
        host: String,
        port: Int
    ): ManagedProcessHandle? {
        val result = shell.execScript(
            listOf(
                "expected_binary=${shellQuote(binaryPath)}",
                "expected_bind=${shellQuote("$host:$port")}",
                "for pid in \$(pidof frida-server 2>/dev/null); do",
                "  cmd=\$(tr '\\000' ' ' < \"/proc/\$pid/cmdline\" 2>/dev/null)",
                "  case \"\$cmd\" in",
                "    *\"\$expected_binary\"* )",
                "      case \"\$cmd\" in",
                "        *\"\$expected_bind\"* )",
                "          start_ticks=\$(awk '{print \$22}' \"/proc/\$pid/stat\" 2>/dev/null)",
                "          echo \"__FM_PID__=\$pid\"",
                "          echo \"__FM_START__=\$start_ticks\"",
                "          echo \"__FM_CMD__=\$cmd\"",
                "          break",
                "          ;;",
                "      esac",
                "      ;;",
                "  esac",
                "done"
            )
        )
        if (!result.isSuccess) return null
        val pid = parseTaggedValue(result.stdout, "__FM_PID__")?.toIntOrNull() ?: return null
        val startTicks = parseTaggedValue(result.stdout, "__FM_START__")?.toLongOrNull() ?: return null
        return ManagedProcessHandle(
            pid = pid,
            startTimeTicks = startTicks,
            version = version,
            binaryPath = binaryPath,
            host = host,
            port = port
        )
    }

    private fun parseStartFailureMessage(binaryPath: String, stdout: String): String? {
        val taggedError = parseTaggedValue(stdout, "__FM_ERROR__")
        if (taggedError == "pid_missing") {
            return "frida-server exited before the process handle was captured"
        }
        if (taggedError != null) {
            return taggedError
        }
        return if (parseTaggedValue(stdout, "__FM_LAUNCHED__") == "1") {
            null
        } else {
            "Failed to parse frida-server launch metadata for $binaryPath"
        }
    }

    private fun readRecentFridaError(): String? {
        val file = fileLayout.fridaStderrLogFile
        if (!file.exists()) return null
        return runCatching {
            file.readLines()
                .takeLast(5)
                .map { it.trim() }
                .lastOrNull { it.isNotEmpty() }
        }.getOrNull()
    }

    private suspend fun verifyHandle(handle: ManagedProcessHandle): ManagedProcessHandle? {
        val probeResult = shell.execScript(
            listOf(
                "pid=${handle.pid}",
                "if [ ! -d \"/proc/\$pid\" ]; then echo \"__FM_STATE__=dead\"; exit 0; fi",
                "actual_start=\$(awk '{print \$22}' \"/proc/\$pid/stat\" 2>/dev/null)",
                "actual_exe=\$(readlink \"/proc/\$pid/exe\" 2>/dev/null)",
                "actual_cmd=\$(tr '\\000' ' ' < \"/proc/\$pid/cmdline\" 2>/dev/null)",
                "echo \"__FM_STATE__=alive\"",
                "echo \"__FM_START__=\$actual_start\"",
                "echo \"__FM_EXE__=\$actual_exe\"",
                "echo \"__FM_CMD__=\$actual_cmd\""
            )
        )
        if (!probeResult.isSuccess) {
            return null
        }
        if (parseTaggedValue(probeResult.stdout, "__FM_STATE__") != "alive") {
            return null
        }

        val actualStart = parseTaggedValue(probeResult.stdout, "__FM_START__")?.toLongOrNull()
        val actualExe = parseTaggedValue(probeResult.stdout, "__FM_EXE__").orEmpty()
        val actualCmd = parseTaggedValue(probeResult.stdout, "__FM_CMD__").orEmpty()
        val binaryMatches = actualExe == handle.binaryPath ||
            actualExe == "${handle.binaryPath} (deleted)" ||
            actualCmd.contains(handle.binaryPath)
        return if (actualStart == handle.startTimeTicks && binaryMatches) {
            handle
        } else {
            null
        }
    }

    private fun parseTaggedValue(output: String, key: String): String? {
        return output.lineSequence()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }

    companion object {
        private const val START_DISCOVERY_STEPS = 20
        private const val START_DISCOVERY_STEP_MS = 120L
        private const val STOP_WAIT_STEPS = 40
        private const val STOP_WAIT_STEP_MS = 150L
        private const val RESTART_COOLDOWN_MS = 350L
    }
}
