package com.yuhao7370.fridamanager.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.staticCompositionLocalOf
import com.yuhao7370.fridamanager.app.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

val LocalHostActivity = staticCompositionLocalOf<ComponentActivity> {
    error("Host activity not provided")
}
