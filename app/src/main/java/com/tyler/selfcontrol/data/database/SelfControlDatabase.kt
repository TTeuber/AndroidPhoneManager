package com.tyler.selfcontrol.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tyler.selfcontrol.data.dao.AllowedAppDao
import com.tyler.selfcontrol.data.dao.AppRuleDao
import com.tyler.selfcontrol.data.dao.BlacklistedAppDao
import com.tyler.selfcontrol.data.dao.BlockDao
import com.tyler.selfcontrol.data.dao.CooldownRequestDao
import com.tyler.selfcontrol.data.dao.LockDao
import com.tyler.selfcontrol.data.dao.ScheduleDao
import com.tyler.selfcontrol.data.dao.WebsiteRuleDao
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.AppRule
import com.tyler.selfcontrol.data.model.BlacklistedApp
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.model.WebsiteRule

@Database(
    entities = [
        Block::class,
        AppRule::class,
        WebsiteRule::class,
        Lock::class,
        Schedule::class,
        AllowedApp::class,
        BlacklistedApp::class,
        CooldownRequest::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SelfControlDatabase : RoomDatabase() {

    abstract fun blockDao(): BlockDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun websiteRuleDao(): WebsiteRuleDao
    abstract fun lockDao(): LockDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun blacklistedAppDao(): BlacklistedAppDao
    abstract fun cooldownRequestDao(): CooldownRequestDao

    companion object {
        const val DATABASE_NAME = "selfcontrol_db"
    }
}
