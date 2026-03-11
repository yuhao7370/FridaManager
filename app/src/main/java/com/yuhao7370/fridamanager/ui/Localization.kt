package com.yuhao7370.fridamanager.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.yuhao7370.fridamanager.model.LanguagePreference
import java.util.Locale

@Composable
fun ProvideLocalizedResources(
    languagePreference: LanguagePreference,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val systemLanguageTags = remember(systemConfiguration) {
        systemConfiguration.locales.toLanguageTags()
    }
    val localizedContext = remember(baseContext, languagePreference, systemLanguageTags) {
        when (languagePreference) {
            LanguagePreference.FOLLOW_SYSTEM -> baseContext
            LanguagePreference.ENGLISH -> baseContext.withAppLocale(Locale.ENGLISH)
            LanguagePreference.SIMPLIFIED_CHINESE -> baseContext.withAppLocale(Locale.SIMPLIFIED_CHINESE)
        }
    }
    val localizedConfiguration = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }
    val layoutDirection = remember(localizedConfiguration) {
        val locale = localizedConfiguration.locales[0] ?: Locale.getDefault()
        if (TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        LocalLayoutDirection provides layoutDirection,
        content = content
    )
}

private fun Context.withAppLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(locale))
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
