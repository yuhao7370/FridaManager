package com.yuhao7370.fridamanager.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yuhao7370.fridamanager.data.local.ControllerLogger
import com.yuhao7370.fridamanager.data.local.FridaFileLayout
import com.yuhao7370.fridamanager.data.local.InstalledVersionDao
import com.yuhao7370.fridamanager.data.local.InstalledVersionEntity
import com.yuhao7370.fridamanager.data.local.RemoteReleaseCacheStore
import com.yuhao7370.fridamanager.data.local.RuntimeStateStore
import com.yuhao7370.fridamanager.data.local.VersionBackupStore
import com.yuhao7370.fridamanager.data.local.toModel
import com.yuhao7370.fridamanager.data.remote.FridaReleaseMapper
import com.yuhao7370.fridamanager.data.remote.GitHubReleaseFetchResult
import com.yuhao7370.fridamanager.data.remote.GitHubReleaseFetchProgress
import com.yuhao7370.fridamanager.data.remote.GitHubReleaseApi
import com.yuhao7370.fridamanager.data.remote.awaitResponse
import com.yuhao7370.fridamanager.model.AppResult
import com.yuhao7370.fridamanager.model.InstallPhase
import com.yuhao7370.fridamanager.model.InstallProgress
import com.yuhao7370.fridamanager.model.InstallSource
import com.yuhao7370.fridamanager.model.InstalledFridaVersion
import com.yuhao7370.fridamanager.model.RemoteFridaAsset
import com.yuhao7370.fridamanager.model.RemoteFridaVersion
import com.yuhao7370.fridamanager.model.RuntimeError
import com.yuhao7370.fridamanager.model.RuntimeErrorType
import com.yuhao7370.fridamanager.root.FilePermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.IOException

