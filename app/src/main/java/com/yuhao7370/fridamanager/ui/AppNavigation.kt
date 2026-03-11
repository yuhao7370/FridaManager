package com.yuhao7370.fridamanager.ui

import androidx.annotation.StringRes
import com.yuhao7370.fridamanager.R

sealed class AppRoute(
    val route: String,
    @StringRes val labelRes: Int
) {
    data object Home : AppRoute("home", R.string.nav_home)
    data object Versions : AppRoute("versions", R.string.nav_versions)
    data object Logs : AppRoute("logs", R.string.nav_logs)
    data object Settings : AppRoute("settings", R.string.nav_settings)
    data object About : AppRoute("about", R.string.nav_about)
}
