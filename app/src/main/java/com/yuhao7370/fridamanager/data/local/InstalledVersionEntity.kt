package com.yuhao7370.fridamanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yuhao7370.fridamanager.model.InstallSource
import com.yuhao7370.fridamanager.model.InstalledFridaVersion

@Entity(tableName = "installed_versions")
data class InstalledVersionEntity(
    @PrimaryKey val version: String,
    val binaryPath: String,
    val installedAtMs: Long,
    val source: InstallSource,
    val abiTag: String?,
    val fileSizeBytes: Long,
    val isActive: Boolean
)

fun InstalledVersionEntity.toModel(isValid: Boolean): InstalledFridaVersion = InstalledFridaVersion(
    version = version,
    binaryPath = binaryPath,
    installedAtMs = installedAtMs,
    source = source,
    abiTag = abiTag,
    fileSizeBytes = fileSizeBytes,
    isActive = isActive,
    isValid = isValid
)
