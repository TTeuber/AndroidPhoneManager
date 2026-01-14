package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tyler.selfcontrol.data.model.AllowedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedAppDao {

    @Query("SELECT * FROM allowed_apps ORDER BY appName ASC")
    fun getAllAllowedApps(): Flow<List<AllowedApp>>

    @Query("SELECT * FROM allowed_apps ORDER BY appName ASC")
    suspend fun getAllAllowedAppsOnce(): List<AllowedApp>

    @Query("SELECT * FROM allowed_apps WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AllowedApp?

    @Query("SELECT packageName FROM allowed_apps")
    suspend fun getAllowedPackageNames(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_apps WHERE packageName = :packageName)")
    suspend fun isAllowed(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: AllowedApp): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<AllowedApp>)

    @Delete
    suspend fun delete(app: AllowedApp)

    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM allowed_apps")
    suspend fun deleteAll()
}
