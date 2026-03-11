package com.yuhao7370.fridamanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
