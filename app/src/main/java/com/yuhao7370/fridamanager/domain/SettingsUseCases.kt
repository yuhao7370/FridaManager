package com.yuhao7370.fridamanager.domain

import com.yuhao7370.fridamanager.data.SettingsRepository
import com.yuhao7370.fridamanager.model.AppSettings
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.observeSettings()
}

class UpdateSettingsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(settings: AppSettings) = repository.update(settings)
}
