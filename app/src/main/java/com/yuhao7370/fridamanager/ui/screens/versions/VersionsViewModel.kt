package com.yuhao7370.fridamanager.ui.screens.versions

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuhao7370.fridamanager.BuildConfig
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.domain.CancelFridaDownloadUseCase
import com.yuhao7370.fridamanager.domain.ClearFinishedDownloadsUseCase
import com.yuhao7370.fridamanager.domain.DeleteFridaVersionUseCase
import com.yuhao7370.fridamanager.domain.DetectDeviceAbiUseCase
import com.yuhao7370.fridamanager.domain.EnqueueFridaDownloadUseCase
import com.yuhao7370.fridamanager.domain.FetchRemoteFridaVersionsUseCase
import com.yuhao7370.fridamanager.domain.FetchRemoteFridaVersionByTagUseCase
import com.yuhao7370.fridamanager.domain.GetCachedRemoteFridaVersionsUseCase
import com.yuhao7370.fridamanager.domain.GetInstalledFridaVersionsUseCase
import com.yuhao7370.fridamanager.domain.ImportFridaVersionUseCase
import com.yuhao7370.fridamanager.domain.ObserveDownloadTasksUseCase
import com.yuhao7370.fridamanager.domain.ObserveSettingsUseCase
import com.yuhao7370.fridamanager.domain.RetryFridaDownloadUseCase
import com.yuhao7370.fridamanager.domain.SwitchActiveFridaVersionUseCase
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.AppSettings
import com.yuhao7370.fridamanager.model.DeviceAbiInfo
import com.yuhao7370.fridamanager.model.DownloadTask
import com.yuhao7370.fridamanager.model.InstallProgress
import com.yuhao7370.fridamanager.model.InstalledFridaVersion
import com.yuhao7370.fridamanager.model.RemoteFridaVersion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VersionsUiState(
    val abiInfo: DeviceAbiInfo? = null,
    val settings: AppSettings = AppSettings(),
    val installed: List<InstalledFridaVersion> = emptyList(),
    val remote: List<RemoteFridaVersion> = emptyList(),
    val downloads: List<DownloadTask> = emptyList(),
    val quickVersionInput: String = "",
    val loadingRemote: Boolean = false,
    val loadingRemoteDebugMessage: String? = null,
    val installProgress: InstallProgress? = null,
    val busyVersion: String? = null,
    val messageRes: Int? = null,
    val message: String? = null
)

