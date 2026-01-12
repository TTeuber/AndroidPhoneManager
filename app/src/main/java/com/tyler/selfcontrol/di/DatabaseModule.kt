package com.tyler.selfcontrol.di

import android.content.Context
import androidx.room.Room
import com.tyler.selfcontrol.data.dao.AppRuleDao
import com.tyler.selfcontrol.data.dao.BlockDao
import com.tyler.selfcontrol.data.dao.LockDao
import com.tyler.selfcontrol.data.dao.WebsiteRuleDao
import com.tyler.selfcontrol.data.database.SelfControlDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SelfControlDatabase {
        return Room.databaseBuilder(
            context,
            SelfControlDatabase::class.java,
            SelfControlDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    fun provideBlockDao(database: SelfControlDatabase): BlockDao {
        return database.blockDao()
    }

    @Provides
    fun provideAppRuleDao(database: SelfControlDatabase): AppRuleDao {
        return database.appRuleDao()
    }

    @Provides
    fun provideWebsiteRuleDao(database: SelfControlDatabase): WebsiteRuleDao {
        return database.websiteRuleDao()
    }

    @Provides
    fun provideLockDao(database: SelfControlDatabase): LockDao {
        return database.lockDao()
    }
}
