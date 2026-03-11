package com.yuhao7370.fridamanager.data.local

import com.yuhao7370.fridamanager.model.InstallSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class VersionBackupStore(
    private val fileLayout: FridaFileLayout,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    suspend fun backup(
        version: String,
        sourceBinary: File,
        source: InstallSource,
        abiTag: String?,
        installedAtMs: Long
    ): File? = withContext(Dispatchers.IO) {
        val targetBinary = fileLayout.backupBinaryFile(version) ?: return@withContext null
        targetBinary.parentFile?.mkdirs()
        sourceBinary.copyTo(targetBinary, overwrite = true)
        targetBinary.setReadable(true, false)
        targetBinary.setExecutable(true, false)

        val metadata = BackupMetadata(
            version = version,
            source = source.name,
            abiTag = abiTag,
            installedAtMs = installedAtMs,
            savedAtMs = System.currentTimeMillis()
        )
        fileLayout.backupMetadataFile(version)?.writeText(
            json.encodeToString(BackupMetadata.serializer(), metadata)
        )
        targetBinary
    }

    suspend fun delete(version: String): Boolean = withContext(Dispatchers.IO) {
        val dir = fileLayout.backupVersionDir(version) ?: return@withContext false
        dir.deleteRecursively()
    }

    suspend fun listBackups(): List<BackupEntry> = withContext(Dispatchers.IO) {
        val root = fileLayout.backupRootDir ?: return@withContext emptyList()
        val children = root.listFiles().orEmpty()
        children
            .asSequence()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val version = dir.name
                val binary = File(dir, "frida-server")
                if (!binary.isFile || binary.length() <= 0L) return@mapNotNull null

                val metadata = readMetadata(version)
                BackupEntry(
                    version = version,
                    binaryFile = binary,
                    source = metadata?.source?.toInstallSource() ?: InstallSource.IMPORTED,
                    abiTag = metadata?.abiTag,
                    installedAtMs = metadata?.installedAtMs ?: binary.lastModified()
                )
            }
            .sortedByDescending { it.installedAtMs }
            .toList()
    }

    private fun readMetadata(version: String): BackupMetadata? {
        val metadataFile = fileLayout.backupMetadataFile(version) ?: return null
        if (!metadataFile.exists() || metadataFile.length() <= 0L) return null
        return runCatching {
            json.decodeFromString(BackupMetadata.serializer(), metadataFile.readText())
        }.getOrNull()
    }

    @Serializable
    private data class BackupMetadata(
        val version: String,
        val source: String,
        val abiTag: String? = null,
        val installedAtMs: Long,
        val savedAtMs: Long
    )
}

data class BackupEntry(
    val version: String,
    val binaryFile: File,
    val source: InstallSource,
    val abiTag: String?,
    val installedAtMs: Long
)

private fun String.toInstallSource(): InstallSource {
    return runCatching { InstallSource.valueOf(this) }.getOrDefault(InstallSource.IMPORTED)
}
