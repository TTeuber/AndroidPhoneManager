package com.tyler.selfcontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tyler.selfcontrol.domain.ScheduleManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker that periodically checks schedules and updates block enabled states.
 */
@HiltWorker
class ScheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduleManager: ScheduleManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val changedCount = scheduleManager.processSchedules()
            if (changedCount > 0) {
                // Blocks were enabled/disabled based on schedule
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "schedule_worker"

        /**
         * Schedule the periodic schedule worker.
         * @param devMode If true, runs every 1 minute for testing. Otherwise runs every 5 minutes.
         */
        fun schedule(context: Context, devMode: Boolean = false) {
            val interval = if (devMode) 1L else 5L
            val timeUnit = TimeUnit.MINUTES

            val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
                interval, timeUnit
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Cancel the periodic schedule worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
