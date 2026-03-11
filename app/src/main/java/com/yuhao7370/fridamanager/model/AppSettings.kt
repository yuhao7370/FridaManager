package com.yuhao7370.fridamanager.model

enum class ThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

enum class LanguagePreference {
    FOLLOW_SYSTEM,
    ENGLISH,
    SIMPLIFIED_CHINESE
}

data class AppSettings(
    val defaultHost: String = "127.0.0.1",
    val defaultPort: Int = 27042,
    val logRetentionKb: Int = 2048,
    val githubApiBaseUrl: String = "https://api.github.com",
    val themePreference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    val languagePreference: LanguagePreference = LanguagePreference.FOLLOW_SYSTEM
)
