package com.tyler.selfcontrol.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.AllowedAppSource
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app installation control including:
 * - Evaluating whether apps can be installed
 * - Managing the cooldown queue
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
     * This adds the app to the allowlist.
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
        Log.d(TAG, "addToAllowlistImmediate() called for: $packageName ($appName)")

        // Check if blacklisted
        if (repository.isBlacklisted(packageName)) {
            Log.w(TAG, "addToAllowlistImmediate: $packageName is blacklisted")
            return Result.failure(AppInstallationException("App is blacklisted"))
        }

        // Check if already allowed
        if (repository.isAllowed(packageName)) {
            Log.d(TAG, "addToAllowlistImmediate: $packageName is already allowed")
            val existing = repository.getByPackageName(packageName)
            return if (existing != null) {
                Result.success(existing)
            } else {
                Result.failure(AppInstallationException("Failed to get existing allowlist entry"))
            }
        }

        Log.d(TAG, "addToAllowlistImmediate: Adding $packageName to allowlist")
        val id = repository.addToAllowlist(packageName, appName, AllowedAppSource.USER_ADDED)
        val app = AllowedApp(
            id = id,
            packageName = packageName,
            appName = appName,
            source = AllowedAppSource.USER_ADDED
        )

        return Result.success(app)
    }

    /**
     * Open Play Store to the app's page for installation.
     */
    fun openPlayStoreForInstall(packageName: String) {
        Log.d(TAG, "openPlayStoreForInstall() called for: $packageName")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Log.d(TAG, "openPlayStoreForInstall: Intent started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "openPlayStoreForInstall: Failed to start intent", e)
        }
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
}

/**
 * Exception thrown when app installation operations fail.
 */
class AppInstallationException(message: String) : Exception(message)
