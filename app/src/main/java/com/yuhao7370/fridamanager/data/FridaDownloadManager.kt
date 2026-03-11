package com.yuhao7370.fridamanager.data

import com.yuhao7370.fridamanager.data.local.ControllerLogger
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.DownloadTask
import com.yuhao7370.fridamanager.model.DownloadTaskStatus
import com.yuhao7370.fridamanager.model.InstallPhase
import com.yuhao7370.fridamanager.model.RemoteFridaAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FridaDownloadManager(
    private val appScope: CoroutineScope,
    private val versionRepository: FridaVersionRepository,
    private val logger: ControllerLogger
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val taskJobs = ConcurrentHashMap<String, Job>()
    private val taskRequests = ConcurrentHashMap<String, TaskRequest>()
    private val lock = Any()

    fun enqueue(version: String, asset: RemoteFridaAsset): DownloadTask {
        synchronized(lock) {
            val existing = _tasks.value.firstOrNull {
                it.version == version &&
                    it.assetName == asset.name &&
                    !it.isTerminal
            }
            if (existing != null) return existing
        }

        val now = System.currentTimeMillis()
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            version = version,
            assetName = asset.name,
            status = DownloadTaskStatus.QUEUED,
            createdAtMs = now,
            updatedAtMs = now
        )
        taskRequests[task.id] = TaskRequest(version = version, asset = asset)
        appendTask(task)
        startTask(task.id)
        return task
    }

    fun cancel(taskId: String) {
        val job = taskJobs[taskId]
        if (job != null) {
            job.cancel(CancellationException("User canceled download"))
            return
        }
        updateTask(taskId) { current ->
            if (current.isTerminal) current else {
                current.copy(
                    status = DownloadTaskStatus.CANCELED,
                    speedBytesPerSec = 0L,
                    message = null,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun retry(taskId: String): DownloadTask? {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return null
        val request = taskRequests[taskId] ?: return null
        if (!current.isTerminal) return current
        cancel(taskId)
        return enqueue(request.version, request.asset)
    }

    fun clearFinished() {
        val finished = _tasks.value.filter { it.isTerminal }.map { it.id }.toSet()
        _tasks.update { list -> list.filterNot { it.id in finished } }
        finished.forEach { taskId ->
            taskRequests.remove(taskId)
            taskJobs.remove(taskId)
        }
    }

    private fun startTask(taskId: String) {
        val request = taskRequests[taskId] ?: return
        val job = appScope.launch(Dispatchers.IO) {
            logger.log("Download queued version=${request.version} asset=${request.asset.name}")
            var lastSampleBytes = 0L
            var lastSampleMs = System.currentTimeMillis()
            var lastEmitMs = 0L

            val result = versionRepository.installRemoteAsset(
                version = request.version,
                asset = request.asset
            ) { progress ->
                val now = System.currentTimeMillis()
                val deltaBytes = (progress.downloadedBytes - lastSampleBytes).coerceAtLeast(0L)
                val deltaMs = (now - lastSampleMs).coerceAtLeast(1L)
                val speed = if (progress.phase == InstallPhase.DOWNLOADING) {
                    (deltaBytes * 1000L) / deltaMs
                } else {
                    0L
                }
                val shouldEmit = now - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS ||
                    progress.phase != InstallPhase.DOWNLOADING
                if (shouldEmit) {
                    updateTask(taskId) { current ->
                        current.copy(
                            status = mapPhaseToStatus(progress.phase),
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            speedBytesPerSec = speed.coerceAtLeast(0L),
                            message = null,
                            updatedAtMs = now
                        )
                    }
                    lastEmitMs = now
                }
                lastSampleBytes = progress.downloadedBytes
                lastSampleMs = now
            }

            when (result) {
                is AppResult.Success -> {
                    updateTask(taskId) { current ->
                        current.copy(
                            status = DownloadTaskStatus.COMPLETED,
                            speedBytesPerSec = 0L,
                            message = null,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                    logger.log("Download completed version=${request.version}")
                }

                is AppResult.Failure -> {
                    updateTask(taskId) { current ->
                        current.copy(
                            status = DownloadTaskStatus.FAILED,
                            speedBytesPerSec = 0L,
                            message = result.error.message ?: result.error.type.name,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                    logger.log("Download failed version=${request.version}: ${result.error.message}")
                }
            }
        }

        taskJobs[taskId] = job
        job.invokeOnCompletion { cause ->
            taskJobs.remove(taskId)
            if (cause is CancellationException) {
                updateTask(taskId) { current ->
                    if (current.isTerminal) current else {
                        current.copy(
                            status = DownloadTaskStatus.CANCELED,
                            speedBytesPerSec = 0L,
                            message = null,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                }
                logger.log("Download canceled taskId=$taskId version=${request.version}")
            }
        }
    }

    private fun appendTask(task: DownloadTask) {
        _tasks.update { current ->
            val trimmed = (listOf(task) + current).take(MAX_TASKS)
            trimmed
        }
    }

    private fun updateTask(taskId: String, update: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) update(it) else it }
        }
    }

    private fun mapPhaseToStatus(phase: InstallPhase): DownloadTaskStatus {
        return when (phase) {
            InstallPhase.IDLE -> DownloadTaskStatus.QUEUED
            InstallPhase.DOWNLOADING -> DownloadTaskStatus.DOWNLOADING
            InstallPhase.DECOMPRESSING,
            InstallPhase.INSTALLING -> DownloadTaskStatus.INSTALLING

            InstallPhase.COMPLETED -> DownloadTaskStatus.COMPLETED
            InstallPhase.FAILED -> DownloadTaskStatus.FAILED
        }
    }

    private data class TaskRequest(
        val version: String,
        val asset: RemoteFridaAsset
    )

    private companion object {
        const val MAX_TASKS = 60
        const val PROGRESS_EMIT_INTERVAL_MS = 220L
    }
}
