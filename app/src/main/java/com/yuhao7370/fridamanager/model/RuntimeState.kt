package com.yuhao7370.fridamanager.model

import kotlinx.serialization.Serializable

@Serializable
data class RuntimeState(
    val status: RuntimeStatus = RuntimeStatus.STOPPED,
    val pid: Int? = null,
    val pidStartTimeTicks: Long? = null,
    val host: String = "127.0.0.1",
    val port: Int = 27042,
    val activeVersion: String? = null,
    val activeBinaryPath: String? = null,
    val lastError: RuntimeError? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)
