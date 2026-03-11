package com.yuhao7370.fridamanager.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhao7370.fridamanager.domain.DetectDeviceAbiUseCase
import com.yuhao7370.fridamanager.domain.ObserveRuntimeStatusUseCase
import com.yuhao7370.fridamanager.domain.ObserveSettingsUseCase
import com.yuhao7370.fridamanager.domain.RefreshRuntimeStatusUseCase
import com.yuhao7370.fridamanager.domain.RestartFridaServerUseCase
import com.yuhao7370.fridamanager.domain.SetRuntimeMonitoringUseCase
import com.yuhao7370.fridamanager.domain.StartFridaServerUseCase
import com.yuhao7370.fridamanager.domain.StopFridaServerUseCase
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.AppSettings
import com.yuhao7370.fridamanager.model.DeviceAbiInfo
import com.yuhao7370.fridamanager.model.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val abiInfo: DeviceAbiInfo? = null,
    val runtime: RuntimeState = RuntimeState(),
    val settings: AppSettings = AppSettings(),
    val inProgress: Boolean = false,
    val message: String? = null
)

class HomeViewModel(
    private val detectDeviceAbi: DetectDeviceAbiUseCase,
    private val refreshRuntimeStatus: RefreshRuntimeStatusUseCase,
    private val setRuntimeMonitoring: SetRuntimeMonitoringUseCase,
    observeRuntimeStatus: ObserveRuntimeStatusUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val startFridaServer: StartFridaServerUseCase,
    private val stopFridaServer: StopFridaServerUseCase,
    private val restartFridaServer: RestartFridaServerUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val abi = detectDeviceAbi()
            _uiState.update { it.copy(abiInfo = abi) }
        }
        viewModelScope.launch {
            observeRuntimeStatus().collect { runtime ->
                _uiState.update { it.copy(runtime = runtime) }
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun start() {
        executeRuntimeAction("Start requested") {
            startFridaServer(uiState.value.settings.defaultHost, uiState.value.settings.defaultPort)
        }
    }

    fun stop() {
        executeRuntimeAction("Stop requested") { stopFridaServer() }
    }

    fun restart() {
        executeRuntimeAction("Restart requested") {
            restartFridaServer(uiState.value.settings.defaultHost, uiState.value.settings.defaultPort)
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        setRuntimeMonitoring(enabled)
        if (enabled) {
            viewModelScope.launch {
                refreshRuntimeStatus()
            }
        }
    }

    override fun onCleared() {
        setRuntimeMonitoring(false)
        super.onCleared()
    }

    private fun executeRuntimeAction(
        defaultMessage: String,
        block: suspend () -> AppResult<RuntimeState>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(inProgress = true, message = null) }
            when (val result = runCatching { block() }.getOrElse { throwable ->
                AppResult.Failure(
                    com.yuhao7370.fridamanager.model.RuntimeError(
                        message = throwable.message ?: "Unexpected runtime failure"
                    )
                )
            }) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        inProgress = false,
                        message = defaultMessage
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        inProgress = false,
                        message = result.error.message ?: result.error.type.name
                    )
                }
            }
        }
    }
}
