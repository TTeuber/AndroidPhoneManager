package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.data.repository.BlockRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages lock enforcement and operations.
 *
 * Lock rules:
 * - Allowed while locked: add to blocklist, rename block
 * - Disallowed while locked: remove from blocklist, disable block, delete block
 */
@Singleton
class LockManager @Inject constructor(
    private val blockRepository: BlockRepository
) {
    /**
     * Check if a block is currently locked (active lock that hasn't expired).
     */
    suspend fun isBlockLocked(blockId: Long): Boolean {
        val lock = blockRepository.getLockForBlock(blockId) ?: return false
        return when (lock.mode) {
            LockMode.UNLOCKED -> false
            LockMode.FOREVER -> true
            LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
                val unlockTime = lock.unlockTime ?: return false
                Instant.now().isBefore(unlockTime)
            }
        }
    }

    /**
     * Check if an operation is allowed on a block.
     */
    suspend fun canPerformOperation(blockId: Long, operation: LockOperation): Boolean {
        val isLocked = isBlockLocked(blockId)
        return when (operation) {
            // Always allowed
            LockOperation.RENAME -> true
            LockOperation.ADD_APP -> true
            LockOperation.ADD_WEBSITE -> true
            LockOperation.ENABLE -> true

            // Disallowed when locked
            LockOperation.REMOVE_APP -> !isLocked
            LockOperation.REMOVE_WEBSITE -> !isLocked
            LockOperation.DISABLE -> !isLocked
            LockOperation.DELETE -> !isLocked
            LockOperation.MODIFY_LOCK -> !isLocked
        }
    }

    /**
     * Lock a block until a specific date/time.
     */
    suspend fun lockUntilDateTime(blockId: Long, unlockTime: Instant): Result<Unit> {
        if (isBlockLocked(blockId)) {
            return Result.failure(LockException("Block is already locked"))
        }

        if (unlockTime.isBefore(Instant.now())) {
            return Result.failure(LockException("Unlock time must be in the future"))
        }

        blockRepository.setLock(blockId, LockMode.UNTIL_DATETIME, unlockTime)
        return Result.success(Unit)
    }

    /**
     * Lock a block for a duration (timer mode).
     */
    suspend fun lockForDuration(blockId: Long, duration: Duration): Result<Unit> {
        if (isBlockLocked(blockId)) {
            return Result.failure(LockException("Block is already locked"))
        }

        if (duration.isNegative || duration.isZero) {
            return Result.failure(LockException("Duration must be positive"))
        }

        val unlockTime = Instant.now().plus(duration)
        blockRepository.setLock(blockId, LockMode.TIMER, unlockTime)
        return Result.success(Unit)
    }

    /**
     * Lock a block forever.
     */
    suspend fun lockForever(blockId: Long): Result<Unit> {
        if (isBlockLocked(blockId)) {
            return Result.failure(LockException("Block is already locked"))
        }

        blockRepository.setLock(blockId, LockMode.FOREVER, null)
        return Result.success(Unit)
    }

    /**
     * Unlock a block (only if it's not a forever lock or has expired).
     */
    suspend fun unlock(blockId: Long): Result<Unit> {
        val lock = blockRepository.getLockForBlock(blockId)
            ?: return Result.success(Unit)

        when (lock.mode) {
            LockMode.UNLOCKED -> return Result.success(Unit)
            LockMode.FOREVER -> return Result.failure(LockException("Forever locks cannot be manually unlocked"))
            LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
                val unlockTime = lock.unlockTime
                if (unlockTime != null && Instant.now().isBefore(unlockTime)) {
                    return Result.failure(LockException("Lock has not expired yet"))
                }
            }
        }

        blockRepository.unlock(blockId)
        return Result.success(Unit)
    }

    /**
     * Process expired locks and unlock them.
     * Called by WorkManager periodic task.
     */
    suspend fun processExpiredLocks(): Int {
        val expiredLocks = blockRepository.getExpiredLocks()
        var unlockCount = 0

        for (lock in expiredLocks) {
            blockRepository.unlock(lock.blockId)
            unlockCount++
        }

        return unlockCount
    }

    /**
     * Get remaining time until unlock, or null if not locked or locked forever.
     */
    suspend fun getRemainingTime(blockId: Long): Duration? {
        val lock = blockRepository.getLockForBlock(blockId) ?: return null

        return when (lock.mode) {
            LockMode.UNLOCKED -> null
            LockMode.FOREVER -> null
            LockMode.UNTIL_DATETIME, LockMode.TIMER -> {
                val unlockTime = lock.unlockTime ?: return null
                val now = Instant.now()
                if (now.isBefore(unlockTime)) {
                    Duration.between(now, unlockTime)
                } else {
                    Duration.ZERO
                }
            }
        }
    }

    /**
     * Get a human-readable description of the lock status.
     */
    fun getLockStatusDescription(lock: Lock?): String {
        if (lock == null) return "Not locked"

        return when (lock.mode) {
            LockMode.UNLOCKED -> "Not locked"
            LockMode.FOREVER -> "Locked forever"
            LockMode.UNTIL_DATETIME -> {
                val unlockTime = lock.unlockTime
                if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
                    "Lock expired"
                } else {
                    val remaining = Duration.between(Instant.now(), unlockTime)
                    "Unlocks in ${formatDuration(remaining)}"
                }
            }
            LockMode.TIMER -> {
                val unlockTime = lock.unlockTime
                if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
                    "Lock expired"
                } else {
                    val remaining = Duration.between(Instant.now(), unlockTime)
                    "Unlocks in ${formatDuration(remaining)}"
                }
            }
        }
    }

    companion object {
        fun formatDuration(duration: Duration): String {
            val totalSeconds = duration.seconds
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60

            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || (days == 0L && hours == 0L)) append("${minutes}m")
            }.trim()
        }
    }
}

/**
 * Operations that can be performed on a block.
 */
enum class LockOperation {
    RENAME,
    ADD_APP,
    ADD_WEBSITE,
    REMOVE_APP,
    REMOVE_WEBSITE,
    ENABLE,
    DISABLE,
    DELETE,
    MODIFY_LOCK
}

/**
 * Exception thrown when a lock operation fails.
 */
class LockException(message: String) : Exception(message)
