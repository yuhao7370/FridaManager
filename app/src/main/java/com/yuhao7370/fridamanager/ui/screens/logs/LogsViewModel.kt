package com.yuhao7370.fridamanager.ui.screens.logs

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.domain.ClearLogsUseCase
import com.yuhao7370.fridamanager.domain.ReadLogsUseCase
import com.yuhao7370.fridamanager.model.LogSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LogsUiState(
    val selectedSource: LogSource = LogSource.CONTROLLER,
    val content: String = "",
    val loading: Boolean = false,
    val autoRefresh: Boolean = true,
    @StringRes val messageRes: Int? = null
)

class LogsViewModel(
    private val readLogs: ReadLogsUseCase,
    private val clearLogs: ClearLogsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var screenActive = false

    init {
        startAutoRefreshLoop()
    }

    fun selectSource(source: LogSource) {
        _uiState.update { it.copy(selectedSource = source) }
        refreshNow()
    }

    fun setAutoRefresh(enabled: Boolean) {
        _uiState.update { it.copy(autoRefresh = enabled) }
        if (enabled) startAutoRefreshLoop() else refreshJob?.cancel()
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
        if (active) {
            startAutoRefreshLoop()
            refreshNow()
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val content = readLogs(uiState.value.selectedSource)
            _uiState.update { it.copy(loading = false, content = content) }
        }
    }

    fun clearSelected() {
        viewModelScope.launch {
            clearLogs(uiState.value.selectedSource)
            _uiState.update { it.copy(messageRes = R.string.logs_cleared) }
            refreshNow()
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(messageRes = null) }
    }

    private fun startAutoRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                if (screenActive && uiState.value.autoRefresh) refreshNow()
                delay(2_500L)
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}
