package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tyler.selfcontrol.data.model.BlacklistedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistedAppDao {

    @Query("SELECT * FROM blacklisted_apps ORDER BY appName ASC")
    fun getAllBlacklistedApps(): Flow<List<BlacklistedApp>>

    @Query("SELECT * FROM blacklisted_apps ORDER BY appName ASC")
    suspend fun getAllBlacklistedAppsOnce(): List<BlacklistedApp>

    @Query("SELECT * FROM blacklisted_apps WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): BlacklistedApp?

    @Query("SELECT EXISTS(SELECT 1 FROM blacklisted_apps WHERE packageName = :packageName)")
    suspend fun isBlacklisted(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: BlacklistedApp): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<BlacklistedApp>)

    @Delete
    suspend fun delete(app: BlacklistedApp)

    @Query("DELETE FROM blacklisted_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
