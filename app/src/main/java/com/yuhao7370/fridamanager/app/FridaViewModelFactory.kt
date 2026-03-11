package com.yuhao7370.fridamanager.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yuhao7370.fridamanager.ui.screens.home.HomeViewModel
import com.yuhao7370.fridamanager.ui.screens.logs.LogsViewModel
import com.yuhao7370.fridamanager.ui.screens.settings.SettingsViewModel
import com.yuhao7370.fridamanager.ui.screens.versions.VersionsViewModel

class FridaViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
                detectDeviceAbi = container.detectDeviceAbiUseCase,
                refreshRuntimeStatus = container.refreshRuntimeStatusUseCase,
                setRuntimeMonitoring = container.setRuntimeMonitoringUseCase,
                observeRuntimeStatus = container.observeRuntimeStatusUseCase,
                observeSettings = container.observeSettingsUseCase,
                startFridaServer = container.startFridaServerUseCase,
                stopFridaServer = container.stopFridaServerUseCase,
                restartFridaServer = container.restartFridaServerUseCase
            ) as T

            modelClass.isAssignableFrom(VersionsViewModel::class.java) -> VersionsViewModel(
                detectDeviceAbi = container.detectDeviceAbiUseCase,
                observeSettingsUseCase = container.observeSettingsUseCase,
                getInstalledFridaVersions = container.getInstalledFridaVersionsUseCase,
                getCachedRemoteFridaVersions = container.getCachedRemoteFridaVersionsUseCase,
                fetchRemoteFridaVersions = container.fetchRemoteFridaVersionsUseCase,
                observeDownloadTasks = container.observeDownloadTasksUseCase,
                enqueueFridaDownload = container.enqueueFridaDownloadUseCase,
                cancelFridaDownload = container.cancelFridaDownloadUseCase,
                retryFridaDownload = container.retryFridaDownloadUseCase,
                clearFinishedDownloads = container.clearFinishedDownloadsUseCase,
                importFridaVersion = container.importFridaVersionUseCase,
                switchActiveFridaVersion = container.switchActiveFridaVersionUseCase,
                deleteFridaVersion = container.deleteFridaVersionUseCase
            ) as T

            modelClass.isAssignableFrom(LogsViewModel::class.java) -> LogsViewModel(
                readLogs = container.readLogsUseCase,
                clearLogs = container.clearLogsUseCase
            ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                observeSettings = container.observeSettingsUseCase,
                updateSettings = container.updateSettingsUseCase
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