class FridaVersionRepository(
    private val context: Context,
    private val gitHubReleaseApi: GitHubReleaseApi,
    private val okHttpClient: OkHttpClient,
    private val dao: InstalledVersionDao,
    private val fileLayout: FridaFileLayout,
    private val stateStore: RuntimeStateStore,
    private val remoteReleaseCacheStore: RemoteReleaseCacheStore,
    private val versionBackupStore: VersionBackupStore,
    private val permissionController: FilePermissionController,
    private val logger: ControllerLogger
) {
    fun observeInstalledVersions(): Flow<List<InstalledFridaVersion>> = dao.observeAll().map { entities ->
        entities.map { entity ->
            entity.toModel(isValid = File(entity.binaryPath).exists())
        }
    }

    suspend fun getInstalledVersions(): List<InstalledFridaVersion> = dao.getAll().map { entity ->
        entity.toModel(isValid = File(entity.binaryPath).exists())
    }

    suspend fun getCachedRemoteVersions(apiBaseUrl: String): List<RemoteFridaVersion> {
        return withContext(Dispatchers.IO) {
            remoteReleaseCacheStore.load(apiBaseUrl).orEmpty()
        }
    }

    suspend fun fetchRemoteVersions(
        apiBaseUrl: String,
        onProgress: (GitHubReleaseFetchProgress) -> Unit = {}
    ): AppResult<List<RemoteFridaVersion>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cachedSnapshot = remoteReleaseCacheStore.loadExactSnapshot(apiBaseUrl)
                when (
                    val fetchResult = gitHubReleaseApi.fetchFridaReleases(
                        baseUrl = apiBaseUrl,
                        cachedEtag = cachedSnapshot?.etag,
                        knownVersions = cachedSnapshot?.versions.orEmpty().map { it.version }.toSet(),
                        onProgress = onProgress
                    )
                ) {
                    GitHubReleaseFetchResult.NotModified -> {
                        AppResult.Success(cachedSnapshot?.versions.orEmpty(), fromCache = true)
                    }

                    is GitHubReleaseFetchResult.Updated -> {
                        val fetched = FridaReleaseMapper.mapToRemoteVersions(fetchResult.releases)
                        val merged = mergeRemoteVersions(
                            fresh = fetched,
                            cached = cachedSnapshot?.versions.orEmpty()
                        )
                        if (merged.isNotEmpty()) {
                            remoteReleaseCacheStore.save(apiBaseUrl, merged, fetchResult.etag)
                        }
                        AppResult.Success(merged)
                    }
                }
            }.getOrElse { throwable ->
                AppResult.Failure(
                    RuntimeError(
                        type = RuntimeErrorType.GITHUB_REQUEST_FAILURE,
                        message = throwable.message
                    )
                )
            }
        }
    }

    suspend fun fetchRemoteVersionByTag(apiBaseUrl: String, version: String): AppResult<RemoteFridaVersion> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val release = gitHubReleaseApi.fetchReleaseByTag(apiBaseUrl, version)
                    ?: return@runCatching AppResult.Failure(
                        RuntimeError(
                            type = RuntimeErrorType.GITHUB_REQUEST_FAILURE,
                            message = "Version not found in remote releases"
                        )
                    )
                val mapped = FridaReleaseMapper.mapToRemoteVersions(listOf(release)).firstOrNull()
                    ?: return@runCatching AppResult.Failure(
                        RuntimeError(
                            type = RuntimeErrorType.INVALID_ASSET,
                            message = "No supported frida-server asset in release ${release.tagName}"
                        )
                    )
                AppResult.Success(mapped)
            }.getOrElse { throwable ->
                AppResult.Failure(
                    RuntimeError(
                        type = RuntimeErrorType.GITHUB_REQUEST_FAILURE,
                        message = throwable.message
                    )
                )
            }
        }
    }

    suspend fun installRemoteAsset(
        version: String,
        asset: RemoteFridaAsset,
        onProgress: suspend (InstallProgress) -> Unit
    ): AppResult<InstalledFridaVersion> {
        return withContext(Dispatchers.IO) {
            runCatching {
                fileLayout.ensureInitialized()
                val tempDownload = File(fileLayout.cacheDownloadsDir, "${version}_${asset.name}")
                downloadAsset(
                    url = asset.downloadUrl,
                    targetFile = tempDownload,
                    onProgress = onProgress
                )

                ensureActive()
                val binarySource = if (tempDownload.name.endsWith(".xz")) {
                    onProgress(InstallProgress(phase = InstallPhase.DECOMPRESSING, message = "Decompressing .xz package"))
                    val decompressed = File(fileLayout.cacheDownloadsDir, "${version}_frida-server.bin")
                    decompressXz(tempDownload, decompressed)
                    decompressed
                } else {
                    tempDownload
                }

                if (binarySource.length() <= 0L) {
                    throw IllegalStateException("Downloaded binary is empty")
                }

                ensureActive()
                onProgress(InstallProgress(phase = InstallPhase.INSTALLING, message = "Installing binary"))
                val installed = installBinary(
                    version = FridaReleaseMapper.normalizeVersion(version),
                    sourceFile = binarySource,
                    source = InstallSource.REMOTE,
                    abiTag = asset.abiTag
                )
                onProgress(InstallProgress(phase = InstallPhase.COMPLETED, message = "Installed ${installed.version}"))
                AppResult.Success(installed)
            }.getOrElse { throwable ->
                logger.log("Install failed for $version: ${throwable.message}")
                AppResult.Failure(
                    RuntimeError(
                        type = when {
                            throwable is IOException -> RuntimeErrorType.DOWNLOAD_FAILURE
                            throwable.message?.contains("empty", ignoreCase = true) == true -> RuntimeErrorType.INVALID_ASSET
                            else -> RuntimeErrorType.SHELL_EXECUTION_FAILURE
                        },
                        message = throwable.message
                    )
                )
            }
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        userVersion: String?,
        onProgress: suspend (InstallProgress) -> Unit
    ): AppResult<InstalledFridaVersion> {
        return withContext(Dispatchers.IO) {
            runCatching {
                fileLayout.ensureInitialized()
                val displayName = queryDisplayName(context.contentResolver, uri)
                    ?: uri.lastPathSegment
                    ?: "imported-${System.currentTimeMillis()}"
                val tempFile = File(fileLayout.cacheDownloadsDir, "import_${System.currentTimeMillis()}_${displayName}")
                copyUriToFile(uri, tempFile)
                val inferredVersion = extractVersion(displayName)
                    ?: userVersion
                    ?: "imported-${System.currentTimeMillis()}"

                ensureActive()
                val binarySource = if (displayName.endsWith(".xz", ignoreCase = true)) {
                    onProgress(InstallProgress(phase = InstallPhase.DECOMPRESSING, message = "Decompressing import"))
                    val decompressed = File(fileLayout.cacheDownloadsDir, "import_${System.currentTimeMillis()}_binary")
                    decompressXz(tempFile, decompressed)
                    decompressed
                } else {
                    tempFile
                }
                if (binarySource.length() <= 0L) {
                    throw IllegalStateException("Imported file is empty")
                }
                ensureActive()
                val installed = installBinary(
                    version = FridaReleaseMapper.normalizeVersion(inferredVersion),
                    sourceFile = binarySource,
                    source = InstallSource.IMPORTED,
                    abiTag = null
                )
                onProgress(InstallProgress(phase = InstallPhase.COMPLETED, message = "Imported ${installed.version}"))
                AppResult.Success(installed)
            }.getOrElse { throwable ->
                logger.log("Import failed: ${throwable.message}")
                AppResult.Failure(
                    RuntimeError(
                        type = RuntimeErrorType.IMPORT_PARSE_FAILURE,
                        message = throwable.message
                    )
                )
            }
        }
    }

    suspend fun setActiveVersion(version: String): AppResult<Unit> = runCatching {
        val entity = dao.getByVersion(version) ?: throw IllegalStateException("Version not installed")
        val file = File(entity.binaryPath)
        if (!file.exists()) throw IllegalStateException("Binary file does not exist")
        if (!file.canRead()) throw IllegalStateException("Binary file is not readable")
        dao.clearActive()
        dao.markActive(version)
        stateStore.writeCurrentVersion(version)
        logger.log("Switched active version to $version")
        AppResult.Success(Unit)
    }.getOrElse { throwable ->
        AppResult.Failure(
            RuntimeError(
                type = RuntimeErrorType.MISSING_BINARY,
                message = throwable.message
            )
        )
    }

    suspend fun getActiveVersion(): InstalledFridaVersion? {
        val entity = dao.getAll().firstOrNull { it.isActive } ?: return null
        return entity.toModel(File(entity.binaryPath).exists())
    }

    suspend fun deleteVersion(version: String): AppResult<Unit> = runCatching {
        val entity = dao.getByVersion(version) ?: return@runCatching AppResult.Success(Unit)
        File(entity.binaryPath).parentFile?.deleteRecursively()
        versionBackupStore.delete(version)
        dao.deleteByVersion(version)
        if (entity.isActive) {
            stateStore.writeCurrentVersion(null)
        }
        logger.log("Deleted version=$version path=${entity.binaryPath}")
        AppResult.Success(Unit)
    }.getOrElse { throwable ->
        AppResult.Failure(
            RuntimeError(
                type = RuntimeErrorType.FILE_PERMISSION_FAILURE,
                message = throwable.message
            )
        )
    }

    suspend fun restoreFromBackups(): Int = withContext(Dispatchers.IO) {
        fileLayout.ensureInitialized()
        val backups = versionBackupStore.listBackups()
        if (backups.isEmpty()) return@withContext 0

        val installedByVersion = dao.getAll().associateBy { it.version }.toMutableMap()
        var restored = 0

        backups.forEach { backup ->
            val targetBinary = fileLayout.binaryFile(backup.version)
            val hasBinary = targetBinary.exists() && targetBinary.length() > 0L
            val hasRecord = installedByVersion.containsKey(backup.version)
            if (hasBinary && hasRecord) return@forEach

            targetBinary.parentFile?.mkdirs()
            backup.binaryFile.copyTo(targetBinary, overwrite = true)
            targetBinary.setReadable(true, false)
            targetBinary.setExecutable(true, false)
            val chmod = permissionController.ensureExecutable(targetBinary.absolutePath)
            if (!chmod.isSuccess) {
                logger.log("Restore chmod failed for ${backup.version}: ${chmod.stderr}")
            }

            val entity = InstalledVersionEntity(
                version = backup.version,
                binaryPath = targetBinary.absolutePath,
                installedAtMs = backup.installedAtMs,
                source = backup.source,
                abiTag = backup.abiTag,
                fileSizeBytes = targetBinary.length(),
                isActive = false
            )
            dao.upsert(entity)
            installedByVersion[backup.version] = entity
            restored += 1
        }

        val anyActive = dao.getAll().any { it.isActive }
        if (!anyActive) {
            val latest = dao.getAll().maxByOrNull { it.installedAtMs }
            if (latest != null) {
                dao.markActive(latest.version)
                stateStore.writeCurrentVersion(latest.version)
            }
        }

        if (restored > 0) {
            logger.log("Restored $restored version(s) from non-root backup directory")
        }
        restored
    }

    private suspend fun installBinary(
        version: String,
        sourceFile: File,
        source: InstallSource,
        abiTag: String?
    ): InstalledFridaVersion {
        val targetDir = fileLayout.versionDir(version).also { it.mkdirs() }
        val targetBinary = File(targetDir, "frida-server")
        sourceFile.copyTo(targetBinary, overwrite = true)

        val chmodResult = permissionController.ensureExecutable(targetBinary.absolutePath)
        if (!chmodResult.isSuccess) {
            throw IllegalStateException("Failed to chmod executable: ${chmodResult.stderr}")
        }

        val entity = InstalledVersionEntity(
            version = version,
            binaryPath = targetBinary.absolutePath,
            installedAtMs = System.currentTimeMillis(),
            source = source,
            abiTag = abiTag,
            fileSizeBytes = targetBinary.length(),
            isActive = false
        )
        dao.upsert(entity)
        runCatching {
            versionBackupStore.backup(
                version = version,
                sourceBinary = targetBinary,
                source = source,
                abiTag = abiTag,
                installedAtMs = entity.installedAtMs
            )
        }.onFailure { throwable ->
            logger.log("Backup write failed for version=$version: ${throwable.message}")
        }.getOrNull()?.let { backupFile ->
            logger.log("Backed up version=$version to ${backupFile.absolutePath}")
        }
        if (dao.getAll().none { it.isActive }) {
            dao.markActive(version)
            stateStore.writeCurrentVersion(version)
        }
        logger.log("Installed version=$version source=$source path=${targetBinary.absolutePath}")
        return (dao.getByVersion(version) ?: entity).toModel(isValid = true)
    }

    private fun mergeRemoteVersions(
        fresh: List<RemoteFridaVersion>,
        cached: List<RemoteFridaVersion>
    ): List<RemoteFridaVersion> {
        if (cached.isEmpty()) return fresh
        val merged = LinkedHashMap<String, RemoteFridaVersion>()
        fresh.forEach { merged[it.version] = it }
        cached.forEach { version -> merged.putIfAbsent(version.version, version) }
        return merged.values.toList()
    }

    private suspend fun downloadAsset(
        url: String,
        targetFile: File,
        onProgress: suspend (InstallProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        okHttpClient.awaitResponse(request).use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Missing response body")
            val total = body.contentLength().coerceAtLeast(0L)
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            InstallProgress(
                                phase = InstallPhase.DOWNLOADING,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    }
                }
            }
        }
        if (targetFile.length() <= 0L) throw IOException("Downloaded file is empty")
    }

    private fun decompressXz(source: File, destination: File) {
        XZInputStream(source.inputStream()).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractVersion(fileName: String): String? {
        return FridaReleaseMapper.extractVersionFromAssetName(fileName)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun copyUriToFile(uri: Uri, targetFile: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
