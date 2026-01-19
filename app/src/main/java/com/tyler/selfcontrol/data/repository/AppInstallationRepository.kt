package com.tyler.selfcontrol.data.repository

import com.tyler.selfcontrol.data.dao.AllowedAppDao
import com.tyler.selfcontrol.data.dao.BlacklistedAppDao
import com.tyler.selfcontrol.data.dao.CooldownRequestDao
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.AllowedAppSource
import com.tyler.selfcontrol.data.model.BlacklistedApp
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInstallationRepository @Inject constructor(
    private val allowedAppDao: AllowedAppDao,
    private val blacklistedAppDao: BlacklistedAppDao,
    private val cooldownRequestDao: CooldownRequestDao
) {
    // Allowlist operations
    fun getAllowedApps(): Flow<List<AllowedApp>> = allowedAppDao.getAllAllowedApps()

    suspend fun getAllowedAppsOnce(): List<AllowedApp> = allowedAppDao.getAllAllowedAppsOnce()

    suspend fun getAllowedPackageNames(): List<String> = allowedAppDao.getAllowedPackageNames()

    /**
     * Get allowed package names as a Flow for reactive updates.
     */
    fun getAllowedPackageNamesFlow(): Flow<Set<String>> =
        allowedAppDao.getAllAllowedApps().map { apps -> apps.map { it.packageName }.toSet() }

    suspend fun isAllowed(packageName: String): Boolean = allowedAppDao.isAllowed(packageName)

    suspend fun getByPackageName(packageName: String): AllowedApp? =
        allowedAppDao.getByPackageName(packageName)

    suspend fun addToAllowlist(
        packageName: String,
        appName: String,
        source: AllowedAppSource = AllowedAppSource.USER_ADDED
    ): Long {
        return allowedAppDao.insert(
            AllowedApp(
                packageName = packageName,
                appName = appName,
                source = source
            )
        )
    }

    suspend fun addAllToAllowlist(apps: List<AllowedApp>) = allowedAppDao.insertAll(apps)

    suspend fun removeFromAllowlist(packageName: String) =
        allowedAppDao.deleteByPackageName(packageName)

    // Blacklist operations
    fun getBlacklistedApps(): Flow<List<BlacklistedApp>> = blacklistedAppDao.getAllBlacklistedApps()

    suspend fun getBlacklistedAppsOnce(): List<BlacklistedApp> =
        blacklistedAppDao.getAllBlacklistedAppsOnce()

    suspend fun isBlacklisted(packageName: String): Boolean =
        blacklistedAppDao.isBlacklisted(packageName)

    suspend fun addToBlacklist(
        packageName: String,
        appName: String,
        reason: String? = null
    ): Long {
        return blacklistedAppDao.insert(
            BlacklistedApp(
                packageName = packageName,
                appName = appName,
                reason = reason
            )
        )
    }

    suspend fun addAllToBlacklist(apps: List<BlacklistedApp>) = blacklistedAppDao.insertAll(apps)

    suspend fun removeFromBlacklist(packageName: String) =
        blacklistedAppDao.deleteByPackageName(packageName)

    // Cooldown request operations
    fun getActiveRequests(): Flow<List<CooldownRequest>> = cooldownRequestDao.getActiveRequests()

    suspend fun getActiveRequestsOnce(): List<CooldownRequest> =
        cooldownRequestDao.getActiveRequestsOnce()

    suspend fun getRequestById(id: Long): CooldownRequest? = cooldownRequestDao.getById(id)

    suspend fun getActiveRequestForPackage(packageName: String): CooldownRequest? =
        cooldownRequestDao.getActiveRequestForPackage(packageName)

    suspend fun getRequestsReadyForWindow(): List<CooldownRequest> =
        cooldownRequestDao.getRequestsReadyForWindow(Instant.now())

    suspend fun getExpiredRequests(): List<CooldownRequest> =
        cooldownRequestDao.getExpiredRequests(Instant.now())

    suspend fun createCooldownRequest(request: CooldownRequest): Long =
        cooldownRequestDao.insert(request)

    suspend fun updateRequestStatus(id: Long, status: CooldownStatus) =
        cooldownRequestDao.updateStatus(id, status)

    suspend fun cancelRequest(id: Long) =
        cooldownRequestDao.updateStatus(id, CooldownStatus.CANCELLED)

    suspend fun approveRequest(id: Long) =
        cooldownRequestDao.updateStatus(id, CooldownStatus.APPROVED)

    suspend fun expireRequest(id: Long) =
        cooldownRequestDao.updateStatus(id, CooldownStatus.EXPIRED)

    suspend fun deleteRequest(id: Long) = cooldownRequestDao.deleteById(id)

    suspend fun cleanupOldRequests() = cooldownRequestDao.cleanupOldRequests()

    // Pre-population helpers
    suspend fun initializeBlacklist() {
        val defaultBlacklist = listOf(
            BlacklistedApp(packageName = "com.instagram.android", appName = "Instagram", reason = "Social media"),
            BlacklistedApp(packageName = "com.zhiliaoapp.musically", appName = "TikTok", reason = "Social media"),
            BlacklistedApp(packageName = "com.ss.android.ugc.trill", appName = "TikTok", reason = "Social media"),
            BlacklistedApp(packageName = "com.twitter.android", appName = "Twitter/X", reason = "Social media"),
            BlacklistedApp(packageName = "com.reddit.frontpage", appName = "Reddit", reason = "Social media"),
            BlacklistedApp(packageName = "com.facebook.katana", appName = "Facebook", reason = "Social media"),
            BlacklistedApp(packageName = "com.snapchat.android", appName = "Snapchat", reason = "Social media"),
            BlacklistedApp(packageName = "com.laurencedawson.reddit_sync", appName = "Sync for Reddit", reason = "Reddit client"),
            BlacklistedApp(packageName = "com.andrewshu.android.reddit", appName = "Reddit is Fun", reason = "Reddit client"),
            BlacklistedApp(packageName = "com.rubenmayayo.reddit", appName = "Boost for Reddit", reason = "Reddit client"),
            BlacklistedApp(packageName = "ml.docilealligator.infinityforreddit", appName = "Infinity for Reddit", reason = "Reddit client"),
            BlacklistedApp(packageName = "free.reddit.news", appName = "Relay for Reddit", reason = "Reddit client"),
            BlacklistedApp(packageName = "tv.twitch.android.app", appName = "Twitch", reason = "Entertainment"),
            BlacklistedApp(packageName = "com.netflix.mediaclient", appName = "Netflix", reason = "Entertainment"),
            BlacklistedApp(packageName = "com.hulu.plus", appName = "Hulu", reason = "Entertainment"),
            BlacklistedApp(packageName = "com.amazon.avod.thirdpartyclient", appName = "Prime Video", reason = "Entertainment")
        )
        addAllToBlacklist(defaultBlacklist)
    }
}
