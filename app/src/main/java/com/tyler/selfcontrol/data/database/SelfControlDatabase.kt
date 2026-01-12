package com.tyler.selfcontrol.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tyler.selfcontrol.data.dao.AppRuleDao
import com.tyler.selfcontrol.data.dao.BlockDao
import com.tyler.selfcontrol.data.dao.LockDao
import com.tyler.selfcontrol.data.dao.ScheduleDao
import com.tyler.selfcontrol.data.dao.WebsiteRuleDao
import com.tyler.selfcontrol.data.model.AppRule
import com.tyler.selfcontrol.data.model.Block
import com.tyler.selfcontrol.data.model.Lock
import com.tyler.selfcontrol.data.model.Schedule
import com.tyler.selfcontrol.data.model.WebsiteRule

@Database(
    entities = [
        Block::class,
        AppRule::class,
        WebsiteRule::class,
        Lock::class,
        Schedule::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SelfControlDatabase : RoomDatabase() {

    abstract fun blockDao(): BlockDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun websiteRuleDao(): WebsiteRuleDao
    abstract fun lockDao(): LockDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        const val DATABASE_NAME = "selfcontrol_db"
    }
}
