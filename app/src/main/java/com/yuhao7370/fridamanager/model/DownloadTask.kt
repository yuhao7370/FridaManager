package com.yuhao7370.fridamanager.model

enum class DownloadTaskStatus {
    QUEUED,
    DOWNLOADING,
    INSTALLING,
    COMPLETED,
    FAILED,
    CANCELED
}

data class DownloadTask(
    val id: String,
    val version: String,
    val assetName: String,
    val status: DownloadTaskStatus,
    val phase: InstallPhase = InstallPhase.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val message: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val isTerminal: Boolean
        get() = status == DownloadTaskStatus.COMPLETED ||
            status == DownloadTaskStatus.FAILED ||
            status == DownloadTaskStatus.CANCELED
}
