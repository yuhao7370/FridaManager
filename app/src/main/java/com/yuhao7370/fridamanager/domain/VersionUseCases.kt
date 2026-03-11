package com.yuhao7370.fridamanager.domain

import android.net.Uri
import com.yuhao7370.fridamanager.data.FridaRuntimeRepository
import com.yuhao7370.fridamanager.data.FridaVersionRepository
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.InstallProgress
import com.yuhao7370.fridamanager.model.InstalledFridaVersion
import com.yuhao7370.fridamanager.model.RemoteFridaAsset
import com.yuhao7370.fridamanager.model.RemoteFridaVersion
import kotlinx.coroutines.flow.Flow

class FetchRemoteFridaVersionsUseCase(private val repository: FridaVersionRepository) {
    suspend operator fun invoke(apiBaseUrl: String): AppResult<List<RemoteFridaVersion>> =
        repository.fetchRemoteVersions(apiBaseUrl)
}

class GetCachedRemoteFridaVersionsUseCase(private val repository: FridaVersionRepository) {
    suspend operator fun invoke(apiBaseUrl: String): List<RemoteFridaVersion> =
        repository.getCachedRemoteVersions(apiBaseUrl)
}

class GetInstalledFridaVersionsUseCase(private val repository: FridaVersionRepository) {
    operator fun invoke(): Flow<List<InstalledFridaVersion>> = repository.observeInstalledVersions()
}

class DownloadFridaVersionUseCase(private val repository: FridaVersionRepository) {
    suspend operator fun invoke(
        version: String,
        asset: RemoteFridaAsset,
        onProgress: suspend (InstallProgress) -> Unit
    ): AppResult<InstalledFridaVersion> = repository.installRemoteAsset(version, asset, onProgress)
}

class ImportFridaVersionUseCase(private val repository: FridaVersionRepository) {
    suspend operator fun invoke(
        uri: Uri,
        userVersion: String?,
        onProgress: suspend (InstallProgress) -> Unit
    ): AppResult<InstalledFridaVersion> = repository.importFromUri(uri, userVersion, onProgress)
}

class SwitchActiveFridaVersionUseCase(
    private val versionRepository: FridaVersionRepository,
    private val runtimeRepository: FridaRuntimeRepository
) {
    suspend operator fun invoke(version: String, host: String, port: Int): AppResult<Unit> {
        val runtimeState = runtimeRepository.observeRuntimeState().value
        val wasRunning = runtimeState.status == com.yuhao7370.fridamanager.model.RuntimeStatus.RUNNING
        if (wasRunning) {
            val stopResult = runtimeRepository.stop()
            if (stopResult is AppResult.Failure) return AppResult.Failure(stopResult.error)
        }

        val switchResult = versionRepository.setActiveVersion(version)
        if (switchResult is AppResult.Failure) return switchResult

        if (wasRunning) {
            val startResult = runtimeRepository.start(host, port)
            if (startResult is AppResult.Failure) return AppResult.Failure(startResult.error)
        }
        return AppResult.Success(Unit)
    }
}

class DeleteFridaVersionUseCase(
    private val versionRepository: FridaVersionRepository,
    private val runtimeRepository: FridaRuntimeRepository
) {
    suspend operator fun invoke(version: String): AppResult<Unit> {
        val runtime = runtimeRepository.observeRuntimeState().value
        if (runtime.status == com.yuhao7370.fridamanager.model.RuntimeStatus.RUNNING &&
            runtime.activeVersion == version
        ) {
            return AppResult.Failure(
                com.yuhao7370.fridamanager.model.RuntimeError(
                    type = com.yuhao7370.fridamanager.model.RuntimeErrorType.SHELL_EXECUTION_FAILURE,
                    message = "Stop frida-server before deleting active running version"
                )
            )
        }
        return versionRepository.deleteVersion(version)
    }
}
