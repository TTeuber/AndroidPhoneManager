package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tyler.selfcontrol.data.model.AppRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Query("SELECT * FROM app_rules WHERE blockId = :blockId")
    fun getAppRulesForBlock(blockId: Long): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE blockId = :blockId")
    suspend fun getAppRulesForBlockOnce(blockId: Long): List<AppRule>

    @Query("""
        SELECT DISTINCT packageName FROM app_rules
        WHERE blockId IN (
            SELECT id FROM blocks
            WHERE isEnabled = 1
            AND (state = 'ALWAYS_ON' OR (state = 'SCHEDULED' AND isScheduleActive = 1))
        )
    """)
    fun getBlockedPackageNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appRule: AppRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appRules: List<AppRule>)

    @Delete
    suspend fun delete(appRule: AppRule)

    @Query("DELETE FROM app_rules WHERE blockId = :blockId AND packageName = :packageName")
    suspend fun deleteByPackageName(blockId: Long, packageName: String)

    @Query("DELETE FROM app_rules WHERE blockId = :blockId")
    suspend fun deleteAllForBlock(blockId: Long)
}
