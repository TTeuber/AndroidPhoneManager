package com.tyler.selfcontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.domain.LockManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker that periodically checks for expired locks and unlocks them.
 * This includes both block locks and the Clear Device Owner lock.
 */
@HiltWorker
class UnlockWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val lockManager: LockManager,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Process expired block locks
            val unlockCount = lockManager.processExpiredLocks()
            if (unlockCount > 0) {
                // Could add notification here if desired
            }

            // Process expired Clear Device Owner lock
            settingsDataStore.checkAndClearExpiredDeviceOwnerLock()

            // Process expired content restriction settings locks
            settingsDataStore.checkAndClearExpiredSafeSearchLock()
            settingsDataStore.checkAndClearExpiredYouTubeRestrictLock()
            settingsDataStore.checkAndClearExpiredIncognitoDisabledLock()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val DEV_INTERVAL_MINUTES = 1L
        private const val DEFAULT_INTERVAL_MINUTES = 15L
        private const val WORK_NAME = "unlock_worker"

        /**
         * Schedule the periodic unlock worker.
         * @param devMode If true, runs every 1 minute for testing. Otherwise runs every 15 minutes.
         */
        fun schedule(context: Context, devMode: Boolean = false) {
            val interval = if (devMode) DEV_INTERVAL_MINUTES else DEFAULT_INTERVAL_MINUTES
            val timeUnit = TimeUnit.MINUTES

            val workRequest = PeriodicWorkRequestBuilder<UnlockWorker>(
                interval, timeUnit
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Use UPDATE to reschedule with new interval
                workRequest
            )
        }

        /**
         * Cancel the periodic unlock worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
