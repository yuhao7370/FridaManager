package com.yuhao7370.fridamanager.data

import com.yuhao7370.fridamanager.data.local.ControllerLogger
import com.yuhao7370.fridamanager.data.local.InstalledVersionDao
import com.yuhao7370.fridamanager.data.local.RuntimeStateStore
import com.yuhao7370.fridamanager.domain.FridaVersionSafetyPolicy
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.RuntimeError
import com.yuhao7370.fridamanager.model.RuntimeErrorType
import com.yuhao7370.fridamanager.model.RuntimeState
import com.yuhao7370.fridamanager.model.RuntimeStatus
import com.yuhao7370.fridamanager.root.FridaProcessController
import com.yuhao7370.fridamanager.root.RootShellManager
import com.yuhao7370.fridamanager.root.RuntimeProbe
import com.yuhao7370.fridamanager.root.RuntimeProbeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class FridaRuntimeRepository(
    private val shell: RootShellManager,
    private val dao: InstalledVersionDao,
    private val processController: FridaProcessController,
    private val runtimeProbe: RuntimeProbe,
    private val stateStore: RuntimeStateStore,
    private val logger: ControllerLogger
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeState = MutableStateFlow(stateStore.read() ?: RuntimeState())
    private var monitoringJob: Job? = null

    fun observeRuntimeState(): StateFlow<RuntimeState> = runtimeState.asStateFlow()

    fun setStatusMonitoringEnabled(enabled: Boolean, intervalMs: Long = 8_000L) {
        if (enabled) {
            if (monitoringJob?.isActive == true) return
            monitoringJob = repoScope.launch {
                runCatching { refreshStatus() }
                while (isActive) {
                    delay(intervalMs)
                    runCatching { refreshStatus() }
                }
            }
        } else {
            monitoringJob?.cancel()
            monitoringJob = null
        }
    }

    suspend fun recoverStatus() {
        refreshStatus()
    }

    suspend fun start(host: String, port: Int): AppResult<RuntimeState> {
        if (!shell.hasRootAccess()) {
            return updateError(
                RuntimeError(RuntimeErrorType.NO_ROOT_ACCESS, "KernelSU root permission is not granted")
            )
        }

        val active = dao.getAll().firstOrNull { it.isActive } ?: return updateError(
            RuntimeError(RuntimeErrorType.MISSING_BINARY, "No active version selected")
        )

        val binaryPath = active.binaryPath
        if (!File(binaryPath).exists()) {
            return updateError(
                RuntimeError(RuntimeErrorType.MISSING_BINARY, "Binary does not exist: $binaryPath")
            )
        }
        val safetyDecision = FridaVersionSafetyPolicy.evaluate(active.version)
        if (!safetyDecision.allowed) {
            return updateError(
                RuntimeError(
                    RuntimeErrorType.INVALID_ASSET,
                    safetyDecision.message ?: "This Frida version is blocked on the current Android version"
                )
            )
        }

        val current = runtimeState.value
        if (current.status == RuntimeStatus.RUNNING && current.activeVersion == active.version) {
            return AppResult.Success(refreshStatus())
        }

        val starting = current.copy(
            status = RuntimeStatus.STARTING,
            host = host,
            port = port,
            activeVersion = active.version,
            activeBinaryPath = binaryPath,
            lastError = null,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(starting)
        logger.log("Start requested version=${active.version} path=$binaryPath host=$host port=$port")

        val startInfo = runCatching {
            processController.start(active.version, binaryPath, host, port)
        }.getOrElse { throwable ->
            return updateError(
                RuntimeError(
                    RuntimeErrorType.PROCESS_FAILED_TO_START,
                    throwable.message ?: "Root supervisor start failed"
                )
            )
        }
        if (!startInfo.isRunning) {
            return updateError(
                RuntimeError(
                    RuntimeErrorType.PROCESS_FAILED_TO_START,
                    startInfo.message ?: "frida-server failed to start"
                )
            )
        }
        val running = starting.copy(
            status = RuntimeStatus.RUNNING,
            pid = startInfo.pid,
            pidStartTimeTicks = null,
            lastError = null,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(running)
        logger.log("Started frida-server pid=${startInfo.pid} version=${active.version}")
        schedulePostStartVerification(
            expectedVersion = active.version,
            host = host,
            port = port
        )
        return AppResult.Success(running)
    }

    suspend fun stop(): AppResult<RuntimeState> {
        val current = runtimeState.value
        val active = dao.getAll().firstOrNull { it.isActive }
        val targetBinaryPath = current.activeBinaryPath ?: active?.binaryPath
        val stopping = current.copy(
            status = RuntimeStatus.STOPPING,
            activeVersion = active?.version ?: current.activeVersion,
            activeBinaryPath = targetBinaryPath,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(stopping)
        logger.log(
            "Stop requested pid=${current.pid} start=${current.pidStartTimeTicks} version=${stopping.activeVersion} path=${targetBinaryPath.orEmpty()}"
        )

        val stopInfo = runCatching {
            processController.stop()
        }.getOrElse { throwable ->
            return updateError(
                RuntimeError(
                    RuntimeErrorType.SHELL_EXECUTION_FAILURE,
                    throwable.message ?: "Root supervisor stop failed"
                )
            )
        }
        if (stopInfo.isRunning) {
            return updateError(
                RuntimeError(
                    RuntimeErrorType.SHELL_EXECUTION_FAILURE,
                    stopInfo.message ?: "frida-server is still running after stop request"
                )
            )
        }
        if (stopInfo.message?.contains("failed", ignoreCase = true) == true) {
            logger.log("Stop returned message=${stopInfo.message}")
        }
        val stopped = stopping.copy(
            status = RuntimeStatus.STOPPED,
            pid = null,
            pidStartTimeTicks = null,
            lastError = null,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(stopped)
        logger.log("Stopped frida-server")
        return AppResult.Success(stopped)
    }

    suspend fun restart(host: String, port: Int): AppResult<RuntimeState> {
        if (!shell.hasRootAccess()) {
            return updateError(
                RuntimeError(RuntimeErrorType.NO_ROOT_ACCESS, "KernelSU root permission is not granted")
            )
        }

        val active = dao.getAll().firstOrNull { it.isActive } ?: return updateError(
            RuntimeError(RuntimeErrorType.MISSING_BINARY, "No active version selected")
        )
        val binaryPath = active.binaryPath
        if (!File(binaryPath).exists()) {
            return updateError(
                RuntimeError(RuntimeErrorType.MISSING_BINARY, "Binary does not exist: $binaryPath")
            )
        }
        val safetyDecision = FridaVersionSafetyPolicy.evaluate(active.version)
        if (!safetyDecision.allowed) {
            return updateError(
                RuntimeError(
                    RuntimeErrorType.INVALID_ASSET,
                    safetyDecision.message ?: "This Frida version is blocked on the current Android version"
                )
            )
        }

        val restarting = runtimeState.value.copy(
            status = RuntimeStatus.STARTING,
            host = host,
            port = port,
            activeVersion = active.version,
            activeBinaryPath = binaryPath,
            lastError = null,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(restarting)
        logger.log("Restart requested version=${active.version} path=$binaryPath host=$host port=$port")

        val restartInfo = runCatching {
            processController.restart(active.version, binaryPath, host, port)
        }.getOrElse { throwable ->
            return updateError(
                RuntimeError(
                    RuntimeErrorType.PROCESS_FAILED_TO_START,
                    throwable.message ?: "Root supervisor restart failed"
                )
            )
        }
        if (!restartInfo.isRunning) {
            return updateError(
                RuntimeError(
                    RuntimeErrorType.PROCESS_FAILED_TO_START,
                    restartInfo.message ?: "frida-server failed to restart"
                )
            )
        }
        val running = restarting.copy(
            status = RuntimeStatus.RUNNING,
            pid = restartInfo.pid,
            pidStartTimeTicks = null,
            lastError = null,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(running)
        logger.log("Restarted frida-server pid=${restartInfo.pid} version=${active.version}")
        schedulePostStartVerification(
            expectedVersion = active.version,
            host = host,
            port = port
        )
        return AppResult.Success(running)
    }

    suspend fun refreshStatus(): RuntimeState {
        val active = dao.getAll().firstOrNull { it.isActive }
        val binaryPath = active?.binaryPath ?: runtimeState.value.activeBinaryPath
        val host = runtimeState.value.host
        val port = runtimeState.value.port
        val current = runtimeState.value
        val probe = runCatching {
            runtimeProbe.probe(host = host, port = port)
        }.getOrElse {
            RuntimeProbeResult(
                isRunning = false,
                pid = null,
                portListening = false
            )
        }
        val refreshedError = if (probe.isRunning) {
            null
        } else {
            current.lastError
        }
        val next = current.copy(
            status = if (probe.isRunning) RuntimeStatus.RUNNING else RuntimeStatus.STOPPED,
            pid = probe.pid,
            pidStartTimeTicks = null,
            host = host,
            port = port,
            activeVersion = active?.version ?: current.activeVersion,
            activeBinaryPath = binaryPath,
            lastError = refreshedError,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(next)
        return next
    }

    private fun emitState(state: RuntimeState) {
        runtimeState.value = state
        stateStore.write(state)
    }

    private fun schedulePostStartVerification(
        expectedVersion: String,
        host: String,
        port: Int
    ) {
        repoScope.launch {
            delay(600L)
            val probe = runCatching {
                runtimeProbe.probe(host = host, port = port, verifyPort = true)
            }.getOrElse { throwable ->
                val error = RuntimeError(
                    RuntimeErrorType.PORT_CHECK_FAILED,
                    throwable.message ?: "Runtime probe failed after start"
                )
                emitState(
                    runtimeState.value.copy(
                        status = RuntimeStatus.ERROR,
                        lastError = error,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                logger.log("Runtime error ${error.type}: ${error.message.orEmpty()}")
                return@launch
            }

            if (!probe.isRunning) {
                val error = RuntimeError(
                    RuntimeErrorType.PROCESS_DIED_IMMEDIATELY,
                    "frida-server exited shortly after start"
                )
                emitState(
                    runtimeState.value.copy(
                        status = RuntimeStatus.ERROR,
                        pid = null,
                        lastError = error,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                logger.log("Runtime error ${error.type}: ${error.message.orEmpty()}")
                return@launch
            }

            if (!probe.portListening) {
                logger.log("Port check did not confirm listening on $port; continuing as best-effort")
            }

            val current = runtimeState.value
            if (current.activeVersion == expectedVersion && current.status == RuntimeStatus.RUNNING) {
                emitState(
                    current.copy(
                        pid = probe.pid ?: current.pid,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun updateError(error: RuntimeError): AppResult.Failure {
        val errored = runtimeState.value.copy(
            status = RuntimeStatus.ERROR,
            lastError = error,
            updatedAtMs = System.currentTimeMillis()
        )
        emitState(errored)
        logger.log("Runtime error ${error.type}: ${error.message.orEmpty()}")
        return AppResult.Failure(error)
    }
}
