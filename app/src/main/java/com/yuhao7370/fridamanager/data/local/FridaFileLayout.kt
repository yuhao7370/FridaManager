package com.yuhao7370.fridamanager.data.local

import android.content.Context
import java.io.File

class FridaFileLayout(private val context: Context) {
    val baseDir: File = File(context.filesDir, "frida")
    val versionsDir: File = File(baseDir, "versions")
    val logsDir: File = File(baseDir, "logs")
    val stateDir: File = File(baseDir, "state")
    val cacheDir: File = File(baseDir, "cache")
    val cacheDownloadsDir: File = File(baseDir, "cache/downloads")
    val remoteReleasesCacheFile: File = File(cacheDir, "remote_releases.json")
    val backupRootDir: File? = resolveBackupRootDir()

    val controllerLogFile: File = File(logsDir, "controller.log")
    val fridaStdoutLogFile: File = File(logsDir, "frida.stdout.log")
    val fridaStderrLogFile: File = File(logsDir, "frida.stderr.log")
    val currentVersionFile: File = File(stateDir, "current_version")
    val lastErrorFile: File = File(stateDir, "last_error")
    val statusFile: File = File(stateDir, "status.json")
    val pidFile: File = File(stateDir, "frida.pid")
    val processHandleFile: File = File(stateDir, "frida_process.json")

    fun ensureInitialized() {
        versionsDir.mkdirs()
        logsDir.mkdirs()
        stateDir.mkdirs()
        cacheDir.mkdirs()
        cacheDownloadsDir.mkdirs()
        backupRootDir?.mkdirs()
        if (!controllerLogFile.exists()) controllerLogFile.createNewFile()
        if (!fridaStdoutLogFile.exists()) fridaStdoutLogFile.createNewFile()
        if (!fridaStderrLogFile.exists()) fridaStderrLogFile.createNewFile()
    }

    fun versionDir(version: String): File = File(versionsDir, sanitizeVersion(version))

    fun binaryFile(version: String): File = File(versionDir(version), "frida-server")

    fun backupVersionDir(version: String): File? {
        return backupRootDir?.let { File(it, sanitizeVersion(version)) }
    }

    fun backupBinaryFile(version: String): File? {
        return backupVersionDir(version)?.let { File(it, "frida-server") }
    }

    fun backupMetadataFile(version: String): File? {
        return backupVersionDir(version)?.let { File(it, "metadata.json") }
    }

    fun sanitizeVersion(raw: String): String = raw
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        .take(80)

    @Suppress("DEPRECATION")
    private fun resolveBackupRootDir(): File? {
        val mediaRoot = context.externalMediaDirs
            .firstOrNull()
            ?.let { File(it, "frida/versions_backup") }
            ?.takeIf { it.exists() || it.mkdirs() }
        if (mediaRoot != null) return mediaRoot

        return context.getExternalFilesDir("frida_versions_backup")
            ?.takeIf { it.exists() || it.mkdirs() }
    }
}
