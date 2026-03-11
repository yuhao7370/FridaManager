package com.yuhao7370.fridamanager.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuhao7370.fridamanager.model.AppSettings
import com.yuhao7370.fridamanager.model.LanguagePreference
import com.yuhao7370.fridamanager.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsStore(private val context: Context) {
    private object Keys {
        val defaultHost = stringPreferencesKey("default_host")
        val defaultPort = intPreferencesKey("default_port")
        val logRetentionKb = intPreferencesKey("log_retention_kb")
        val githubApiBaseUrl = stringPreferencesKey("github_api_base_url")
        val themePreference = stringPreferencesKey("theme_preference")
        val languagePreference = stringPreferencesKey("language_preference")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultHost = prefs[Keys.defaultHost] ?: "127.0.0.1",
            defaultPort = prefs[Keys.defaultPort] ?: 27042,
            logRetentionKb = prefs[Keys.logRetentionKb] ?: 2048,
            githubApiBaseUrl = prefs[Keys.githubApiBaseUrl] ?: "https://api.github.com",
            themePreference = runCatching {
                ThemePreference.valueOf(
                    prefs[Keys.themePreference] ?: ThemePreference.FOLLOW_SYSTEM.name
                )
            }.getOrDefault(ThemePreference.FOLLOW_SYSTEM),
            languagePreference = runCatching {
                LanguagePreference.valueOf(
                    prefs[Keys.languagePreference] ?: LanguagePreference.FOLLOW_SYSTEM.name
                )
            }.getOrDefault(LanguagePreference.FOLLOW_SYSTEM)
        )
    }

    suspend fun update(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.defaultHost] = settings.defaultHost
            prefs[Keys.defaultPort] = settings.defaultPort
            prefs[Keys.logRetentionKb] = settings.logRetentionKb
            prefs[Keys.githubApiBaseUrl] = settings.githubApiBaseUrl
            prefs[Keys.themePreference] = settings.themePreference.name
            prefs[Keys.languagePreference] = settings.languagePreference.name
        }
    }
}
