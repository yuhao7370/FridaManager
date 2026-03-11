package com.yuhao7370.fridamanager.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuhao7370.fridamanager.app.FridaViewModelFactory
import com.yuhao7370.fridamanager.model.AppSettings
import com.yuhao7370.fridamanager.ui.components.MiBottomNavItem
import com.yuhao7370.fridamanager.ui.components.MiBottomNavigationBar
import com.yuhao7370.fridamanager.ui.screens.about.AboutScreen
import com.yuhao7370.fridamanager.ui.screens.home.HomeScreen
import com.yuhao7370.fridamanager.ui.screens.home.HomeViewModel
import com.yuhao7370.fridamanager.ui.screens.logs.LogsScreen
import com.yuhao7370.fridamanager.ui.screens.logs.LogsViewModel
import com.yuhao7370.fridamanager.ui.screens.settings.SettingsScreen
import com.yuhao7370.fridamanager.ui.screens.settings.SettingsViewModel
import com.yuhao7370.fridamanager.ui.screens.versions.VersionsScreen
import com.yuhao7370.fridamanager.ui.screens.versions.VersionsViewModel
import com.yuhao7370.fridamanager.ui.theme.MiuiTheme

@Composable
fun FridaManagerRoot(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val settings by container.observeSettingsUseCase().collectAsState(initial = null)
    val resolvedSettings = settings ?: AppSettings()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val vmFactory = FridaViewModelFactory(container)
    val bottomRoutes = listOf(
        AppRoute.Home,
        AppRoute.Versions,
        AppRoute.Logs,
        AppRoute.Settings
    )
    var selectedBottomRoute by rememberSaveable { mutableStateOf(AppRoute.Home.route) }
    var pendingBottomRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val showBottomBar = bottomRoutes.any { it.route == currentDestination?.route }

    LaunchedEffect(currentDestination?.route) {
        val route = currentDestination?.route ?: return@LaunchedEffect
        if (bottomRoutes.any { it.route == route }) {
            if (pendingBottomRoute == null || pendingBottomRoute == route) {
                selectedBottomRoute = route
                pendingBottomRoute = null
            }
        }
    }

    ProvideLocalizedResources(languagePreference = resolvedSettings.languagePreference) {
        MiuiTheme(preference = resolvedSettings.themePreference) {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        MiBottomNavigationBar(
                            items = bottomRoutes.map { route ->
                                MiBottomNavItem(
                                    label = stringResource(route.labelRes),
                                    icon = when (route) {
                                        AppRoute.Home -> Icons.Rounded.Home
                                        AppRoute.Versions -> Icons.AutoMirrored.Rounded.List
                                        AppRoute.Logs -> Icons.Rounded.Terminal
                                        AppRoute.Settings -> Icons.Rounded.Settings
                                        AppRoute.About -> Icons.Rounded.Settings
                                    },
                                    selected = selectedBottomRoute == route.route,
                                    onClick = {
                                        if (selectedBottomRoute == route.route &&
                                            currentDestination?.route == route.route
                                        ) {
                                            return@MiBottomNavItem
                                        }
                                        selectedBottomRoute = route.route
                                        pendingBottomRoute = route.route
                                        navController.navigate(route.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            ) { padding ->
                NavHost(
                    modifier = modifier.padding(bottom = padding.calculateBottomPadding()),
                    navController = navController,
                    startDestination = AppRoute.Home.route,
                    enterTransition = {
                        bottomTabEnterTransition(
                            initialRoute = initialState.destination.route,
                            targetRoute = targetState.destination.route,
                            bottomRoutes = bottomRoutes
                        )
                    },
                    exitTransition = {
                        bottomTabExitTransition(
                            initialRoute = initialState.destination.route,
                            targetRoute = targetState.destination.route,
                            bottomRoutes = bottomRoutes
                        )
                    },
                    popEnterTransition = {
                        bottomTabEnterTransition(
                            initialRoute = initialState.destination.route,
                            targetRoute = targetState.destination.route,
                            bottomRoutes = bottomRoutes
                        )
                    },
                    popExitTransition = {
                        bottomTabExitTransition(
                            initialRoute = initialState.destination.route,
                            targetRoute = targetState.destination.route,
                            bottomRoutes = bottomRoutes
                        )
                    }
                ) {
                    composable(AppRoute.Home.route) {
                        val vm: HomeViewModel = viewModel(factory = vmFactory)
                        HomeScreen(viewModel = vm)
                    }
                    composable(AppRoute.Versions.route) {
                        val vm: VersionsViewModel = viewModel(factory = vmFactory)
                        VersionsScreen(viewModel = vm)
                    }
                    composable(AppRoute.Logs.route) {
                        val vm: LogsViewModel = viewModel(factory = vmFactory)
                        LogsScreen(viewModel = vm)
                    }
                    composable(AppRoute.Settings.route) {
                        val vm: SettingsViewModel = viewModel(factory = vmFactory)
                        SettingsScreen(
                            viewModel = vm,
                            onOpenAbout = { navController.navigate(AppRoute.About.route) }
                        )
                    }
                    composable(AppRoute.About.route) {
                        AboutScreen()
                    }
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<*>.bottomTabEnterTransition(
    initialRoute: String?,
    targetRoute: String?,
    bottomRoutes: List<AppRoute>
) = if (initialRoute in bottomRoutes.map { it.route } && targetRoute in bottomRoutes.map { it.route }) {
    val initialIndex = bottomRoutes.indexOfFirst { it.route == initialRoute }
    val targetIndex = bottomRoutes.indexOfFirst { it.route == targetRoute }
    if (targetIndex >= initialIndex) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(durationMillis = 260)
        ) + fadeIn(animationSpec = tween(durationMillis = 220))
    } else {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(durationMillis = 260)
        ) + fadeIn(animationSpec = tween(durationMillis = 220))
    }
} else {
    fadeIn(animationSpec = tween(durationMillis = 180))
}

private fun AnimatedContentTransitionScope<*>.bottomTabExitTransition(
    initialRoute: String?,
    targetRoute: String?,
    bottomRoutes: List<AppRoute>
) = if (initialRoute in bottomRoutes.map { it.route } && targetRoute in bottomRoutes.map { it.route }) {
    val initialIndex = bottomRoutes.indexOfFirst { it.route == initialRoute }
    val targetIndex = bottomRoutes.indexOfFirst { it.route == targetRoute }
    if (targetIndex >= initialIndex) {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(durationMillis = 260)
        ) + fadeOut(animationSpec = tween(durationMillis = 180))
    } else {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(durationMillis = 260)
        ) + fadeOut(animationSpec = tween(durationMillis = 180))
    }
} else {
    fadeOut(animationSpec = tween(durationMillis = 140))
}
