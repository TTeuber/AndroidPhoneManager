package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface LockDao {

    @Query("SELECT * FROM locks WHERE blockId = :blockId")
    suspend fun getLockForBlock(blockId: Long): Lock?

    @Query("SELECT * FROM locks WHERE blockId = :blockId")
    fun getLockForBlockFlow(blockId: Long): Flow<Lock?>

    @Query("SELECT * FROM locks WHERE mode != 'UNLOCKED'")
    fun getActiveLocks(): Flow<List<Lock>>

    @Query("SELECT * FROM locks WHERE mode IN ('UNTIL_DATETIME', 'TIMER') AND unlockTime <= :currentTime")
    suspend fun getExpiredLocks(currentTime: Instant): List<Lock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lock: Lock): Long

    @Update
    suspend fun update(lock: Lock)

    @Query("UPDATE locks SET mode = :mode, unlockTime = :unlockTime WHERE blockId = :blockId")
    suspend fun updateLockState(blockId: Long, mode: LockMode, unlockTime: Instant?)

    @Query("DELETE FROM locks WHERE blockId = :blockId")
    suspend fun deleteForBlock(blockId: Long)

    @Query("UPDATE locks SET mode = 'UNLOCKED', unlockTime = NULL WHERE blockId = :blockId")
    suspend fun unlock(blockId: Long)
}
