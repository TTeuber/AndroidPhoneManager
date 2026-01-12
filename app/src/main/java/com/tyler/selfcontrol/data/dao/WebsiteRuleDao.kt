package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tyler.selfcontrol.data.model.WebsiteRule
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteRuleDao {

    @Query("SELECT * FROM website_rules WHERE blockId = :blockId")
    fun getWebsiteRulesForBlock(blockId: Long): Flow<List<WebsiteRule>>

    @Query("SELECT * FROM website_rules WHERE blockId = :blockId")
    suspend fun getWebsiteRulesForBlockOnce(blockId: Long): List<WebsiteRule>

    @Query("SELECT * FROM website_rules WHERE blockId IN (SELECT id FROM blocks WHERE isEnabled = 1)")
    fun getActiveWebsiteRules(): Flow<List<WebsiteRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(websiteRule: WebsiteRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(websiteRules: List<WebsiteRule>)

    @Delete
    suspend fun delete(websiteRule: WebsiteRule)

    @Query("DELETE FROM website_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: Long)

    @Query("DELETE FROM website_rules WHERE blockId = :blockId")
    suspend fun deleteAllForBlock(blockId: Long)
}
