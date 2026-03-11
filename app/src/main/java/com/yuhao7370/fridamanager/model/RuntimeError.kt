package com.yuhao7370.fridamanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeErrorType {
    NONE,
    NO_ROOT_ACCESS,
    SHELL_EXECUTION_FAILURE,
    GITHUB_REQUEST_FAILURE,
    DOWNLOAD_FAILURE,
    DECOMPRESSION_FAILURE,
    INVALID_ASSET,
    ABI_MISMATCH,
    MISSING_BINARY,
    BINARY_NOT_EXECUTABLE,
    PROCESS_FAILED_TO_START,
    PROCESS_DIED_IMMEDIATELY,
    PORT_CHECK_FAILED,
    IMPORT_PARSE_FAILURE,
    FILE_PERMISSION_FAILURE
}

@Serializable
data class RuntimeError(
    val type: RuntimeErrorType = RuntimeErrorType.NONE,
    val message: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isError: Boolean get() = type != RuntimeErrorType.NONE
}
