package com.yuhao7370.fridamanager.model

sealed class AppResult<out T> {
    data class Success<T>(
        val data: T,
        val fromCache: Boolean = false
    ) : AppResult<T>()
    data class Failure(val error: RuntimeError) : AppResult<Nothing>()
}
