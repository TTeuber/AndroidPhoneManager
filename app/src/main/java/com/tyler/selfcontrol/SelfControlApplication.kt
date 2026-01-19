package com.tyler.selfcontrol

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import com.tyler.selfcontrol.worker.CooldownExpirationWorker
import com.tyler.selfcontrol.worker.CooldownNotificationWorker
import com.tyler.selfcontrol.worker.ScheduleWorker
import com.tyler.selfcontrol.worker.UnlockWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SelfControlApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var appInstallationRepository: AppInstallationRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Schedule the periodic workers with correct interval based on dev mode
        applicationScope.launch {
            val devMode = settingsDataStore.devModeFlow.first()

            // Schedule existing workers
            UnlockWorker.schedule(this@SelfControlApplication, devMode)
            ScheduleWorker.schedule(this@SelfControlApplication, devMode)

            // Schedule cooldown workers
            CooldownNotificationWorker.schedule(this@SelfControlApplication, devMode)
            CooldownExpirationWorker.schedule(this@SelfControlApplication, devMode)

            // Initialize blacklist with default entries if empty
            initializeBlacklistIfNeeded()
            appInstallationRepository.removeFromBlacklist("com.google.android.youtube")
        }
    }

    private suspend fun initializeBlacklistIfNeeded() {
        val existingBlacklist = appInstallationRepository.getBlacklistedAppsOnce()
        if (existingBlacklist.isEmpty()) {
            appInstallationRepository.initializeBlacklist()
        }
    }
}
