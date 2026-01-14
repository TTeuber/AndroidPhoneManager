package com.tyler.selfcontrol.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.AllowedAppSource
import com.tyler.selfcontrol.data.model.AppCategory
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import com.tyler.selfcontrol.receiver.SelfControlDeviceAdminReceiver
import com.tyler.selfcontrol.worker.PlayStoreResuspensionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app installation control including:
 * - Evaluating whether apps can be installed
 * - Managing the cooldown queue
 * - Controlling Play Store suspension
 */
@Singleton
class AppInstallationManager @Inject constructor(
    private val playStoreParser: PlayStoreParser,
    private val repository: AppInstallationRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppInstallationManager"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val COOLDOWN_WINDOW_START_HOUR = 15  // 3 PM
        const val COOLDOWN_WINDOW_END_HOUR = 18    // 6 PM
        const val INSTALLATION_TIMEOUT_MINUTES = 5L

        private const val RESUSPENSION_WORK_NAME = "play_store_resuspension"
    }

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, SelfControlDeviceAdminReceiver::class.java)
    }

    /**
     * Result of evaluating an installation request.
     */
    sealed class InstallationDecision {
        /** App is on allowlist or unrestricted category - can install immediately */
        data class Allowed(val appInfo: PlayStoreParser.ParsedAppInfo) : InstallationDecision()

        /** App is already on allowlist */
        data class AlreadyAllowed(val appInfo: PlayStoreParser.ParsedAppInfo) : InstallationDecision()

        /** App requires cooldown - must wait for approval window */
        data class RequiresCooldown(
            val appInfo: PlayStoreParser.ParsedAppInfo,
            val windowStart: Instant,
            val windowEnd: Instant
        ) : InstallationDecision()

        /** App is blacklisted - cannot be installed */
        data class Blacklisted(val reason: String?) : InstallationDecision()

        /** App already has a pending cooldown request */
        data class PendingCooldown(val request: CooldownRequest) : InstallationDecision()

        /** Failed to evaluate - treat as restricted (fail-safe) */
        data class Error(val message: String) : InstallationDecision()
    }

    /**
     * Evaluate whether an app can be installed based on its Play Store URL.
     */
    suspend fun evaluateInstallation(playStoreUrl: String): InstallationDecision {
        // Parse the Play Store page
        val parseResult = playStoreParser.parsePlayStoreUrl(playStoreUrl)
        val appInfo = parseResult.getOrElse { error ->
            return InstallationDecision.Error(error.message ?: "Failed to parse Play Store URL")
        }

        // Check if blacklisted
        if (repository.isBlacklisted(appInfo.packageName)) {
            val blacklisted = repository.getBlacklistedAppsOnce()
                .find { it.packageName == appInfo.packageName }
            return InstallationDecision.Blacklisted(blacklisted?.reason)
        }

        // Check if already allowed
        if (repository.isAllowed(appInfo.packageName)) {
            return InstallationDecision.AlreadyAllowed(appInfo)
        }

        // Check for existing cooldown request
        val existingRequest = repository.getActiveRequestForPackage(appInfo.packageName)
        if (existingRequest != null) {
            return InstallationDecision.PendingCooldown(existingRequest)
        }

        // Determine if cooldown is required
        return if (playStoreParser.requiresCooldown(appInfo.category)) {
            val (windowStart, windowEnd) = calculateCooldownWindow()
            InstallationDecision.RequiresCooldown(appInfo, windowStart, windowEnd)
        } else {
            InstallationDecision.Allowed(appInfo)
        }
    }

    /**
     * Create a cooldown request for an app that requires it.
     */
    suspend fun createCooldownRequest(
        appInfo: PlayStoreParser.ParsedAppInfo,
        playStoreUrl: String
    ): CooldownRequest {
        val (windowStart, windowEnd) = calculateCooldownWindow()

        val request = CooldownRequest(
            packageName = appInfo.packageName,
            appName = appInfo.appName,
            playStoreUrl = playStoreUrl,
            category = appInfo.category,
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        val id = repository.createCooldownRequest(request)
        return request.copy(id = id)
    }

    /**
     * Approve a cooldown request during the approval window.
     * This adds the app to the allowlist and temporarily unsuspends Play Store.
     */
    suspend fun approveRequest(requestId: Long): Result<CooldownRequest> {
        val request = repository.getRequestById(requestId)
            ?: return Result.failure(AppInstallationException("Request not found"))

        if (request.status != CooldownStatus.WINDOW_OPEN) {
            return Result.failure(AppInstallationException("Request is not in approval window"))
        }

        val now = Instant.now()
        if (now.isBefore(request.windowStart)) {
            return Result.failure(AppInstallationException("Approval window has not opened yet"))
        }
        if (now.isAfter(request.windowEnd)) {
            return Result.failure(AppInstallationException("Approval window has expired"))
        }

        // Update status
        repository.updateRequestStatus(requestId, CooldownStatus.APPROVED)

        // Add to allowlist
        repository.addToAllowlist(
            packageName = request.packageName,
            appName = request.appName,
            source = AllowedAppSource.COOLDOWN_APPROVED
        )

        // Temporarily unsuspend Play Store and schedule resuspension
        unsuspendPlayStore()
        scheduleResuspension()

        return Result.success(request.copy(status = CooldownStatus.APPROVED))
    }

    /**
     * Cancel a cooldown request.
     */
    suspend fun cancelRequest(requestId: Long): Result<Unit> {
        repository.cancelRequest(requestId)
        return Result.success(Unit)
    }

    /**
     * Immediately add an app to the allowlist (for unrestricted apps).
     */
    suspend fun addToAllowlistImmediate(
        packageName: String,
        appName: String
    ): Result<AllowedApp> {
        // Check if blacklisted
        if (repository.isBlacklisted(packageName)) {
            return Result.failure(AppInstallationException("App is blacklisted"))
        }

        // Check if already allowed
        if (repository.isAllowed(packageName)) {
            val existing = repository.getByPackageName(packageName)
            return if (existing != null) {
                Result.success(existing)
            } else {
                Result.failure(AppInstallationException("Failed to get existing allowlist entry"))
            }
        }

        val id = repository.addToAllowlist(packageName, appName, AllowedAppSource.USER_ADDED)
        val app = AllowedApp(
            id = id,
            packageName = packageName,
            appName = appName,
            source = AllowedAppSource.USER_ADDED
        )

        // Temporarily unsuspend Play Store for installation
        unsuspendPlayStore()
        scheduleResuspension()

        return Result.success(app)
    }

    /**
     * Open Play Store to the app's page for installation.
     */
    fun openPlayStoreForInstall(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Calculate the cooldown window (3-6pm next day).
     */
    fun calculateCooldownWindow(): Pair<Instant, Instant> {
        val tomorrow = LocalDate.now().plusDays(1)
        val zoneId = ZoneId.systemDefault()

        val windowStart = tomorrow
            .atTime(LocalTime.of(COOLDOWN_WINDOW_START_HOUR, 0))
            .atZone(zoneId)
            .toInstant()

        val windowEnd = tomorrow
            .atTime(LocalTime.of(COOLDOWN_WINDOW_END_HOUR, 0))
            .atZone(zoneId)
            .toInstant()

        return windowStart to windowEnd
    }

    /**
     * Check if device owner mode is enabled.
     */
    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Suspend and hide the Play Store.
     */
    fun suspendPlayStore() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot suspend Play Store: not device owner")
            return
        }

        // Primary method: hide the Play Store from launcher
        val hidden = hidePlayStore()
        Log.d(TAG, "Play Store hide result: $hidden")

        // Secondary method: try suspension (known to fail on some devices)
        try {
            val failed = devicePolicyManager.setPackagesSuspended(
                adminComponent,
                arrayOf(PLAY_STORE_PACKAGE),
                true
            )
            if (failed.isNotEmpty()) {
                Log.w(TAG, "setPackagesSuspended failed for Play Store: ${failed.toList()}")
            } else {
                Log.d(TAG, "Play Store suspended via setPackagesSuspended")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to suspend Play Store", e)
        }
    }

    /**
     * Unsuspend and unhide the Play Store temporarily.
     */
    fun unsuspendPlayStore() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot unsuspend Play Store: not device owner")
            return
        }

        unhidePlayStore()

        try {
            devicePolicyManager.setPackagesSuspended(
                adminComponent,
                arrayOf(PLAY_STORE_PACKAGE),
                false
            )
            Log.d(TAG, "Play Store unsuspended")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsuspend Play Store", e)
        }
    }

    /**
     * Hide the Play Store from the launcher using setApplicationHidden.
     * @return true if hiding was successful, false otherwise
     */
    private fun hidePlayStore(): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot hide Play Store: not device owner")
            return false
        }

        return try {
            val result = devicePolicyManager.setApplicationHidden(
                adminComponent,
                PLAY_STORE_PACKAGE,
                true
            )
            Log.d(TAG, "setApplicationHidden returned: $result")
            if (!result) {
                Log.e(TAG, "setApplicationHidden returned false - Play Store may be protected by system")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide Play Store", e)
            false
        }
    }

    /**
     * Unhide the Play Store to allow installation.
     */
    private fun unhidePlayStore() {
        if (!isDeviceOwner()) return

        try {
            val result = devicePolicyManager.setApplicationHidden(
                adminComponent,
                PLAY_STORE_PACKAGE,
                false
            )
            Log.d(TAG, "Play Store unhidden: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unhide Play Store", e)
        }
    }

    /**
     * Schedule resuspension of Play Store after timeout.
     */
    private fun scheduleResuspension() {
        val workRequest = OneTimeWorkRequestBuilder<PlayStoreResuspensionWorker>()
            .setInitialDelay(INSTALLATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RESUSPENSION_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Cancel pending resuspension (e.g., when app is installed).
     */
    fun cancelResuspension() {
        WorkManager.getInstance(context).cancelUniqueWork(RESUSPENSION_WORK_NAME)
    }

    /**
     * Immediately resuspend Play Store (e.g., after installation detected).
     */
    fun resuspendPlayStoreNow() {
        cancelResuspension()
        suspendPlayStore()
    }
}

/**
 * Exception thrown when app installation operations fail.
 */
class AppInstallationException(message: String) : Exception(message)
