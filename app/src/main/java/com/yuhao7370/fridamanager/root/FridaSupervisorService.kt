package com.yuhao7370.fridamanager.root

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.yuhao7370.fridamanager.data.local.ControllerLogger
import com.yuhao7370.fridamanager.data.local.FridaFileLayout
import com.yuhao7370.fridamanager.data.local.RuntimeStateStore
import com.yuhao7370.fridamanager.model.RuntimeError
import com.yuhao7370.fridamanager.model.RuntimeErrorType
import com.yuhao7370.fridamanager.model.RuntimeState
import com.yuhao7370.fridamanager.model.RuntimeStatus
import com.yuhao7370.fridamanager.root.ipc.IFridaSupervisorService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FridaSupervisorService : RootService() {
    private val lock = Any()

    private lateinit var fileLayout: FridaFileLayout
    private lateinit var logger: ControllerLogger
    private lateinit var stateStore: RuntimeStateStore

    private var process: Process? = null
    private var processVersion: String? = null
    private var processBinaryPath: String? = null
    private var processHost: String = DEFAULT_HOST
    private var processPort: Int = DEFAULT_PORT
    private var lastError: RuntimeError? = null
    private var stopRequested = false

    private val binder = object : IFridaSupervisorService.Stub() {
        override fun start(version: String, binaryPath: String, host: String, port: Int) =
            synchronized(lock) { startLocked(version, binaryPath, host, port) }

        override fun stop() = synchronized(lock) { stopLocked(reason = "api_stop") }

        override fun restart(version: String, binaryPath: String, host: String, port: Int) =
            synchronized(lock) {
                stopLocked(reason = "api_restart")
                startLocked(version, binaryPath, host, port)
            }

        override fun getStatus() = synchronized(lock) { currentInfoLocked() }
    }

    override fun onCreate() {
        super.onCreate()
        fileLayout = FridaFileLayout(this)
        logger = ControllerLogger(fileLayout)
        stateStore = RuntimeStateStore(fileLayout)
        fileLayout.ensureInitialized()
        clearTransientHandle()
        Log.i(TAG, "Root supervisor created")
        logger.log("Root supervisor initialized")
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startLocked(version: String, binaryPath: String, host: String, port: Int) =
        runCatching {
            syncProcessLocked()
            val targetFile = File(binaryPath)
            if (!targetFile.exists()) {
                return@runCatching failure(
                    RuntimeErrorType.MISSING_BINARY,
                    "Binary does not exist: $binaryPath"
                )
            }
            if (!targetFile.canExecute() && !targetFile.setExecutable(true, false)) {
                return@runCatching failure(
                    RuntimeErrorType.BINARY_NOT_EXECUTABLE,
                    "Binary is not executable: $binaryPath"
                )
            }

            val current = process
            if (current?.isAlive == true &&
                processBinaryPath == binaryPath &&
                processHost == host &&
                processPort == port
            ) {
                return@runCatching success(current, "Already running")
            }

            if (current?.isAlive == true) {
                stopLocked(reason = "switch_before_start")
            }

            processVersion = version
            processBinaryPath = binaryPath
            processHost = host
            processPort = port
            lastError = null
            stopRequested = false
            persist(
                status = RuntimeStatus.STARTING,
                pid = null,
                error = null
            )
            Log.i(TAG, "Starting frida version=$version path=$binaryPath host=$host port=$port")
            logger.log("Supervisor start requested version=$version path=$binaryPath host=$host port=$port")

            val launched = ProcessBuilder(binaryPath, "-l", "$host:$port")
                .directory(targetFile.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(fileLayout.fridaStdoutLogFile))
                .redirectError(ProcessBuilder.Redirect.appendTo(fileLayout.fridaStderrLogFile))
                .start()

            process = launched
            val launchedPid = resolvePid(launched)
            if (launchedPid != null) {
                fileLayout.pidFile.writeText(launchedPid.toString())
            }
            val alive = waitForStart(launched, host, port)
            if (!alive) {
                val exitCode = runCatching { launched.exitValue() }.getOrNull()
                clearRuntimeHandleLocked()
                val message = "frida-server exited during startup${exitCode?.let { " code=$it" }.orEmpty()}"
                lastError = RuntimeError(RuntimeErrorType.PROCESS_DIED_IMMEDIATELY, message)
                persist(status = RuntimeStatus.ERROR, pid = null, error = lastError)
                logger.log("Supervisor start failed version=$version message=$message")
                return@runCatching ProcessInfo(false, null, message)
            }

            startWatcher(launched)
            persist(
                status = RuntimeStatus.RUNNING,
                pid = launchedPid,
                error = null
            )
            Log.i(TAG, "Started frida pid=${launchedPid ?: -1} version=$version")
            logger.log("Supervisor started frida-server pid=${launchedPid ?: -1} version=$version")
            success(launched)
        }.getOrElse { throwable ->
            clearRuntimeHandleLocked()
            val message = throwable.message ?: "Root supervisor failed to start process"
            lastError = RuntimeError(RuntimeErrorType.PROCESS_FAILED_TO_START, message)
            persist(status = RuntimeStatus.ERROR, pid = null, error = lastError)
            logger.log("Supervisor exception during start: $message")
            ProcessInfo(false, null, message)
        }.let(SupervisorBundleCodec::encode)

    private fun stopLocked(reason: String) = runCatching {
        syncProcessLocked()
        val target = process
        if (target == null) {
            clearRuntimeHandleLocked()
            persist(status = RuntimeStatus.STOPPED, pid = null, error = null)
            logger.log("Supervisor stop requested with no running process reason=$reason")
            return@runCatching ProcessInfo(false, null, "Already stopped")
        }

        stopRequested = true
        val targetPid = resolvePid(target)
        persist(status = RuntimeStatus.STOPPING, pid = targetPid, error = null)
        Log.i(TAG, "Stopping frida pid=${targetPid ?: -1} version=${processVersion.orEmpty()} reason=$reason")
        logger.log("Supervisor stop requested pid=${targetPid ?: -1} version=${processVersion.orEmpty()} reason=$reason")
        target.destroy()
        if (!target.waitFor(STOP_GRACE_MS, TimeUnit.MILLISECONDS)) {
            logger.log("Supervisor force-stopping pid=${targetPid ?: -1} after grace timeout")
            target.destroyForcibly()
            target.waitFor(STOP_FORCE_MS, TimeUnit.MILLISECONDS)
        }

        val alive = target.isAlive
        clearRuntimeHandleLocked()
        if (alive) {
            val error = RuntimeError(
                RuntimeErrorType.SHELL_EXECUTION_FAILURE,
                "frida-server did not stop cleanly"
            )
            lastError = error
            persist(status = RuntimeStatus.ERROR, pid = null, error = error)
            Log.e(TAG, "Stop failed pid=${targetPid ?: -1} version=${processVersion.orEmpty()}")
            logger.log("Supervisor stop failed pid=${targetPid ?: -1} version=${processVersion.orEmpty()}")
            ProcessInfo(false, null, error.message)
        } else {
            persist(status = RuntimeStatus.STOPPED, pid = null, error = null)
            Log.i(TAG, "Stopped frida version=${processVersion.orEmpty()}")
            logger.log("Supervisor stopped frida-server version=${processVersion.orEmpty()}")
            ProcessInfo(false, null, "Stopped")
        }
    }.getOrElse { throwable ->
        clearRuntimeHandleLocked()
        val message = throwable.message ?: "Root supervisor failed to stop process"
        lastError = RuntimeError(RuntimeErrorType.SHELL_EXECUTION_FAILURE, message)
        persist(status = RuntimeStatus.ERROR, pid = null, error = lastError)
        logger.log("Supervisor exception during stop: $message")
        ProcessInfo(false, null, message)
    }.let(SupervisorBundleCodec::encode)

    private fun currentInfoLocked() = runCatching {
        syncProcessLocked()
        val target = process
        if (target?.isAlive == true) {
            val pid = resolvePid(target)
            persist(status = RuntimeStatus.RUNNING, pid = pid, error = null)
            success(target)
        } else {
            persist(status = RuntimeStatus.STOPPED, pid = null, error = lastError)
            ProcessInfo(false, null, lastError?.message)
        }
    }.getOrElse { throwable ->
        val message = throwable.message ?: "Root supervisor status failed"
        ProcessInfo(false, null, message)
    }.let(SupervisorBundleCodec::encode)

    private fun success(process: Process, message: String? = null) =
        ProcessInfo(true, resolvePid(process), message)

    private fun failure(type: RuntimeErrorType, message: String): ProcessInfo {
        lastError = RuntimeError(type, message)
        persist(status = RuntimeStatus.ERROR, pid = null, error = lastError)
        logger.log("Supervisor failure $type: $message")
        return ProcessInfo(false, null, message)
    }

    private fun waitForStart(target: Process, host: String, port: Int): Boolean {
        repeat(START_WAIT_STEPS) {
            if (!target.isAlive) return false
            if (isPortListening(host, port)) return true
            Thread.sleep(START_WAIT_STEP_MS)
        }
        return target.isAlive
    }

    private fun isPortListening(host: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), PORT_CHECK_TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)
    }

    private fun startWatcher(target: Process) {
        thread(
            start = true,
            isDaemon = true,
            name = "frida-supervisor-watcher"
        ) {
            val exitCode = runCatching { target.waitFor() }.getOrDefault(Int.MIN_VALUE)
            synchronized(lock) {
                if (process !== target) return@synchronized
                val version = processVersion
                clearRuntimeHandleLocked()
                if (stopRequested) {
                    persist(status = RuntimeStatus.STOPPED, pid = null, error = null)
                    logger.log("Supervisor observed expected exit version=${version.orEmpty()} code=$exitCode")
                } else {
                    val error = RuntimeError(
                        RuntimeErrorType.PROCESS_DIED_IMMEDIATELY,
                        "frida-server exited unexpectedly code=$exitCode"
                    )
                    lastError = error
                    persist(status = RuntimeStatus.ERROR, pid = null, error = error)
                    logger.log("Supervisor observed unexpected exit version=${version.orEmpty()} code=$exitCode")
                }
            }
        }
    }

    private fun syncProcessLocked() {
        if (process?.isAlive == true) return
        if (process != null) {
            clearRuntimeHandleLocked()
        }
    }

    private fun clearRuntimeHandleLocked() {
        process = null
        stopRequested = false
        if (fileLayout.pidFile.exists()) {
            fileLayout.pidFile.delete()
        }
    }

    private fun clearTransientHandle() {
        synchronized(lock) {
            clearRuntimeHandleLocked()
            val previous = stateStore.read()
            if (previous?.status == RuntimeStatus.RUNNING ||
                previous?.status == RuntimeStatus.STARTING ||
                previous?.status == RuntimeStatus.STOPPING
            ) {
                persist(
                    status = RuntimeStatus.STOPPED,
                    pid = null,
                    error = previous.lastError
                )
            }
        }
    }

    private fun persist(status: RuntimeStatus, pid: Int?, error: RuntimeError?) {
        val snapshot = RuntimeState(
            status = status,
            pid = pid,
            pidStartTimeTicks = null,
            host = processHost,
            port = processPort,
            activeVersion = processVersion,
            activeBinaryPath = processBinaryPath,
            lastError = error,
            updatedAtMs = System.currentTimeMillis()
        )
        stateStore.write(snapshot)
    }

    companion object {
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 27042
        private const val START_WAIT_STEPS = 12
        private const val START_WAIT_STEP_MS = 150L
        private const val STOP_GRACE_MS = 1500L
        private const val STOP_FORCE_MS = 1000L
        private const val PORT_CHECK_TIMEOUT_MS = 250
        private const val TAG = "FridaSupervisorSvc"
    }

    private fun resolvePid(process: Process): Int? {
        runCatching {
            val method = Process::class.java.getMethod("pid")
            val pid = method.invoke(process) as? Long
            if (pid != null) return pid.toInt()
        }
        runCatching {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            val value = field.get(process)
            when (value) {
                is Int -> value
                is Long -> value.toInt()
                else -> null
            }?.let { return it }
        }
        return null
    }
}
