package com.yuhao7370.fridamanager.app

import android.content.Context
import androidx.room.Room
import com.yuhao7370.fridamanager.data.AbiRepository
import com.yuhao7370.fridamanager.data.FridaDownloadManager
import com.yuhao7370.fridamanager.data.FridaRuntimeRepository
import com.yuhao7370.fridamanager.data.FridaVersionRepository
import com.yuhao7370.fridamanager.data.LogsRepository
import com.yuhao7370.fridamanager.data.SettingsRepository
import com.yuhao7370.fridamanager.data.local.AppDatabase
import com.yuhao7370.fridamanager.data.local.ControllerLogger
import com.yuhao7370.fridamanager.data.local.FridaFileLayout
import com.yuhao7370.fridamanager.data.local.RemoteReleaseCacheStore
import com.yuhao7370.fridamanager.data.local.RuntimeStateStore
import com.yuhao7370.fridamanager.data.local.VersionBackupStore
import com.yuhao7370.fridamanager.data.remote.GitHubReleaseApi
import com.yuhao7370.fridamanager.data.settings.AppSettingsStore
import com.yuhao7370.fridamanager.domain.ClearLogsUseCase
import com.yuhao7370.fridamanager.domain.ClearFinishedDownloadsUseCase
import com.yuhao7370.fridamanager.domain.CancelFridaDownloadUseCase
import com.yuhao7370.fridamanager.domain.DeleteFridaVersionUseCase
import com.yuhao7370.fridamanager.domain.DetectDeviceAbiUseCase
import com.yuhao7370.fridamanager.domain.DownloadFridaVersionUseCase
import com.yuhao7370.fridamanager.domain.EnqueueFridaDownloadUseCase
import com.yuhao7370.fridamanager.domain.FetchRemoteFridaVersionsUseCase
import com.yuhao7370.fridamanager.domain.GetCachedRemoteFridaVersionsUseCase
import com.yuhao7370.fridamanager.domain.GetInstalledFridaVersionsUseCase
import com.yuhao7370.fridamanager.domain.ImportFridaVersionUseCase
import com.yuhao7370.fridamanager.domain.ObserveRuntimeStatusUseCase
import com.yuhao7370.fridamanager.domain.ObserveSettingsUseCase
import com.yuhao7370.fridamanager.domain.ObserveDownloadTasksUseCase
import com.yuhao7370.fridamanager.domain.RecoverRuntimeStatusUseCase
import com.yuhao7370.fridamanager.domain.ReadLogsUseCase
import com.yuhao7370.fridamanager.domain.RefreshRuntimeStatusUseCase
import com.yuhao7370.fridamanager.domain.RestartFridaServerUseCase
import com.yuhao7370.fridamanager.domain.RetryFridaDownloadUseCase
import com.yuhao7370.fridamanager.domain.SetRuntimeMonitoringUseCase
import com.yuhao7370.fridamanager.domain.StartFridaServerUseCase
import com.yuhao7370.fridamanager.domain.StopFridaServerUseCase
import com.yuhao7370.fridamanager.domain.SwitchActiveFridaVersionUseCase
import com.yuhao7370.fridamanager.domain.UpdateSettingsUseCase
import com.yuhao7370.fridamanager.root.FilePermissionController
import com.yuhao7370.fridamanager.root.FridaProcessController
import com.yuhao7370.fridamanager.root.ProcessHandleStore
import com.yuhao7370.fridamanager.root.RootShellManager
import com.yuhao7370.fridamanager.root.RuntimeProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val fileLayout = FridaFileLayout(context)
    private val stateStore = RuntimeStateStore(fileLayout)
    private val logger = ControllerLogger(fileLayout)
    private val remoteReleaseCacheStore = RemoteReleaseCacheStore(fileLayout.remoteReleasesCacheFile)
    private val versionBackupStore = VersionBackupStore(fileLayout)

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "frida_manager.db"
    ).fallbackToDestructiveMigration().build()

    private val installedVersionDao = database.installedVersionDao()
    private val rootShellManager = RootShellManager(context)
    private val permissionController = FilePermissionController(rootShellManager)
    private val processHandleStore = ProcessHandleStore(fileLayout)
    private val processController = FridaProcessController(
        shell = rootShellManager,
        fileLayout = fileLayout,
        handleStore = processHandleStore,
        logger = logger
    )
    private val runtimeProbe = RuntimeProbe(processController)

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()
    }
    private val gitHubReleaseApi = GitHubReleaseApi(httpClient)
    private val settingsStore = AppSettingsStore(context)

    val settingsRepository = SettingsRepository(settingsStore)
    private val abiRepository = AbiRepository(rootShellManager)
    private val logsRepository = LogsRepository(fileLayout)
    private val versionRepository = FridaVersionRepository(
        context = context,
        gitHubReleaseApi = gitHubReleaseApi,
        okHttpClient = httpClient,
        dao = installedVersionDao,
        fileLayout = fileLayout,
        stateStore = stateStore,
        remoteReleaseCacheStore = remoteReleaseCacheStore,
        versionBackupStore = versionBackupStore,
        permissionController = permissionController,
        logger = logger
    )
    private val runtimeRepository = FridaRuntimeRepository(
        shell = rootShellManager,
        dao = installedVersionDao,
        processController = processController,
        runtimeProbe = runtimeProbe,
        stateStore = stateStore,
        logger = logger
    )
    private val downloadManager = FridaDownloadManager(
        appScope = appScope,
        versionRepository = versionRepository,
        logger = logger
    )

    val detectDeviceAbiUseCase = DetectDeviceAbiUseCase(abiRepository)
    val fetchRemoteFridaVersionsUseCase = FetchRemoteFridaVersionsUseCase(versionRepository)
    val getCachedRemoteFridaVersionsUseCase = GetCachedRemoteFridaVersionsUseCase(versionRepository)
    val getInstalledFridaVersionsUseCase = GetInstalledFridaVersionsUseCase(versionRepository)
    val downloadFridaVersionUseCase = DownloadFridaVersionUseCase(versionRepository)
    val observeDownloadTasksUseCase = ObserveDownloadTasksUseCase(downloadManager)
    val enqueueFridaDownloadUseCase = EnqueueFridaDownloadUseCase(downloadManager)
    val cancelFridaDownloadUseCase = CancelFridaDownloadUseCase(downloadManager)
    val retryFridaDownloadUseCase = RetryFridaDownloadUseCase(downloadManager)
    val clearFinishedDownloadsUseCase = ClearFinishedDownloadsUseCase(downloadManager)
    val importFridaVersionUseCase = ImportFridaVersionUseCase(versionRepository)
    val switchActiveFridaVersionUseCase = SwitchActiveFridaVersionUseCase(versionRepository, runtimeRepository)
    val deleteFridaVersionUseCase = DeleteFridaVersionUseCase(versionRepository, runtimeRepository)
    val startFridaServerUseCase = StartFridaServerUseCase(runtimeRepository)
    val stopFridaServerUseCase = StopFridaServerUseCase(runtimeRepository)
    val restartFridaServerUseCase = RestartFridaServerUseCase(runtimeRepository)
    val refreshRuntimeStatusUseCase = RefreshRuntimeStatusUseCase(runtimeRepository)
    val recoverRuntimeStatusUseCase = RecoverRuntimeStatusUseCase(runtimeRepository)
    val setRuntimeMonitoringUseCase = SetRuntimeMonitoringUseCase(runtimeRepository)
    val observeRuntimeStatusUseCase = ObserveRuntimeStatusUseCase(runtimeRepository)
    val observeSettingsUseCase = ObserveSettingsUseCase(settingsRepository)
    val updateSettingsUseCase = UpdateSettingsUseCase(settingsRepository)
    val readLogsUseCase = ReadLogsUseCase(logsRepository)
    val clearLogsUseCase = ClearLogsUseCase(logsRepository)

    init {
        fileLayout.ensureInitialized()
        appScope.launch {
            versionRepository.restoreFromBackups()
        }
        appScope.launch {
            runCatching { runtimeRepository.recoverStatus() }
        }
        appScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                logsRepository.trimToRetention(settings.logRetentionKb)
            }
        }
    }
}
