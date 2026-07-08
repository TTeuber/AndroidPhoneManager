package com.tyler.selfcontrol.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Schedule entity for time-based block activation.
 *
 * Times are stored as minutes from midnight in UTC.
 * Days are stored as a bitmask where:
 * - bit 0 = Sunday
 * - bit 1 = Monday
 * - bit 2 = Tuesday
 * - bit 3 = Wednesday
 * - bit 4 = Thursday
 * - bit 5 = Friday
 * - bit 6 = Saturday
 *
 * Overnight schedules (endTime < startTime) are supported.
 */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Block::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("blockId")]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockId: Long,
    val daysOfWeek: Int = 0, // Bitmask for days
    val startTimeMinutes: Int = 0, // Minutes from midnight (0-1439)
    val endTimeMinutes: Int = 0 // Minutes from midnight (0-1439)
) {
    companion object {
        const val SUNDAY = 1 shl 0    // 1
        const val MONDAY = 1 shl 1    // 2
        const val TUESDAY = 1 shl 2   // 4
        const val WEDNESDAY = 1 shl 3 // 8
        const val THURSDAY = 1 shl 4  // 16
        const val FRIDAY = 1 shl 5    // 32
        const val SATURDAY = 1 shl 6  // 64

        const val WEEKDAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY
        const val WEEKENDS = SATURDAY or SUNDAY
        const val ALL_DAYS = WEEKDAYS or WEEKENDS

        fun fromDayOfWeek(day: DayOfWeek): Int {
            return when (day) {
                DayOfWeek.SUNDAY -> SUNDAY
                DayOfWeek.MONDAY -> MONDAY
                DayOfWeek.TUESDAY -> TUESDAY
                DayOfWeek.WEDNESDAY -> WEDNESDAY
                DayOfWeek.THURSDAY -> THURSDAY
                DayOfWeek.FRIDAY -> FRIDAY
                DayOfWeek.SATURDAY -> SATURDAY
            }
        }

        private const val MINUTES_PER_HOUR = 60
        private const val DAY_ABBREVIATION_LENGTH = 3

        fun localTimeToMinutes(time: LocalTime): Int {
            return time.hour * MINUTES_PER_HOUR + time.minute
        }

        fun minutesToLocalTime(minutes: Int): LocalTime {
            return LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        }
    }

    fun isDayEnabled(dayBit: Int): Boolean {
        return (daysOfWeek and dayBit) != 0
    }

    fun getStartTime(): LocalTime = minutesToLocalTime(startTimeMinutes)

    fun getEndTime(): LocalTime = minutesToLocalTime(endTimeMinutes)

    fun isOvernightSchedule(): Boolean {
        return endTimeMinutes < startTimeMinutes
    }

    fun getEnabledDays(): List<DayOfWeek> {
        return buildList {
            if (isDayEnabled(SUNDAY)) add(DayOfWeek.SUNDAY)
            if (isDayEnabled(MONDAY)) add(DayOfWeek.MONDAY)
            if (isDayEnabled(TUESDAY)) add(DayOfWeek.TUESDAY)
            if (isDayEnabled(WEDNESDAY)) add(DayOfWeek.WEDNESDAY)
            if (isDayEnabled(THURSDAY)) add(DayOfWeek.THURSDAY)
            if (isDayEnabled(FRIDAY)) add(DayOfWeek.FRIDAY)
            if (isDayEnabled(SATURDAY)) add(DayOfWeek.SATURDAY)
        }
    }

    fun getScheduleDescription(): String {
        val days = getEnabledDays()
        if (days.isEmpty()) return "No days selected"

        val daysStr = when {
            daysOfWeek == ALL_DAYS -> "Every day"
            daysOfWeek == WEEKDAYS -> "Weekdays"
            daysOfWeek == WEEKENDS -> "Weekends"
            else -> days.joinToString(", ") {
                it.name.take(DAY_ABBREVIATION_LENGTH).lowercase().replaceFirstChar { c -> c.uppercase() }
            }
        }

        val startTime = getStartTime()
        val endTime = getEndTime()
        val timeStr = String.format("%02d:%02d - %02d:%02d",
            startTime.hour, startTime.minute,
            endTime.hour, endTime.minute
        )

        return "$daysStr $timeStr"
    }
}
