package com.yuhao7370.fridamanager.model

enum class InstallSource {
    REMOTE,
    IMPORTED
}

data class InstalledFridaVersion(
    val version: String,
    val binaryPath: String,
    val installedAtMs: Long,
    val source: InstallSource,
    val abiTag: String?,
    val fileSizeBytes: Long,
    val isActive: Boolean,
    val isValid: Boolean
)