class VersionsViewModel(
    private val detectDeviceAbi: DetectDeviceAbiUseCase,
    observeSettingsUseCase: ObserveSettingsUseCase,
    getInstalledFridaVersions: GetInstalledFridaVersionsUseCase,
    private val getCachedRemoteFridaVersions: GetCachedRemoteFridaVersionsUseCase,
    observeDownloadTasks: ObserveDownloadTasksUseCase,
    private val fetchRemoteFridaVersions: FetchRemoteFridaVersionsUseCase,
    private val fetchRemoteFridaVersionByTag: FetchRemoteFridaVersionByTagUseCase,
    private val enqueueFridaDownload: EnqueueFridaDownloadUseCase,
    private val cancelFridaDownload: CancelFridaDownloadUseCase,
    private val retryFridaDownload: RetryFridaDownloadUseCase,
    private val clearFinishedDownloads: ClearFinishedDownloadsUseCase,
    private val importFridaVersion: ImportFridaVersionUseCase,
    private val switchActiveFridaVersion: SwitchActiveFridaVersionUseCase,
    private val deleteFridaVersion: DeleteFridaVersionUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(VersionsUiState())
    val uiState: StateFlow<VersionsUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null
    private var lastCacheBaseUrl: String? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(abiInfo = detectDeviceAbi()) }
        }
        viewModelScope.launch {
            observeSettingsUseCase().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                if (lastCacheBaseUrl != settings.githubApiBaseUrl || uiState.value.remote.isEmpty()) {
                    lastCacheBaseUrl = settings.githubApiBaseUrl
                    loadCachedRemote(settings.githubApiBaseUrl)
                }
            }
        }
        viewModelScope.launch {
            getInstalledFridaVersions().collect { list ->
                _uiState.update { it.copy(installed = list) }
            }
        }
        viewModelScope.launch {
            observeDownloadTasks().collect { tasks ->
                _uiState.update { it.copy(downloads = tasks) }
            }
        }
    }

    fun refreshRemote() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingRemote = true,
                    loadingRemoteDebugMessage = if (BuildConfig.DEBUG) "Starting remote refresh..." else null,
                    messageRes = null,
                    message = null
                )
            }
            when (
                val result = fetchRemoteFridaVersions(
                    uiState.value.settings.githubApiBaseUrl
                ) { progress ->
                    if (BuildConfig.DEBUG) {
                        _uiState.update {
                            it.copy(
                                loadingRemoteDebugMessage = "${progress.message} | pages ${progress.loadedPages}/${progress.totalPages} | releases ${progress.loadedReleases}"
                            )
                        }
                    }
                }
            ) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        loadingRemote = false,
                        loadingRemoteDebugMessage = null,
                        remote = result.data,
                        messageRes = null,
                        message = null
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    val errorMessage = result.error.message ?: result.error.type.name
                    val isTimeout = errorMessage.contains("timed out", ignoreCase = true) ||
                        errorMessage.contains("504", ignoreCase = true) ||
                        errorMessage.contains("gateway timeout", ignoreCase = true)
                    it.copy(
                        loadingRemote = false,
                        loadingRemoteDebugMessage = null,
                        messageRes = if (isTimeout) R.string.versions_remote_timeout_hint else null,
                        message = if (isTimeout) null else errorMessage
                    )
                }
            }
        }
    }

    fun onQuickVersionInputChanged(value: String) {
        _uiState.update { it.copy(quickVersionInput = value.take(MAX_QUICK_INPUT_LENGTH)) }
    }

    fun quickDownloadByVersionInput() {
        val targetVersion = normalizeVersion(uiState.value.quickVersionInput)
        if (targetVersion.isBlank()) {
            _uiState.update { it.copy(messageRes = R.string.versions_quick_missing_version, message = null) }
            return
        }

        val currentMatch = findRemoteVersion(targetVersion, uiState.value.remote)
        if (currentMatch != null) {
            download(currentMatch)
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingRemote = true,
                    loadingRemoteDebugMessage = if (BuildConfig.DEBUG) "Resolving exact release tag..." else null,
                    messageRes = null,
                    message = null
                )
            }
            when (val result = fetchRemoteFridaVersionByTag(uiState.value.settings.githubApiBaseUrl, targetVersion)) {
                is AppResult.Success -> {
                    download(result.data)
                    _uiState.update {
                        it.copy(
                            loadingRemote = false,
                            loadingRemoteDebugMessage = null,
                            remote = listOf(result.data) + it.remote.filterNot { remoteVersion ->
                                normalizeVersion(remoteVersion.version).equals(targetVersion, ignoreCase = true)
                            },
                            messageRes = null,
                            message = null
                        )
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        loadingRemote = false,
                        loadingRemoteDebugMessage = null,
                        messageRes = R.string.versions_quick_not_found,
                        message = null
                    )
                }
            }
        }
    }

    fun download(version: RemoteFridaVersion) {
        val abiTag = uiState.value.abiInfo?.fridaAssetTag ?: run {
            _uiState.update { it.copy(messageRes = null, message = "Unable to detect device ABI") }
            return
        }
        val asset = version.matchingAsset(abiTag) ?: run {
            _uiState.update { it.copy(messageRes = null, message = "No matching asset for ABI $abiTag") }
            return
        }
        val task = enqueueFridaDownload(version.version, asset)
        _uiState.update {
            it.copy(
                messageRes = null,
                message = "Queued ${task.version}"
            )
        }
    }

    fun cancelDownload(taskId: String) {
        cancelFridaDownload(taskId)
    }

    fun retryDownload(taskId: String) {
        retryFridaDownload(taskId)
    }

    fun clearFinishedDownloadTasks() {
        clearFinishedDownloads()
        _uiState.update { it.copy(messageRes = R.string.versions_downloads_cleared, message = null) }
    }

    fun cancelInstall() {
        importJob?.cancel()
        importJob = null
        _uiState.update {
            it.copy(
                busyVersion = null,
                installProgress = null,
                messageRes = null,
                message = "Install canceled"
            )
        }
    }

    fun import(uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(busyVersion = "import", installProgress = null, messageRes = null, message = null) }
            val result = importFridaVersion(uri, userVersion = null) { progress ->
                _uiState.update { it.copy(installProgress = progress) }
            }
            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        busyVersion = null,
                        installProgress = null,
                        messageRes = null,
                        message = "Imported ${result.data.version}"
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        busyVersion = null,
                        installProgress = null,
                        messageRes = null,
                        message = result.error.message ?: result.error.type.name
                    )
                }
            }
        }
    }

    fun switch(version: InstalledFridaVersion) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyVersion = version.version, messageRes = null, message = null) }
            val settings = uiState.value.settings
            when (val result = switchActiveFridaVersion(version.version, settings.defaultHost, settings.defaultPort)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        busyVersion = null,
                        messageRes = null,
                        message = "Active version: ${version.version}"
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        busyVersion = null,
                        messageRes = null,
                        message = result.error.message ?: result.error.type.name
                    )
                }
            }
        }
    }

    fun delete(version: InstalledFridaVersion) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyVersion = version.version, messageRes = null, message = null) }
            when (val result = deleteFridaVersion(version.version)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        busyVersion = null,
                        messageRes = null,
                        message = "Deleted ${version.version}"
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        busyVersion = null,
                        messageRes = null,
                        message = result.error.message ?: result.error.type.name
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(messageRes = null, message = null) }
    }

    private suspend fun loadCachedRemote(baseUrl: String) {
        val cached = getCachedRemoteFridaVersions(baseUrl)
        _uiState.update {
            it.copy(
                remote = cached,
                loadingRemote = false,
                loadingRemoteDebugMessage = null
            )
        }
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
    }

    private fun findRemoteVersion(version: String, list: List<RemoteFridaVersion>): RemoteFridaVersion? {
        val normalized = normalizeVersion(version)
        return list.firstOrNull { normalizeVersion(it.version).equals(normalized, ignoreCase = true) }
    }

    private companion object {
        const val MAX_QUICK_INPUT_LENGTH = 32
    }
}
