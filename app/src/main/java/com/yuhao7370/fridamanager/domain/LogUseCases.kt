package com.yuhao7370.fridamanager.domain

import com.yuhao7370.fridamanager.data.LogsRepository
import com.yuhao7370.fridamanager.model.LogSource

class ReadLogsUseCase(private val repository: LogsRepository) {
    suspend operator fun invoke(source: LogSource): String = repository.readLog(source)
}

class ClearLogsUseCase(private val repository: LogsRepository) {
    suspend operator fun invoke(source: LogSource? = null) = repository.clear(source)
}
