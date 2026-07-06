package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.data.repository.BlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LockManagerTest {

    private val repository = mockk<BlockRepository>()
    private val manager = LockManager(repository)

    private val blockId = 1L
    private val future = Instant.now().plus(Duration.ofHours(2))
    private val past = Instant.now().minus(Duration.ofHours(2))

    private fun givenLock(lock: Lock?) {
        coEvery { repository.getLockForBlock(blockId) } returns lock
    }

    private fun timerLock(unlockTime: Instant?) =
        Lock(blockId = blockId, mode = LockMode.TIMER, unlockTime = unlockTime)

    // --- isBlockLocked ---

    @Test
    fun `block without lock entry is not locked`() = runTest {
        givenLock(null)
        assertFalse(manager.isBlockLocked(blockId))
    }

    @Test
    fun `unlocked mode is not locked`() = runTest {
        givenLock(Lock(blockId = blockId, mode = LockMode.UNLOCKED))
        assertFalse(manager.isBlockLocked(blockId))
    }

    @Test
    fun `forever lock is always locked`() = runTest {
        givenLock(Lock(blockId = blockId, mode = LockMode.FOREVER))
        assertTrue(manager.isBlockLocked(blockId))
    }

    @Test
    fun `timer lock is locked until expiry`() = runTest {
        givenLock(timerLock(future))
        assertTrue(manager.isBlockLocked(blockId))

        givenLock(timerLock(past))
        assertFalse(manager.isBlockLocked(blockId))
    }

    @Test
    fun `timer lock with missing unlock time is not locked`() = runTest {
        givenLock(timerLock(null))
        assertFalse(manager.isBlockLocked(blockId))
    }

    // --- canPerformOperation: add-only semantics ---

    @Test
    fun `locked block allows adding restrictions but not removing them`() = runTest {
        givenLock(Lock(blockId = blockId, mode = LockMode.FOREVER))

        assertTrue(manager.canPerformOperation(blockId, LockOperation.ADD_APP))
        assertTrue(manager.canPerformOperation(blockId, LockOperation.ADD_WEBSITE))
        assertTrue(manager.canPerformOperation(blockId, LockOperation.ENABLE))
        assertTrue(manager.canPerformOperation(blockId, LockOperation.RENAME))

        assertFalse(manager.canPerformOperation(blockId, LockOperation.REMOVE_APP))
        assertFalse(manager.canPerformOperation(blockId, LockOperation.REMOVE_WEBSITE))
        assertFalse(manager.canPerformOperation(blockId, LockOperation.DISABLE))
        assertFalse(manager.canPerformOperation(blockId, LockOperation.DELETE))
        assertFalse(manager.canPerformOperation(blockId, LockOperation.MODIFY_LOCK))
    }

    @Test
    fun `unlocked block allows all operations`() = runTest {
        givenLock(null)
        LockOperation.entries.forEach { op ->
            assertTrue("$op should be allowed", manager.canPerformOperation(blockId, op))
        }
    }

    // --- Creating locks ---

    @Test
    fun `lockUntilDateTime rejects past times`() = runTest {
        givenLock(null)
        val result = manager.lockUntilDateTime(blockId, past)
        assertTrue(result.isFailure)
    }

    @Test
    fun `lockUntilDateTime rejects already locked block`() = runTest {
        givenLock(Lock(blockId = blockId, mode = LockMode.FOREVER))
        val result = manager.lockUntilDateTime(blockId, future)
        assertTrue(result.isFailure)
    }

    @Test
    fun `lockUntilDateTime sets lock for future time`() = runTest {
        givenLock(null)
        coEvery { repository.setLock(blockId, LockMode.UNTIL_DATETIME, future) } returns Unit

        val result = manager.lockUntilDateTime(blockId, future)

        assertTrue(result.isSuccess)
        coVerify { repository.setLock(blockId, LockMode.UNTIL_DATETIME, future) }
    }

    @Test
    fun `lockForDuration rejects zero and negative durations`() = runTest {
        givenLock(null)
        assertTrue(manager.lockForDuration(blockId, Duration.ZERO).isFailure)
        assertTrue(manager.lockForDuration(blockId, Duration.ofMinutes(-5)).isFailure)
    }

    @Test
    fun `lockForDuration sets unlock time relative to now`() = runTest {
        givenLock(null)
        val captured = slot<Instant>()
        coEvery { repository.setLock(blockId, LockMode.TIMER, capture(captured)) } returns Unit

        val before = Instant.now()
        val result = manager.lockForDuration(blockId, Duration.ofHours(1))
        val after = Instant.now()

        assertTrue(result.isSuccess)
        assertFalse(captured.captured.isBefore(before.plus(Duration.ofHours(1))))
        assertFalse(captured.captured.isAfter(after.plus(Duration.ofHours(1))))
    }

    @Test
    fun `lockForever sets forever lock with no unlock time`() = runTest {
        givenLock(null)
        coEvery { repository.setLock(blockId, LockMode.FOREVER, null) } returns Unit

        assertTrue(manager.lockForever(blockId).isSuccess)
        coVerify { repository.setLock(blockId, LockMode.FOREVER, null) }
    }

    // --- Unlocking ---

    @Test
    fun `forever lock cannot be manually unlocked`() = runTest {
        givenLock(Lock(blockId = blockId, mode = LockMode.FOREVER))
        assertTrue(manager.unlock(blockId).isFailure)
    }

    @Test
    fun `unexpired timer lock cannot be unlocked`() = runTest {
        givenLock(timerLock(future))
        assertTrue(manager.unlock(blockId).isFailure)
    }

    @Test
    fun `expired timer lock unlocks successfully`() = runTest {
        givenLock(timerLock(past))
        coEvery { repository.unlock(blockId) } returns Unit

        assertTrue(manager.unlock(blockId).isSuccess)
        coVerify { repository.unlock(blockId) }
    }

    @Test
    fun `processExpiredLocks unlocks each expired lock`() = runTest {
        coEvery { repository.getExpiredLocks() } returns listOf(
            timerLock(past),
            Lock(blockId = 2, mode = LockMode.UNTIL_DATETIME, unlockTime = past)
        )
        coEvery { repository.unlock(any()) } returns Unit

        assertEquals(2, manager.processExpiredLocks())
        coVerify { repository.unlock(1) }
        coVerify { repository.unlock(2) }
    }

    // --- Extending locks ---

    @Test
    fun `forever lock cannot be extended`() = runTest {
        givenLock(Lock(blockId = blockId, mode = LockMode.FOREVER))
        assertFalse(manager.canExtendLock(blockId))
        assertTrue(manager.extendLockByDuration(blockId, Duration.ofHours(1)).isFailure)
    }

    @Test
    fun `active timer lock can be extended by duration`() = runTest {
        givenLock(timerLock(future))
        val captured = slot<Instant>()
        coEvery { repository.setLock(blockId, LockMode.TIMER, capture(captured)) } returns Unit

        assertTrue(manager.canExtendLock(blockId))
        val result = manager.extendLockByDuration(blockId, Duration.ofHours(1))

        assertTrue(result.isSuccess)
        assertEquals(future.plus(Duration.ofHours(1)), captured.captured)
    }

    @Test
    fun `extendLockUntil rejects times earlier than current unlock time`() = runTest {
        givenLock(timerLock(future))
        val earlier = future.minus(Duration.ofHours(1))
        assertTrue(manager.extendLockUntil(blockId, earlier).isFailure)
    }

    @Test
    fun `extendLockUntil accepts later unlock time`() = runTest {
        givenLock(timerLock(future))
        val later = future.plus(Duration.ofHours(3))
        coEvery { repository.setLock(blockId, LockMode.TIMER, later) } returns Unit

        assertTrue(manager.extendLockUntil(blockId, later).isSuccess)
        coVerify { repository.setLock(blockId, LockMode.TIMER, later) }
    }

    // --- Remaining time & formatting ---

    @Test
    fun `remaining time is null when not locked and zero when expired`() = runTest {
        givenLock(null)
        assertNull(manager.getRemainingTime(blockId))

        givenLock(Lock(blockId = blockId, mode = LockMode.FOREVER))
        assertNull(manager.getRemainingTime(blockId))

        givenLock(timerLock(past))
        assertEquals(Duration.ZERO, manager.getRemainingTime(blockId))
    }

    @Test
    fun `formatDuration renders days hours and minutes`() {
        assertEquals("0m", LockManager.formatDuration(Duration.ZERO))
        assertEquals("45m", LockManager.formatDuration(Duration.ofMinutes(45)))
        assertEquals("1h 30m", LockManager.formatDuration(Duration.ofMinutes(90)))
        assertEquals("1d", LockManager.formatDuration(Duration.ofHours(24)))
        assertEquals("1d 1h", LockManager.formatDuration(Duration.ofHours(25)))
        assertEquals("2d 3h 15m", LockManager.formatDuration(Duration.ofMinutes(2 * 1440 + 3 * 60 + 15)))
    }
}
