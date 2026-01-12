package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.dao.ScheduleDao
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.repository.BlockRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages schedule evaluation and block state based on schedules.
 *
 * Handles:
 * - Evaluating if a schedule is currently active
 * - Overnight schedules (where end time < start time)
 * - Updating block enabled state based on schedule
 */
@Singleton
class ScheduleManager @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val blockRepository: BlockRepository
) {
    /**
     * Check if a schedule is currently active.
     *
     * @param schedule The schedule to check
     * @param now The current local date/time (defaults to system time)
     * @return true if the schedule is active right now
     */
    fun isScheduleActive(schedule: Schedule, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val currentDay = now.dayOfWeek
        val currentTimeMinutes = now.hour * 60 + now.minute

        // Check if today is enabled in the schedule
        val dayBit = Schedule.fromDayOfWeek(currentDay)

        return if (schedule.isOvernightSchedule()) {
            // Overnight schedule: e.g., 22:00 - 06:00
            // Active if:
            // - Today is enabled AND current time >= start time, OR
            // - Yesterday was enabled AND current time <= end time
            val isAfterStartToday = schedule.isDayEnabled(dayBit) && currentTimeMinutes >= schedule.startTimeMinutes
            val yesterdayBit = Schedule.fromDayOfWeek(currentDay.minus(1))
            val isBeforeEndFromYesterday = schedule.isDayEnabled(yesterdayBit) && currentTimeMinutes < schedule.endTimeMinutes

            isAfterStartToday || isBeforeEndFromYesterday
        } else {
            // Normal schedule: e.g., 09:00 - 17:00
            // Active if today is enabled AND current time is between start and end
            schedule.isDayEnabled(dayBit) &&
                    currentTimeMinutes >= schedule.startTimeMinutes &&
                    currentTimeMinutes < schedule.endTimeMinutes
        }
    }

    /**
     * Check if a block should be enabled based on its schedule.
     * Returns null if the block is not in SCHEDULED state.
     */
    suspend fun shouldBlockBeEnabled(blockId: Long): Boolean? {
        val block = blockRepository.getBlockById(blockId) ?: return null
        if (block.state != BlockState.SCHEDULED) return null

        val schedule = scheduleDao.getScheduleForBlock(blockId) ?: return false
        return isScheduleActive(schedule)
    }

    /**
     * Process all scheduled blocks and update their enabled state.
     *
     * @return Number of blocks whose state changed
     */
    suspend fun processSchedules(): Int {
        val activeSchedules = scheduleDao.getActiveSchedules()
        var changedCount = 0

        for (schedule in activeSchedules) {
            val block = blockRepository.getBlockById(schedule.blockId) ?: continue
            if (block.state != BlockState.SCHEDULED) continue

            val shouldBeEnabled = isScheduleActive(schedule)
            if (block.isEnabled != shouldBeEnabled) {
                blockRepository.setBlockEnabled(schedule.blockId, shouldBeEnabled)
                changedCount++
            }
        }

        return changedCount
    }

    /**
     * Get the next state change time for a schedule.
     *
     * @param schedule The schedule to check
     * @param now The current local date/time
     * @return The next time the schedule will toggle (enable or disable), or null if never
     */
    fun getNextStateChangeTime(schedule: Schedule, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (schedule.daysOfWeek == 0) return null

        val currentTimeMinutes = now.hour * 60 + now.minute
        val isActive = isScheduleActive(schedule, now)

        // Look up to 7 days ahead
        for (dayOffset in 0..7) {
            val checkDate = now.toLocalDate().plusDays(dayOffset.toLong())
            val checkDay = checkDate.dayOfWeek
            val dayBit = Schedule.fromDayOfWeek(checkDay)

            if (!schedule.isDayEnabled(dayBit)) continue

            if (schedule.isOvernightSchedule()) {
                // Overnight schedule logic
                if (dayOffset == 0) {
                    // Today
                    if (!isActive && currentTimeMinutes < schedule.startTimeMinutes) {
                        // Will activate at start time today
                        return checkDate.atTime(Schedule.minutesToLocalTime(schedule.startTimeMinutes))
                    }
                    if (isActive && currentTimeMinutes < schedule.endTimeMinutes) {
                        // Will deactivate at end time today (from yesterday's start)
                        return checkDate.atTime(Schedule.minutesToLocalTime(schedule.endTimeMinutes))
                    }
                } else {
                    // Future day - schedule will start at startTime
                    return checkDate.atTime(Schedule.minutesToLocalTime(schedule.startTimeMinutes))
                }
            } else {
                // Normal schedule logic
                if (dayOffset == 0) {
                    // Today
                    if (!isActive && currentTimeMinutes < schedule.startTimeMinutes) {
                        return checkDate.atTime(Schedule.minutesToLocalTime(schedule.startTimeMinutes))
                    }
                    if (isActive && currentTimeMinutes < schedule.endTimeMinutes) {
                        return checkDate.atTime(Schedule.minutesToLocalTime(schedule.endTimeMinutes))
                    }
                } else {
                    // Future day - schedule will start at startTime
                    return checkDate.atTime(Schedule.minutesToLocalTime(schedule.startTimeMinutes))
                }
            }
        }

        return null
    }

    /**
     * Get a description of the schedule's current state.
     */
    suspend fun getScheduleStatusDescription(blockId: Long): String {
        val block = blockRepository.getBlockById(blockId) ?: return "Block not found"
        val schedule = scheduleDao.getScheduleForBlock(blockId)

        return when (block.state) {
            BlockState.ALWAYS_ON -> if (block.isEnabled) "Always on" else "Disabled"
            BlockState.SCHEDULED -> {
                if (schedule == null) {
                    "No schedule set"
                } else {
                    val isActive = isScheduleActive(schedule)
                    val nextChange = getNextStateChangeTime(schedule)

                    buildString {
                        append(if (isActive) "Active now" else "Inactive")
                        if (nextChange != null) {
                            val action = if (isActive) "Deactivates" else "Activates"
                            append(" ($action ${formatDateTime(nextChange)})")
                        }
                    }
                }
            }
        }
    }

    private fun formatDateTime(dateTime: LocalDateTime): String {
        val now = LocalDateTime.now()
        val isToday = dateTime.toLocalDate() == now.toLocalDate()
        val isTomorrow = dateTime.toLocalDate() == now.toLocalDate().plusDays(1)

        val dayPart = when {
            isToday -> "today"
            isTomorrow -> "tomorrow"
            else -> dateTime.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }

        return "$dayPart at ${String.format("%02d:%02d", dateTime.hour, dateTime.minute)}"
    }
}

private fun DayOfWeek.minus(days: Long): DayOfWeek {
    val adjusted = (this.value - days % 7 + 7) % 7
    return DayOfWeek.of(if (adjusted == 0L) 7 else adjusted.toInt())
}
