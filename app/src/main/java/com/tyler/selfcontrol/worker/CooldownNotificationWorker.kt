package com.tyler.selfcontrol.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tyler.selfcontrol.MainActivity
import com.tyler.selfcontrol.R
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker that periodically checks for cooldown requests ready for approval
 * and sends notifications when the approval window opens.
 */
@HiltWorker
class CooldownNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AppInstallationRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Get requests that are ready for their window to open
            val readyRequests = repository.getRequestsReadyForWindow()

            for (request in readyRequests) {
                // Update status to WINDOW_OPEN
                repository.updateRequestStatus(request.id, CooldownStatus.WINDOW_OPEN)

                // Send notification
                sendNotification(request)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(request: CooldownRequest) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

        // Create notification channel if needed
        createNotificationChannel(notificationManager)

        // Create intent to open the app
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(EXTRA_NAVIGATE_TO, "pending_approvals")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            request.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("App Ready for Approval")
            .setContentText("${request.appName} can now be approved until 6 PM")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_TAG, request.id.toInt(), notification)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Approval Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when apps are ready for approval"
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val DEV_INTERVAL_MINUTES = 1L
        private const val DEFAULT_INTERVAL_MINUTES = 15L
        private const val WORK_NAME = "cooldown_notification_worker"
        private const val CHANNEL_ID = "cooldown_channel"
        private const val NOTIFICATION_TAG = "cooldown_notification"
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        /**
         * Schedule the periodic cooldown notification worker.
         * @param devMode If true, runs every 1 minute for testing. Otherwise runs every 15 minutes.
         */
        fun schedule(context: Context, devMode: Boolean = false) {
            val interval = if (devMode) DEV_INTERVAL_MINUTES else DEFAULT_INTERVAL_MINUTES
            val timeUnit = TimeUnit.MINUTES

            val workRequest = PeriodicWorkRequestBuilder<CooldownNotificationWorker>(
                interval, timeUnit
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Cancel the periodic cooldown notification worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
