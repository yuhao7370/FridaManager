package com.yuhao7370.fridamanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhao7370.fridamanager.domain.ObserveSettingsUseCase
import com.yuhao7370.fridamanager.domain.UpdateSettingsUseCase
import com.yuhao7370.fridamanager.model.AppSettings
import com.yuhao7370.fridamanager.model.LanguagePreference
import com.yuhao7370.fridamanager.model.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsMessage {
    SAVED
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val message: SettingsMessage? = null
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun updateHost(host: String) = save { it.copy(defaultHost = host.ifBlank { "127.0.0.1" }) }

    fun updatePort(portText: String) {
        val parsed = portText.toIntOrNull()?.coerceIn(1, 65535) ?: uiState.value.settings.defaultPort
        save { it.copy(defaultPort = parsed) }
    }

    fun updateLogRetention(kbText: String) {
        val parsed = kbText.toIntOrNull()?.coerceIn(128, 1024 * 128) ?: uiState.value.settings.logRetentionKb
        save { it.copy(logRetentionKb = parsed) }
    }

    fun updateGithubBaseUrl(url: String) {
        val normalized = url.trim().ifBlank { "https://api.github.com" }
        save { it.copy(githubApiBaseUrl = normalized) }
    }

    fun updateTheme(theme: ThemePreference) = save { it.copy(themePreference = theme) }

    fun updateLanguage(language: LanguagePreference) = save { it.copy(languagePreference = language) }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = transform(uiState.value.settings)
            updateSettings(updated)
            _uiState.update { it.copy(message = SettingsMessage.SAVED) }
        }
    }
}
