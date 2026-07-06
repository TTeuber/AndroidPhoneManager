package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.dao.ScheduleDao
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.repository.BlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleManagerTest {

    private val scheduleDao = mockk<ScheduleDao>()
    private val blockRepository = mockk<BlockRepository>()
    private val manager = ScheduleManager(scheduleDao, blockRepository)

    // 2026-01-05 is a Monday
    private val mondayNoon = LocalDateTime.of(2026, 1, 5, 12, 0)

    private fun schedule(days: Int, start: Int, end: Int) =
        Schedule(blockId = 1, daysOfWeek = days, startTimeMinutes = start, endTimeMinutes = end)

    // --- Normal (same-day) schedules ---

    @Test
    fun `normal schedule active within window on enabled day`() {
        val s = schedule(Schedule.MONDAY, start = 9 * 60, end = 17 * 60)
        assertTrue(manager.isScheduleActive(s, mondayNoon))
    }

    @Test
    fun `normal schedule inactive before start and after end`() {
        val s = schedule(Schedule.MONDAY, start = 9 * 60, end = 17 * 60)
        assertFalse(manager.isScheduleActive(s, mondayNoon.withHour(8).withMinute(59)))
        assertFalse(manager.isScheduleActive(s, mondayNoon.withHour(18)))
    }

    @Test
    fun `normal schedule start is inclusive and end is exclusive`() {
        val s = schedule(Schedule.MONDAY, start = 9 * 60, end = 17 * 60)
        assertTrue(manager.isScheduleActive(s, mondayNoon.withHour(9).withMinute(0)))
        assertFalse(manager.isScheduleActive(s, mondayNoon.withHour(17).withMinute(0)))
        assertTrue(manager.isScheduleActive(s, mondayNoon.withHour(16).withMinute(59)))
    }

    @Test
    fun `normal schedule inactive on disabled day`() {
        val s = schedule(Schedule.MONDAY, start = 9 * 60, end = 17 * 60)
        val tuesdayNoon = mondayNoon.plusDays(1)
        assertFalse(manager.isScheduleActive(s, tuesdayNoon))
    }

    // --- Overnight schedules ---

    @Test
    fun `overnight schedule active after start on enabled day`() {
        val s = schedule(Schedule.MONDAY, start = 22 * 60, end = 6 * 60)
        assertTrue(manager.isScheduleActive(s, mondayNoon.withHour(23)))
    }

    @Test
    fun `overnight schedule carries into next morning`() {
        val s = schedule(Schedule.MONDAY, start = 22 * 60, end = 6 * 60)
        val tuesdayEarly = mondayNoon.plusDays(1).withHour(5)
        assertTrue(manager.isScheduleActive(s, tuesdayEarly))
    }

    @Test
    fun `overnight schedule ends at end time next morning`() {
        val s = schedule(Schedule.MONDAY, start = 22 * 60, end = 6 * 60)
        val tuesdaySixAm = mondayNoon.plusDays(1).withHour(6).withMinute(0)
        assertFalse(manager.isScheduleActive(s, tuesdaySixAm))
    }

    @Test
    fun `overnight schedule not active in morning when previous day disabled`() {
        val s = schedule(Schedule.MONDAY, start = 22 * 60, end = 6 * 60)
        // Monday 5 AM: previous day is Sunday, which is not enabled
        assertFalse(manager.isScheduleActive(s, mondayNoon.withHour(5)))
    }

    @Test
    fun `overnight schedule wraps from Sunday into Monday`() {
        val s = schedule(Schedule.SUNDAY, start = 22 * 60, end = 6 * 60)
        // Monday 3 AM: previous day is Sunday, which is enabled
        assertTrue(manager.isScheduleActive(s, mondayNoon.withHour(3)))
    }

    @Test
    fun `overnight schedule inactive in daytime gap`() {
        val s = schedule(Schedule.ALL_DAYS, start = 22 * 60, end = 6 * 60)
        assertFalse(manager.isScheduleActive(s, mondayNoon))
    }

    // --- Next state change ---

    @Test
    fun `no next change when no days enabled`() {
        val s = schedule(days = 0, start = 9 * 60, end = 17 * 60)
        assertNull(manager.getNextStateChangeTime(s, mondayNoon))
    }

    @Test
    fun `next change is start time later today when inactive before window`() {
        val s = schedule(Schedule.MONDAY, start = 14 * 60, end = 17 * 60)
        val next = manager.getNextStateChangeTime(s, mondayNoon)
        assertEquals(mondayNoon.withHour(14).withMinute(0), next)
    }

    @Test
    fun `next change is end time today when currently active`() {
        val s = schedule(Schedule.MONDAY, start = 9 * 60, end = 17 * 60)
        val next = manager.getNextStateChangeTime(s, mondayNoon)
        assertEquals(mondayNoon.withHour(17).withMinute(0), next)
    }

    @Test
    fun `next change rolls to next enabled day after window has passed`() {
        val s = schedule(Schedule.MONDAY or Schedule.WEDNESDAY, start = 9 * 60, end = 11 * 60)
        // Monday noon: today's window is over, next is Wednesday 9 AM
        val next = manager.getNextStateChangeTime(s, mondayNoon)
        assertEquals(mondayNoon.plusDays(2).withHour(9).withMinute(0), next)
    }

    @Test
    fun `next change rolls a full week when only today is enabled and window passed`() {
        val s = schedule(Schedule.MONDAY, start = 9 * 60, end = 11 * 60)
        val next = manager.getNextStateChangeTime(s, mondayNoon)
        assertEquals(mondayNoon.plusDays(7).withHour(9).withMinute(0), next)
    }

    // --- processSchedules ---

    @Test
    fun `processSchedules updates blocks whose active state changed`() = runTest {
        val spy = spyk(ScheduleManager(scheduleDao, blockRepository))
        val activeSchedule = schedule(Schedule.ALL_DAYS, 0, 0).copy(blockId = 1)
        val inactiveSchedule = schedule(Schedule.ALL_DAYS, 0, 0).copy(blockId = 2)

        coEvery { scheduleDao.getActiveSchedules() } returns listOf(activeSchedule, inactiveSchedule)
        coEvery { blockRepository.getBlockById(1) } returns
                Block(id = 1, name = "A", state = BlockState.SCHEDULED, isScheduleActive = false)
        coEvery { blockRepository.getBlockById(2) } returns
                Block(id = 2, name = "B", state = BlockState.SCHEDULED, isScheduleActive = false)
        coEvery { blockRepository.setScheduleActive(any(), any()) } returns Unit

        every { spy.isScheduleActive(activeSchedule, any()) } returns true
        every { spy.isScheduleActive(inactiveSchedule, any()) } returns false

        val changed = spy.processSchedules()

        assertEquals(1, changed)
        coVerify(exactly = 1) { blockRepository.setScheduleActive(1, true) }
        coVerify(exactly = 0) { blockRepository.setScheduleActive(2, any()) }
    }

    @Test
    fun `processSchedules skips blocks not in scheduled state`() = runTest {
        val spy = spyk(ScheduleManager(scheduleDao, blockRepository))
        val s = schedule(Schedule.ALL_DAYS, 0, 0).copy(blockId = 1)

        coEvery { scheduleDao.getActiveSchedules() } returns listOf(s)
        coEvery { blockRepository.getBlockById(1) } returns
                Block(id = 1, name = "A", state = BlockState.ALWAYS_ON, isScheduleActive = false)
        every { spy.isScheduleActive(s, any()) } returns true

        assertEquals(0, spy.processSchedules())
        coVerify(exactly = 0) { blockRepository.setScheduleActive(any(), any()) }
    }
}
