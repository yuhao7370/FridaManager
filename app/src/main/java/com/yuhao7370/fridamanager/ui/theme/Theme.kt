package com.yuhao7370.fridamanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.yuhao7370.fridamanager.model.ThemePreference

private val LightColors = lightColorScheme(
    primary = MiBlue,
    onPrimary = Color.White,
    primaryContainer = MiSurfaceBlue,
    onPrimaryContainer = MiBlueDeep,
    secondary = MiCyan,
    onSecondary = Color.White,
    tertiary = MiMint,
    onTertiary = Color.White,
    background = MiBackgroundLight,
    onBackground = MiTextPrimaryLight,
    surface = MiSurfaceLight,
    onSurface = MiTextPrimaryLight,
    surfaceVariant = MiSurfaceLightAlt,
    onSurfaceVariant = MiTextSecondaryLight,
    outline = Color(0x14000000),
    outlineVariant = Color(0x0F000000),
    error = MiDanger,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = MiBlueDark,
    onPrimary = Color(0xFF08172F),
    primaryContainer = Color(0xFF17345D),
    onPrimaryContainer = Color(0xFFD6E5FF),
    secondary = MiCyanDark,
    onSecondary = Color(0xFF08222D),
    tertiary = Color(0xFF48CFBE),
    onTertiary = Color(0xFF032823),
    background = MiBackgroundDark,
    onBackground = MiTextPrimaryDark,
    surface = MiSurfaceDark,
    onSurface = MiTextPrimaryDark,
    surfaceVariant = MiSurfaceDarkAlt,
    onSurfaceVariant = MiTextSecondaryDark,
    outline = Color(0x1FFFFFFF),
    outlineVariant = Color(0x12FFFFFF),
    error = Color(0xFFFF8B8B),
    onError = Color(0xFF3B0000)
)

@Composable
fun MiuiTheme(
    preference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (preference) {
        ThemePreference.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    CompositionLocalProvider(LocalSpacing provides MiuiSpacing()) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = MiuiTypography,
            shapes = MiuiShapes,
            content = content
        )
    }
}

object MiTheme {
    val spacing: MiuiSpacing
        @Composable get() = LocalSpacing.current
}
