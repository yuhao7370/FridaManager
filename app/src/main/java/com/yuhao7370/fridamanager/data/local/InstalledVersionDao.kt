package com.yuhao7370.fridamanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledVersionDao {
    @Query("SELECT * FROM installed_versions ORDER BY installedAtMs DESC")
    fun observeAll(): Flow<List<InstalledVersionEntity>>

    @Query("SELECT * FROM installed_versions ORDER BY installedAtMs DESC")
    suspend fun getAll(): List<InstalledVersionEntity>

    @Query("SELECT * FROM installed_versions WHERE version = :version LIMIT 1")
    suspend fun getByVersion(version: String): InstalledVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstalledVersionEntity)

    @Query("DELETE FROM installed_versions WHERE version = :version")
    suspend fun deleteByVersion(version: String)

    @Query("UPDATE installed_versions SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE installed_versions SET isActive = 1 WHERE version = :version")
    suspend fun markActive(version: String)
}
