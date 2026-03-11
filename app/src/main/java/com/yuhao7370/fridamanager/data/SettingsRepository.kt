package com.yuhao7370.fridamanager.data

import com.yuhao7370.fridamanager.data.settings.AppSettingsStore
import com.yuhao7370.fridamanager.model.AppSettings
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val store: AppSettingsStore) {
    fun observeSettings(): Flow<AppSettings> = store.settingsFlow

    suspend fun update(settings: AppSettings) {
        store.update(settings)
    }
}
