package com.tyler.selfcontrol.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
 * Manages Chrome URL blocking via DevicePolicyManager's setApplicationRestrictions().
 *
 * Converts WebsiteRule entities to Chrome's managed configuration format and applies
 * them as URLBlocklist and URLAllowlist restrictions.
 */
@Singleton
class ChromeRestrictionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockRepository: BlockRepository
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
     * Update Chrome URL restrictions based on active website rules.
     * Should be called whenever website rules change or blocks are enabled/disabled.
     */
    suspend fun updateChromeRestrictions() {
        withContext(Dispatchers.IO) {
            if (!isDeviceOwner()) {
                Log.w(TAG, "Cannot update Chrome restrictions - not device owner")
                return@withContext
            }

            try {
                val rules = blockRepository.getActiveWebsiteRules().first()

                val blockList = rules
                    .filter { !it.isAllowed }
                    .map { it.toChromeFormat() }
                    .distinct()
                    .toTypedArray()

                val allowList = rules
                    .filter { it.isAllowed }
                    .map { it.toChromeFormat() }
                    .distinct()
                    .toTypedArray()

                Log.d(TAG, "Applying Chrome restrictions: ${blockList.size} blocked, ${allowList.size} allowed")
                if (blockList.isNotEmpty()) {
                    Log.d(TAG, "URLBlocklist: ${blockList.toList()}")
                }
                if (allowList.isNotEmpty()) {
                    Log.d(TAG, "URLAllowlist: ${allowList.toList()}")
                }

                val restrictions = Bundle().apply {
                    if (blockList.isNotEmpty()) {
                        putStringArray("URLBlocklist", blockList)
                    }
                    if (allowList.isNotEmpty()) {
                        putStringArray("URLAllowlist", allowList)
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
        private const val TAG = "ChromeRestrictionMgr"
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
