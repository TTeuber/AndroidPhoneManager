package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.BlockWithRules
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {

    @Query("SELECT * FROM blocks ORDER BY name ASC")
    fun getAllBlocks(): Flow<List<Block>>

    @Transaction
    @Query("SELECT * FROM blocks ORDER BY name ASC")
    fun getAllBlocksWithRules(): Flow<List<BlockWithRules>>

    @Query("SELECT * FROM blocks WHERE id = :blockId")
    suspend fun getBlockById(blockId: Long): Block?

    @Transaction
    @Query("SELECT * FROM blocks WHERE id = :blockId")
    suspend fun getBlockWithRulesById(blockId: Long): BlockWithRules?

    @Query("SELECT * FROM blocks WHERE isEnabled = 1")
    fun getEnabledBlocks(): Flow<List<Block>>

    @Transaction
    @Query("SELECT * FROM blocks WHERE isEnabled = 1")
    fun getEnabledBlocksWithRules(): Flow<List<BlockWithRules>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: Block): Long

    @Update
    suspend fun update(block: Block)

    @Delete
    suspend fun delete(block: Block)

    @Query("UPDATE blocks SET isEnabled = :enabled WHERE id = :blockId")
    suspend fun setEnabled(blockId: Long, enabled: Boolean)

    @Query("UPDATE blocks SET isScheduleActive = :active WHERE id = :blockId")
    suspend fun setScheduleActive(blockId: Long, active: Boolean)

    @Query("DELETE FROM blocks WHERE id = :blockId")
    suspend fun deleteById(blockId: Long)
}
