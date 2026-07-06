package com.tyler.selfcontrol.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleTest {

    private fun schedule(
        days: Int = Schedule.ALL_DAYS,
        start: Int = 0,
        end: Int = 0
    ) = Schedule(blockId = 1, daysOfWeek = days, startTimeMinutes = start, endTimeMinutes = end)

    // --- Day bitmask ---

    @Test
    fun `fromDayOfWeek maps every day to its own bit`() {
        assertEquals(Schedule.SUNDAY, Schedule.fromDayOfWeek(DayOfWeek.SUNDAY))
        assertEquals(Schedule.MONDAY, Schedule.fromDayOfWeek(DayOfWeek.MONDAY))
        assertEquals(Schedule.TUESDAY, Schedule.fromDayOfWeek(DayOfWeek.TUESDAY))
        assertEquals(Schedule.WEDNESDAY, Schedule.fromDayOfWeek(DayOfWeek.WEDNESDAY))
        assertEquals(Schedule.THURSDAY, Schedule.fromDayOfWeek(DayOfWeek.THURSDAY))
        assertEquals(Schedule.FRIDAY, Schedule.fromDayOfWeek(DayOfWeek.FRIDAY))
        assertEquals(Schedule.SATURDAY, Schedule.fromDayOfWeek(DayOfWeek.SATURDAY))
    }

    @Test
    fun `day bits are distinct powers of two`() {
        val bits = listOf(
            Schedule.SUNDAY, Schedule.MONDAY, Schedule.TUESDAY, Schedule.WEDNESDAY,
            Schedule.THURSDAY, Schedule.FRIDAY, Schedule.SATURDAY
        )
        assertEquals(7, bits.distinct().size)
        bits.forEach { bit -> assertEquals(0, bit and (bit - 1)) }
    }

    @Test
    fun `isDayEnabled checks individual bits`() {
        val s = schedule(days = Schedule.MONDAY or Schedule.FRIDAY)
        assertTrue(s.isDayEnabled(Schedule.MONDAY))
        assertTrue(s.isDayEnabled(Schedule.FRIDAY))
        assertFalse(s.isDayEnabled(Schedule.SUNDAY))
        assertFalse(s.isDayEnabled(Schedule.SATURDAY))
    }

    @Test
    fun `weekdays and weekends combine to all days`() {
        assertEquals(Schedule.ALL_DAYS, Schedule.WEEKDAYS or Schedule.WEEKENDS)
        assertEquals(0, Schedule.WEEKDAYS and Schedule.WEEKENDS)
        assertEquals(0b1111111, Schedule.ALL_DAYS)
    }

    @Test
    fun `getEnabledDays returns matching days`() {
        val s = schedule(days = Schedule.WEEKENDS)
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY), s.getEnabledDays())
        assertTrue(schedule(days = 0).getEnabledDays().isEmpty())
    }

    // --- Time conversion ---

    @Test
    fun `localTimeToMinutes and minutesToLocalTime round trip`() {
        assertEquals(0, Schedule.localTimeToMinutes(LocalTime.MIDNIGHT))
        assertEquals(570, Schedule.localTimeToMinutes(LocalTime.of(9, 30)))
        assertEquals(1439, Schedule.localTimeToMinutes(LocalTime.of(23, 59)))

        assertEquals(LocalTime.of(9, 30), Schedule.minutesToLocalTime(570))
        assertEquals(LocalTime.of(23, 59), Schedule.minutesToLocalTime(1439))
        assertEquals(LocalTime.MIDNIGHT, Schedule.minutesToLocalTime(0))
    }

    // --- Overnight detection ---

    @Test
    fun `overnight schedule when end is before start`() {
        assertTrue(schedule(start = 22 * 60, end = 6 * 60).isOvernightSchedule())
        assertFalse(schedule(start = 9 * 60, end = 17 * 60).isOvernightSchedule())
        assertFalse(schedule(start = 9 * 60, end = 9 * 60).isOvernightSchedule())
    }

    // --- Description ---

    @Test
    fun `schedule description recognizes common day groups`() {
        assertEquals(
            "Weekdays 09:00 - 17:00",
            schedule(days = Schedule.WEEKDAYS, start = 540, end = 1020).getScheduleDescription()
        )
        assertEquals(
            "Every day 22:00 - 06:00",
            schedule(days = Schedule.ALL_DAYS, start = 1320, end = 360).getScheduleDescription()
        )
        assertEquals("No days selected", schedule(days = 0).getScheduleDescription())
    }
}
