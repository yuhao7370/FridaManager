package com.yuhao7370.fridamanager.data.local

import androidx.room.TypeConverter
import com.yuhao7370.fridamanager.model.InstallSource

class RoomConverters {
    @TypeConverter
    fun toInstallSource(raw: String?): InstallSource = runCatching {
        InstallSource.valueOf(raw.orEmpty())
    }.getOrDefault(InstallSource.IMPORTED)

    @TypeConverter
    fun fromInstallSource(source: InstallSource): String = source.name
}
