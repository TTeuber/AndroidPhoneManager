package com.tyler.selfcontrol.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.tyler.selfcontrol.data.datastore.SettingsDataStore
import com.tyler.selfcontrol.data.datastore.YouTubeRestrictLevel
import com.tyler.selfcontrol.data.model.WebsiteRule
import com.tyler.selfcontrol.data.repository.BlockRepository
import com.tyler.selfcontrol.receiver.SelfControlDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages content restrictions via DevicePolicyManager's setApplicationRestrictions().
 *
 * Handles restrictions for:
 * - Chrome: URL blocking, SafeSearch, YouTube restrict (web), incognito mode
 *
 * Note: The YouTube Android app does not support managed configurations, so YouTube
 * app restrictions are handled by blocking the app entirely via AppBlockingService
 * when content restrictions are enabled.
 */
@Singleton
class ContentRestrictionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockRepository: BlockRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, SelfControlDeviceAdminReceiver::class.java)
    }

    // Chrome package names to apply restrictions to
    private val chromePackages = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary"
    )

    /**
     * Update all content restrictions (Chrome only).
     * Should be called whenever settings change.
     *
     * Note: YouTube app restrictions are handled by blocking the app entirely
     * via AppBlockingService since YouTube doesn't support managed configurations.
     */
    suspend fun updateAllRestrictions() {
        updateChromeRestrictions()
    }

    /**
     * Update Chrome restrictions based on active website rules and settings.
     * Includes URL blocking, SafeSearch, YouTube restrict, and incognito mode.
     */
    suspend fun updateChromeRestrictions() {
        withContext(Dispatchers.IO) {
            if (!isDeviceOwner()) {
                Log.w(TAG, "Cannot update Chrome restrictions - not device owner")
                return@withContext
            }

            try {
                // Get website rules
                val rules = blockRepository.getActiveWebsiteRules().first()

                // Get safe search state early since we need it for blocked search engines
                val safeSearchState = settingsDataStore.safeSearchStateFlow.first()

                // Build blocklist from user rules + blocked search engines when safe search is on
                val userBlockedDomains = rules.filter { !it.isAllowed }.map { it.toChromeFormat() }
                val blockedSearchEngines = if (safeSearchState.value) {
                    listOf("duckduckgo.com", "bing.com", "yahoo.com")
                } else {
                    emptyList()
                }
                val blockList = (userBlockedDomains + blockedSearchEngines).distinct().toTypedArray()

                val allowList = rules
                    .filter { it.isAllowed }
                    .map { it.toChromeFormat() }
                    .distinct()
                    .toTypedArray()

                // Get content restriction settings (safeSearchState already retrieved above)
                val youtubeRestrictState = settingsDataStore.youtubeRestrictStateFlow.first()
                val incognitoDisabledState = settingsDataStore.incognitoDisabledStateFlow.first()

                Log.d(TAG, "Applying Chrome restrictions: ${blockList.size} blocked, ${allowList.size} allowed")
                Log.d(
                    TAG,
                    "SafeSearch: ${safeSearchState.value}, YouTubeRestrict: ${youtubeRestrictState.value}, " +
                        "IncognitoDisabled: ${incognitoDisabledState.value}"
                )

                val restrictions = Bundle().apply {
                    // URL blocking
                    if (blockList.isNotEmpty()) {
                        putStringArray("URLBlocklist", blockList)
                    }
                    if (allowList.isNotEmpty()) {
                        putStringArray("URLAllowlist", allowList)
                    }

                    // SafeSearch - forces Google SafeSearch
                    if (safeSearchState.value) {
                        putBoolean("ForceGoogleSafeSearch", true)
                    }

                    // YouTube Restrict for web - enforces restricted mode on youtube.com in Chrome
                    // 0 = Off (don't enforce), 1 = Moderate, 2 = Strict
                    if (youtubeRestrictState.value != YouTubeRestrictLevel.OFF) {
                        putInt("ForceYouTubeRestrict", youtubeRestrictState.value.value)
                    }

                    // Incognito mode availability
                    // "0" = Incognito mode available
                    // "1" = Incognito mode disabled
                    // "2" = Incognito mode forced (not useful for us)
                    if (incognitoDisabledState.value) {
                        putString("IncognitoModeAvailability", "1")
                    }
                }

                chromePackages.forEach { packageName ->
                    if (isPackageInstalled(packageName)) {
                        try {
                            devicePolicyManager.setApplicationRestrictions(
                                adminComponent,
                                packageName,
                                restrictions
                            )
                            Log.d(TAG, "Applied restrictions to $packageName")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to apply restrictions to $packageName", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update Chrome restrictions", e)
            }
        }
    }

    /**
     * Clear all Chrome URL restrictions.
     */
    suspend fun clearChromeRestrictions() {
        withContext(Dispatchers.IO) {
            if (!isDeviceOwner()) {
                Log.w(TAG, "Cannot clear Chrome restrictions - not device owner")
                return@withContext
            }

            chromePackages.forEach { packageName ->
                if (isPackageInstalled(packageName)) {
                    try {
                        devicePolicyManager.setApplicationRestrictions(
                            adminComponent,
                            packageName,
                            Bundle.EMPTY
                        )
                        Log.d(TAG, "Cleared restrictions from $packageName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to clear restrictions from $packageName", e)
                    }
                }
            }
        }
    }

    private fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        private const val TAG = "ContentRestrictionMgr"
    }
}

/**
 * Convert a WebsiteRule to Chrome's URL filter format.
 *
 * Chrome URL filter format: [scheme://][.]host[:port][/path][@query]
 *
 * Examples:
 * - "example.com" - Blocks domain and all subdomains
 * - "example.com/path" - Blocks specific path on domain
 * - ".example.com" - Blocks only the exact domain, not subdomains
 */
fun WebsiteRule.toChromeFormat(): String {
    // Normalize domain to lowercase
    val normalizedDomain = domain.lowercase().trim()

    // Handle wildcard subdomain pattern: *.example.com -> example.com
    // Chrome blocks subdomains by default, so *.example.com = example.com
    val chromeDomain = if (normalizedDomain.startsWith("*.")) {
        normalizedDomain.substring(2)
    } else {
        normalizedDomain
    }

    // Combine domain and path
    return if (path != null) {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        "$chromeDomain$normalizedPath"
    } else {
        chromeDomain
    }
}
