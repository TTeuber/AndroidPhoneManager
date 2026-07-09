package com.tyler.selfcontrol.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tyler.selfcontrol.data.database.SelfControlDatabase
import com.tyler.selfcontrol.data.model.AppRule
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.BlockState
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.LockMode
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.model.WebsiteRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room DAO tests exercising the real SQLite implementation via Robolectric.
 *
 * Covers the critical [AppRuleDao.getBlockedPackageNames] query (the source of truth
 * for which packages get suspended) and the ON DELETE CASCADE foreign keys that tie
 * app rules, website rules, schedules and locks to their parent block.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockDaoTest {

    private lateinit var db: SelfControlDatabase
    private lateinit var blockDao: BlockDao
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var websiteRuleDao: WebsiteRuleDao
    private lateinit var scheduleDao: ScheduleDao
    private lateinit var lockDao: LockDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SelfControlDatabase::class.java
        ).allowMainThreadQueries().build()
        blockDao = db.blockDao()
        appRuleDao = db.appRuleDao()
        websiteRuleDao = db.websiteRuleDao()
        scheduleDao = db.scheduleDao()
        lockDao = db.lockDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertBlock(
        name: String,
        isEnabled: Boolean,
        state: BlockState,
        isScheduleActive: Boolean = true
    ): Long = blockDao.insert(
        Block(
            name = name,
            isEnabled = isEnabled,
            state = state,
            isScheduleActive = isScheduleActive
        )
    )

    // --- getBlockedPackageNames() ---

    @Test
    fun `getBlockedPackageNames returns packages for enabled ALWAYS_ON block`() = runTest {
        val blockId = insertBlock("Always", isEnabled = true, state = BlockState.ALWAYS_ON)
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.a"))
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.b"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertEquals(setOf("com.example.a", "com.example.b"), blocked.toSet())
    }

    @Test
    fun `getBlockedPackageNames returns packages for enabled active SCHEDULED block`() = runTest {
        val blockId = insertBlock(
            "Scheduled",
            isEnabled = true,
            state = BlockState.SCHEDULED,
            isScheduleActive = true
        )
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.sched"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertEquals(listOf("com.example.sched"), blocked)
    }

    @Test
    fun `getBlockedPackageNames excludes SCHEDULED block when schedule inactive`() = runTest {
        val blockId = insertBlock(
            "Scheduled",
            isEnabled = true,
            state = BlockState.SCHEDULED,
            isScheduleActive = false
        )
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.sched"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertTrue(blocked.isEmpty())
    }

    @Test
    fun `getBlockedPackageNames excludes disabled ALWAYS_ON block`() = runTest {
        val blockId = insertBlock("Disabled", isEnabled = false, state = BlockState.ALWAYS_ON)
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.off"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertTrue(blocked.isEmpty())
    }

    @Test
    fun `getBlockedPackageNames excludes disabled SCHEDULED block even when schedule active`() = runTest {
        val blockId = insertBlock(
            "DisabledSched",
            isEnabled = false,
            state = BlockState.SCHEDULED,
            isScheduleActive = true
        )
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.off"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertTrue(blocked.isEmpty())
    }

    @Test
    fun `getBlockedPackageNames deduplicates a package present in multiple blocks`() = runTest {
        val alwaysOn = insertBlock("Always", isEnabled = true, state = BlockState.ALWAYS_ON)
        val scheduled = insertBlock(
            "Scheduled",
            isEnabled = true,
            state = BlockState.SCHEDULED,
            isScheduleActive = true
        )
        appRuleDao.insert(AppRule(blockId = alwaysOn, packageName = "com.example.dup"))
        appRuleDao.insert(AppRule(blockId = scheduled, packageName = "com.example.dup"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertEquals(listOf("com.example.dup"), blocked)
    }

    @Test
    fun `getBlockedPackageNames returns only packages from qualifying blocks in a mixed set`() = runTest {
        val enabledAlwaysOn = insertBlock("A", isEnabled = true, state = BlockState.ALWAYS_ON)
        val activeScheduled = insertBlock(
            "B",
            isEnabled = true,
            state = BlockState.SCHEDULED,
            isScheduleActive = true
        )
        val inactiveScheduled = insertBlock(
            "C",
            isEnabled = true,
            state = BlockState.SCHEDULED,
            isScheduleActive = false
        )
        val disabled = insertBlock("D", isEnabled = false, state = BlockState.ALWAYS_ON)

        appRuleDao.insert(AppRule(blockId = enabledAlwaysOn, packageName = "com.blocked.one"))
        appRuleDao.insert(AppRule(blockId = activeScheduled, packageName = "com.blocked.two"))
        appRuleDao.insert(AppRule(blockId = inactiveScheduled, packageName = "com.free.one"))
        appRuleDao.insert(AppRule(blockId = disabled, packageName = "com.free.two"))

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertEquals(setOf("com.blocked.one", "com.blocked.two"), blocked.toSet())
    }

    @Test
    fun `getBlockedPackageNames returns empty when nothing qualifies`() = runTest {
        insertBlock("Empty", isEnabled = true, state = BlockState.ALWAYS_ON)

        val blocked = appRuleDao.getBlockedPackageNames().first()

        assertTrue(blocked.isEmpty())
    }

    // --- Cascade deletes ---

    @Test
    fun `deleting a block cascades to its app rules`() = runTest {
        val blockId = insertBlock("Block", isEnabled = true, state = BlockState.ALWAYS_ON)
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.a"))
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.b"))
        assertEquals(2, appRuleDao.getAppRulesForBlockOnce(blockId).size)

        blockDao.deleteById(blockId)

        assertTrue(appRuleDao.getAppRulesForBlockOnce(blockId).isEmpty())
    }

    @Test
    fun `deleting a block cascades to its website rules`() = runTest {
        val blockId = insertBlock("Block", isEnabled = true, state = BlockState.ALWAYS_ON)
        websiteRuleDao.insert(WebsiteRule(blockId = blockId, domain = "example.com"))
        assertEquals(1, websiteRuleDao.getWebsiteRulesForBlockOnce(blockId).size)

        blockDao.deleteById(blockId)

        assertTrue(websiteRuleDao.getWebsiteRulesForBlockOnce(blockId).isEmpty())
    }

    @Test
    fun `deleting a block cascades to its schedule`() = runTest {
        val blockId = insertBlock("Block", isEnabled = true, state = BlockState.SCHEDULED)
        scheduleDao.insert(
            Schedule(
                blockId = blockId,
                daysOfWeek = Schedule.ALL_DAYS,
                startTimeMinutes = 0,
                endTimeMinutes = 60
            )
        )
        assertNotNull(scheduleDao.getScheduleForBlock(blockId))

        blockDao.deleteById(blockId)

        assertNull(scheduleDao.getScheduleForBlock(blockId))
    }

    @Test
    fun `deleting a block cascades to its lock`() = runTest {
        val blockId = insertBlock("Block", isEnabled = true, state = BlockState.ALWAYS_ON)
        lockDao.insert(Lock(blockId = blockId, mode = LockMode.FOREVER))
        assertNotNull(lockDao.getLockForBlock(blockId))

        blockDao.deleteById(blockId)

        assertNull(lockDao.getLockForBlock(blockId))
    }

    @Test
    fun `deleting a block via entity cascades to all child rows`() = runTest {
        val blockId = insertBlock("Block", isEnabled = true, state = BlockState.SCHEDULED)
        appRuleDao.insert(AppRule(blockId = blockId, packageName = "com.example.a"))
        websiteRuleDao.insert(WebsiteRule(blockId = blockId, domain = "example.com"))
        scheduleDao.insert(Schedule(blockId = blockId, daysOfWeek = Schedule.ALL_DAYS))
        lockDao.insert(Lock(blockId = blockId, mode = LockMode.FOREVER))

        val block = blockDao.getBlockById(blockId)
        assertNotNull(block)
        blockDao.delete(block!!)

        assertTrue(appRuleDao.getAppRulesForBlockOnce(blockId).isEmpty())
        assertTrue(websiteRuleDao.getWebsiteRulesForBlockOnce(blockId).isEmpty())
        assertNull(scheduleDao.getScheduleForBlock(blockId))
        assertNull(lockDao.getLockForBlock(blockId))
    }

    @Test
    fun `deleting one block leaves another block's rules intact`() = runTest {
        val keep = insertBlock("Keep", isEnabled = true, state = BlockState.ALWAYS_ON)
        val remove = insertBlock("Remove", isEnabled = true, state = BlockState.ALWAYS_ON)
        appRuleDao.insert(AppRule(blockId = keep, packageName = "com.example.keep"))
        appRuleDao.insert(AppRule(blockId = remove, packageName = "com.example.remove"))

        blockDao.deleteById(remove)

        assertEquals(1, appRuleDao.getAppRulesForBlockOnce(keep).size)
        assertEquals("com.example.keep", appRuleDao.getAppRulesForBlockOnce(keep).first().packageName)
    }
}
