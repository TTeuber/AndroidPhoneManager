package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CooldownRequestDao {

    @Query("SELECT * FROM cooldown_requests WHERE status IN ('PENDING', 'WINDOW_OPEN') ORDER BY windowStart ASC")
    fun getActiveRequests(): Flow<List<CooldownRequest>>

    @Query("SELECT * FROM cooldown_requests WHERE status IN ('PENDING', 'WINDOW_OPEN') ORDER BY windowStart ASC")
    suspend fun getActiveRequestsOnce(): List<CooldownRequest>

    @Query("SELECT * FROM cooldown_requests WHERE id = :id")
    suspend fun getById(id: Long): CooldownRequest?

    @Query("SELECT * FROM cooldown_requests WHERE packageName = :packageName AND status IN ('PENDING', 'WINDOW_OPEN')")
    suspend fun getActiveRequestForPackage(packageName: String): CooldownRequest?

    @Query("SELECT * FROM cooldown_requests WHERE status = 'PENDING' AND windowStart <= :now")
    suspend fun getRequestsReadyForWindow(now: Instant): List<CooldownRequest>

    @Query("SELECT * FROM cooldown_requests WHERE status = 'WINDOW_OPEN' AND windowEnd <= :now")
    suspend fun getExpiredRequests(now: Instant): List<CooldownRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: CooldownRequest): Long

    @Update
    suspend fun update(request: CooldownRequest)

    @Query("UPDATE cooldown_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CooldownStatus)

    @Delete
    suspend fun delete(request: CooldownRequest)

    @Query("DELETE FROM cooldown_requests WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cooldown_requests WHERE status IN ('EXPIRED', 'CANCELLED', 'APPROVED')")
    suspend fun cleanupOldRequests()
}
