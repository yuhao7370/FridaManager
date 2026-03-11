package com.yuhao7370.fridamanager.model

enum class InstallPhase {
    IDLE,
    DOWNLOADING,
    DECOMPRESSING,
    INSTALLING,
    COMPLETED,
    FAILED
}

data class InstallProgress(
    val phase: InstallPhase = InstallPhase.IDLE,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null
)
