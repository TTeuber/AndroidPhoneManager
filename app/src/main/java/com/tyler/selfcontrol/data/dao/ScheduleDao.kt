package com.tyler.selfcontrol.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tyler.selfcontrol.data.model.Schedule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules WHERE blockId = :blockId")
    suspend fun getScheduleForBlock(blockId: Long): Schedule?

    @Query("SELECT * FROM schedules WHERE blockId = :blockId")
    fun getScheduleForBlockFlow(blockId: Long): Flow<Schedule?>

    @Query("SELECT * FROM schedules")
    fun getAllSchedules(): Flow<List<Schedule>>

    @Query("SELECT s.* FROM schedules s INNER JOIN blocks b ON s.blockId = b.id WHERE b.state = 'SCHEDULED'")
    suspend fun getActiveSchedules(): List<Schedule>

    @Query("SELECT s.* FROM schedules s INNER JOIN blocks b ON s.blockId = b.id WHERE b.state = 'SCHEDULED'")
    fun getActiveSchedulesFlow(): Flow<List<Schedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE blockId = :blockId")
    suspend fun deleteForBlock(blockId: Long)

    @Query("UPDATE schedules SET daysOfWeek = :daysOfWeek, startTimeMinutes = :startTime, endTimeMinutes = :endTime WHERE blockId = :blockId")
    suspend fun updateSchedule(blockId: Long, daysOfWeek: Int, startTime: Int, endTime: Int)
}
