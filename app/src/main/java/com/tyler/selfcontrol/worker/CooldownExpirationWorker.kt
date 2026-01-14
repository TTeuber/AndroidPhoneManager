package com.tyler.selfcontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tyler.selfcontrol.data.model.CooldownStatus
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker that periodically checks for expired cooldown requests
 * and marks them as expired. Also cleans up old requests.
 */
@HiltWorker
class CooldownExpirationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AppInstallationRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Get requests that have passed their window end time
            val expiredRequests = repository.getExpiredRequests()

            for (request in expiredRequests) {
                // Mark as expired
                repository.updateRequestStatus(request.id, CooldownStatus.EXPIRED)
            }

            // Clean up old completed requests
            repository.cleanupOldRequests()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cooldown_expiration_worker"

        /**
         * Schedule the periodic cooldown expiration worker.
         * @param devMode If true, runs every 1 minute for testing. Otherwise runs every 15 minutes.
         */
        fun schedule(context: Context, devMode: Boolean = false) {
            val interval = if (devMode) 1L else 15L
            val timeUnit = TimeUnit.MINUTES

            val workRequest = PeriodicWorkRequestBuilder<CooldownExpirationWorker>(
                interval, timeUnit
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Cancel the periodic cooldown expiration worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
