package com.yuhao7370.fridamanager.domain

import com.yuhao7370.fridamanager.data.FridaRuntimeRepository
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.RuntimeState
import kotlinx.coroutines.flow.StateFlow

class StartFridaServerUseCase(private val repository: FridaRuntimeRepository) {
    suspend operator fun invoke(host: String, port: Int): AppResult<RuntimeState> =
        repository.start(host, port)
}

class StopFridaServerUseCase(private val repository: FridaRuntimeRepository) {
    suspend operator fun invoke(): AppResult<RuntimeState> = repository.stop()
}

class RestartFridaServerUseCase(private val repository: FridaRuntimeRepository) {
    suspend operator fun invoke(host: String, port: Int): AppResult<RuntimeState> =
        repository.restart(host, port)
}

class RefreshRuntimeStatusUseCase(private val repository: FridaRuntimeRepository) {
    suspend operator fun invoke() = repository.refreshStatus()
}

class SetRuntimeMonitoringUseCase(private val repository: FridaRuntimeRepository) {
    operator fun invoke(enabled: Boolean) = repository.setStatusMonitoringEnabled(enabled)
}

class ObserveRuntimeStatusUseCase(private val repository: FridaRuntimeRepository) {
    operator fun invoke(): StateFlow<RuntimeState> = repository.observeRuntimeState()
}
