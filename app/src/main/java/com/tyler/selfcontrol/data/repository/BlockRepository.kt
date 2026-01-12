package com.tyler.selfcontrol.data.repository

import com.tyler.selfcontrol.data.dao.AppRuleDao
import com.tyler.selfcontrol.data.dao.BlockDao
import com.tyler.selfcontrol.data.dao.LockDao
import com.tyler.selfcontrol.data.dao.ScheduleDao
import com.tyler.selfcontrol.data.dao.WebsiteRuleDao
import com.tyler.selfcontrol.data.model.AppRule
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.BlockWithRules
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.model.WebsiteRule
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockRepository @Inject constructor(
    private val blockDao: BlockDao,
    private val appRuleDao: AppRuleDao,
    private val websiteRuleDao: WebsiteRuleDao,
    private val lockDao: LockDao,
    private val scheduleDao: ScheduleDao
) {
    // Block operations
    fun getAllBlocks(): Flow<List<Block>> = blockDao.getAllBlocks()

    fun getAllBlocksWithRules(): Flow<List<BlockWithRules>> = blockDao.getAllBlocksWithRules()

    fun getEnabledBlocks(): Flow<List<Block>> = blockDao.getEnabledBlocks()

    fun getEnabledBlocksWithRules(): Flow<List<BlockWithRules>> = blockDao.getEnabledBlocksWithRules()

    suspend fun getBlockById(blockId: Long): Block? = blockDao.getBlockById(blockId)

    suspend fun getBlockWithRulesById(blockId: Long): BlockWithRules? = blockDao.getBlockWithRulesById(blockId)

    suspend fun createBlock(name: String): Long {
        val block = Block(name = name)
        val blockId = blockDao.insert(block)
        // Create an unlocked lock entry for the new block
        lockDao.insert(Lock(blockId = blockId, mode = LockMode.UNLOCKED))
        return blockId
    }

    suspend fun updateBlock(block: Block) = blockDao.update(block)

    suspend fun deleteBlock(blockId: Long) = blockDao.deleteById(blockId)

    suspend fun setBlockEnabled(blockId: Long, enabled: Boolean) = blockDao.setEnabled(blockId, enabled)

    suspend fun setScheduleActive(blockId: Long, active: Boolean) = blockDao.setScheduleActive(blockId, active)

    // App rule operations
    fun getAppRulesForBlock(blockId: Long): Flow<List<AppRule>> = appRuleDao.getAppRulesForBlock(blockId)

    suspend fun getAppRulesForBlockOnce(blockId: Long): List<AppRule> = appRuleDao.getAppRulesForBlockOnce(blockId)

    fun getBlockedPackageNames(): Flow<List<String>> = appRuleDao.getBlockedPackageNames()

    suspend fun addAppRule(blockId: Long, packageName: String): Long {
        return appRuleDao.insert(AppRule(blockId = blockId, packageName = packageName))
    }

    suspend fun removeAppRule(blockId: Long, packageName: String) {
        appRuleDao.deleteByPackageName(blockId, packageName)
    }

    // Website rule operations
    fun getWebsiteRulesForBlock(blockId: Long): Flow<List<WebsiteRule>> = websiteRuleDao.getWebsiteRulesForBlock(blockId)

    suspend fun getWebsiteRulesForBlockOnce(blockId: Long): List<WebsiteRule> = websiteRuleDao.getWebsiteRulesForBlockOnce(blockId)

    fun getActiveWebsiteRules(): Flow<List<WebsiteRule>> = websiteRuleDao.getActiveWebsiteRules()

    suspend fun addWebsiteRule(blockId: Long, domain: String, path: String? = null, isAllowed: Boolean = false): Long {
        return websiteRuleDao.insert(
            WebsiteRule(
                blockId = blockId,
                domain = domain,
                path = path,
                isAllowed = isAllowed
            )
        )
    }

    suspend fun removeWebsiteRule(ruleId: Long) = websiteRuleDao.deleteById(ruleId)

    // Lock operations
    suspend fun getLockForBlock(blockId: Long): Lock? = lockDao.getLockForBlock(blockId)

    fun getLockForBlockFlow(blockId: Long): Flow<Lock?> = lockDao.getLockForBlockFlow(blockId)

    fun getActiveLocks(): Flow<List<Lock>> = lockDao.getActiveLocks()

    suspend fun setLock(blockId: Long, mode: LockMode, unlockTime: Instant? = null) {
        lockDao.updateLockState(blockId, mode, unlockTime)
    }

    suspend fun unlock(blockId: Long) = lockDao.unlock(blockId)

    suspend fun getExpiredLocks(): List<Lock> = lockDao.getExpiredLocks(Instant.now())

    // Utility functions
    suspend fun isBlockLocked(blockId: Long): Boolean {
        val lock = lockDao.getLockForBlock(blockId)
        return lock != null && lock.mode != LockMode.UNLOCKED
    }

    suspend fun canModifyBlock(blockId: Long): Boolean {
        return !isBlockLocked(blockId)
    }

    // Schedule operations
    suspend fun getScheduleForBlock(blockId: Long): Schedule? = scheduleDao.getScheduleForBlock(blockId)

    fun getScheduleForBlockFlow(blockId: Long): Flow<Schedule?> = scheduleDao.getScheduleForBlockFlow(blockId)

    suspend fun getActiveSchedules(): List<Schedule> = scheduleDao.getActiveSchedules()

    fun getActiveSchedulesFlow(): Flow<List<Schedule>> = scheduleDao.getActiveSchedulesFlow()

    suspend fun setSchedule(blockId: Long, daysOfWeek: Int, startTimeMinutes: Int, endTimeMinutes: Int) {
        val existingSchedule = scheduleDao.getScheduleForBlock(blockId)
        if (existingSchedule != null) {
            scheduleDao.updateSchedule(blockId, daysOfWeek, startTimeMinutes, endTimeMinutes)
        } else {
            scheduleDao.insert(
                Schedule(
                    blockId = blockId,
                    daysOfWeek = daysOfWeek,
                    startTimeMinutes = startTimeMinutes,
                    endTimeMinutes = endTimeMinutes
                )
            )
        }
    }

    suspend fun removeSchedule(blockId: Long) {
        scheduleDao.deleteForBlock(blockId)
    }

    suspend fun setBlockState(blockId: Long, state: BlockState) {
        val block = blockDao.getBlockById(blockId) ?: return
        blockDao.update(block.copy(state = state))
    }
}
