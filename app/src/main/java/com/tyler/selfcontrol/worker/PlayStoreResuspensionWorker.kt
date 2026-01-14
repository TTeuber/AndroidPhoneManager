package com.tyler.selfcontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tyler.selfcontrol.domain.AppInstallationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that re-suspends the Play Store after the installation timeout.
 * This is a one-time worker scheduled when the Play Store is temporarily unsuspended.
 */
@HiltWorker
class PlayStoreResuspensionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appInstallationManager: AppInstallationManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Re-suspend the Play Store
            appInstallationManager.suspendPlayStore()
            Result.success()
        } catch (e: Exception) {
            // Still try to suspend even on failure
            try {
                appInstallationManager.suspendPlayStore()
            } catch (_: Exception) {
                // Ignore
            }
            Result.success()
        }
    }
}
