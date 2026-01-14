package com.tyler.selfcontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tyler.selfcontrol.MainActivity
import com.tyler.selfcontrol.R
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import com.tyler.selfcontrol.data.repository.BlockRepository
import com.tyler.selfcontrol.receiver.SelfControlDeviceAdminReceiver
import com.tyler.selfcontrol.util.UsageStatsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockingService : Service() {

    @Inject
    lateinit var appInstallationRepository: AppInstallationRepository

    @Inject
    lateinit var blockRepository: BlockRepository

    @Inject
    lateinit var usageStatsHelper: UsageStatsHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringJob: Job? = null

    private var blockedPackages = setOf<String>()
    private var isMonitoring = false

    // Retry tracking for failed suspensions
    private var packagesNeedingRetry = setOf<String>()
    private var lastRetryAttempt = 0L

    // Flow to trigger recalculation when installed packages change
    private val installedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())

    private val devicePolicyManager: DevicePolicyManager by lazy {
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(this, SelfControlDeviceAdminReceiver::class.java)
    }

    private val checkForegroundRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkAndBlockForegroundApp()

                // Periodically retry suspending packages that failed earlier
                val now = System.currentTimeMillis()
                if (now - lastRetryAttempt >= RETRY_INTERVAL_MS) {
                    lastRetryAttempt = now
                    retryFailedSuspensions()
                }

                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    // Dynamic receiver for package changes (backup for manifest registration)
    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            Log.d(TAG, "Dynamic receiver: ${intent.action} for $packageName")

            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    Log.d(TAG, "Package installed/replaced: $packageName - triggering update")
                    // Refresh installed packages with a small delay
                    serviceScope.launch {
                        delay(500)
                        installedPackagesFlow.value = getInstalledPackages()
                        updateSuspendedApps()
                    }
                }
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    Log.d(TAG, "Package removed: $packageName - triggering update")
                    serviceScope.launch {
                        installedPackagesFlow.value = getInstalledPackages()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        // Initialize installed packages flow
        installedPackagesFlow.value = getInstalledPackages()
        observeBlockedPackages()

        // Register dynamic receiver for package changes
        // This is more reliable than manifest registration on newer Android versions
        val packageFilter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, packageFilter)
        Log.d(TAG, "Registered dynamic package change receiver")

        // Retry suspension after a delay to handle device owner setup timing
        // This catches cases where the service starts before device owner is set
        serviceScope.launch {
            delay(3000) // Wait for device owner to potentially be set
            if (blockedPackages.isNotEmpty()) {
                Log.d(TAG, "Startup retry: attempting to suspend ${blockedPackages.size} blocked packages")
                retryFailedSuspensions()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_BLOCKS -> {
                // Refresh installed packages to trigger recalculation
                Log.d(TAG, "ACTION_UPDATE_BLOCKS received, refreshing installed packages")
                // Add a small delay to ensure the system has registered the new package
                serviceScope.launch {
                    delay(500) // Give system time to register the package
                    installedPackagesFlow.value = getInstalledPackages()
                    updateSuspendedApps()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister package change receiver", e)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Observe all sources of blocked packages and combine them.
     * Uses the same pattern as the original working code.
     *
     * Note: installedPackagesFlow is included to trigger recalculation when
     * new packages are installed (via PackageChangeReceiver -> ACTION_UPDATE_BLOCKS).
     */
    private fun observeBlockedPackages() {
        serviceScope.launch {
            // Combine all four sources of blocking info
            combine(
                blockRepository.getBlockedPackageNames(),
                appInstallationRepository.getBlacklistedApps(),
                appInstallationRepository.getAllowedPackageNamesFlow(),
                installedPackagesFlow
            ) { blockRulePackages, blacklistedApps, allowedPackages, allInstalledPackages ->
                // Calculate all packages that should be blocked

                // Source 1: Packages from user-defined blocks (original Phase 3-5 functionality)
                val fromBlockRules = blockRulePackages.filter { pkg ->
                    pkg != packageName
                }.toSet()

                // Source 2: Packages on the blacklist - blocked regardless of system status
                val blacklistedPackageNames = blacklistedApps.map { it.packageName }.toSet()
                Log.d(TAG, "Blacklist DB contains: $blacklistedPackageNames")
                val fromBlacklist = blacklistedPackageNames.filter { pkg ->
                    pkg != packageName && allInstalledPackages.contains(pkg)
                }.toSet()
                Log.d(TAG, "Blacklisted apps that are installed: $fromBlacklist")

                // Source 3: Packages not on allowlist (only non-system packages)
                val notOnAllowlist = allInstalledPackages.filter { pkg ->
                    !isSystemPackage(pkg) &&
                    !allowedPackages.contains(pkg) &&
                    pkg != packageName
                }.toSet()

                Log.d(TAG, "Block rules: ${fromBlockRules.size}, Blacklist: ${fromBlacklist.size}, Not on allowlist: ${notOnAllowlist.size}")

                // Combine all sources
                fromBlockRules + fromBlacklist + notOnAllowlist
            }.collectLatest { newBlockedPackages ->
                val previouslyBlocked = blockedPackages
                blockedPackages = newBlockedPackages

                Log.d(TAG, "observeBlockedPackages: Flow emitted, total blocked packages: ${newBlockedPackages.size}")

                // Debug: Check specifically for YouTube
                val youtubePackage = "com.google.android.youtube"
                if (youtubePackage in newBlockedPackages) {
                    Log.d(TAG, "YouTube IS in blocked packages")
                } else {
                    Log.w(TAG, "YouTube is NOT in blocked packages")
                }

                // Unsuspend apps that are no longer blocked
                val toUnsuspend = previouslyBlocked - newBlockedPackages
                if (toUnsuspend.isNotEmpty()) {
                    Log.d(TAG, "Unsuspending ${toUnsuspend.size} apps")
                    setPackagesSuspended(toUnsuspend.toTypedArray(), false)
                }

                // Always suspend ALL blocked packages, not just new ones
                // This handles the case where packages were added to blockedPackages
                // but failed to suspend (e.g., device owner wasn't set at the time)
                if (newBlockedPackages.isNotEmpty()) {
                    Log.d(TAG, "Ensuring all ${newBlockedPackages.size} blocked packages are suspended")
                    setPackagesSuspended(newBlockedPackages.toTypedArray(), true)
                }
            }
        }
    }

    /**
     * Get all installed package names on the device.
     */
    private fun getInstalledPackages(): Set<String> {
        return try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }
                .toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed packages", e)
            emptySet()
        }
    }

    /**
     * Check if a package is a system package that should never be blocked.
     * Includes com.android*, com.google*, android*
     */
    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android") ||
               packageName.startsWith("com.google") ||
               packageName.startsWith("android") ||
               isSystemCriticalPackage(packageName)
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        handler.post(checkForegroundRunnable)

        // Also ensure all blocked packages are suspended
        updateSuspendedApps()
    }

    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(checkForegroundRunnable)
    }

    private fun updateSuspendedApps() {
        // Re-apply ALL current blocks (not just new ones)
        // This ensures packages get suspended even if they were in blockedPackages
        // but failed to suspend earlier (e.g., device owner wasn't set)
        if (blockedPackages.isNotEmpty()) {
            Log.d(TAG, "updateSuspendedApps: Force re-suspending ALL ${blockedPackages.size} blocked packages")
            setPackagesSuspended(blockedPackages.toTypedArray(), true)
        } else {
            Log.d(TAG, "updateSuspendedApps: No blocked packages to suspend")
        }
    }

    private fun checkAndBlockForegroundApp() {
        val foregroundPackage = usageStatsHelper.getCurrentForegroundPackage() ?: return

        // Don't block our own app
        if (foregroundPackage == packageName) return

        if (foregroundPackage in blockedPackages) {
            // App is blocked - suspend it and go home
            setPackagesSuspended(arrayOf(foregroundPackage), true)
            goToHome()
            showBlockedToast(foregroundPackage)
        }
    }

    private fun setPackagesSuspended(packages: Array<String>, suspended: Boolean) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "CRITICAL: Cannot suspend packages - app is NOT device owner!")
            Log.e(TAG, "Run: adb shell dpm set-device-owner com.tyler.selfcontrol/.receiver.SelfControlDeviceAdminReceiver")
            // Track these packages for retry when device owner becomes available
            if (suspended) {
                packagesNeedingRetry = packagesNeedingRetry + packages.toSet()
            }
            return
        }

        try {
            // Filter out system-critical packages and our own app
            val filteredPackages = packages.filter { pkg ->
                pkg != packageName && !isSystemCriticalPackage(pkg)
            }.toTypedArray()

            if (filteredPackages.isNotEmpty()) {
                Log.d(TAG, "Calling setPackagesSuspended(suspended=$suspended) for ${filteredPackages.size} packages")
                val failedPackages = devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    filteredPackages,
                    suspended
                )
                if (failedPackages.isNotEmpty()) {
                    Log.w(TAG, "Failed to ${if (suspended) "suspend" else "unsuspend"} these packages: ${failedPackages.toList()}")
                    // Track failed packages for retry (only when suspending)
                    if (suspended) {
                        packagesNeedingRetry = packagesNeedingRetry + failedPackages.toSet()
                    }
                } else {
                    Log.d(TAG, "Successfully ${if (suspended) "suspended" else "unsuspended"} ${filteredPackages.size} packages")
                    // Clear successfully suspended packages from retry set
                    if (suspended) {
                        packagesNeedingRetry = packagesNeedingRetry - filteredPackages.toSet()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set packages suspended: $suspended", e)
            // Track all packages for retry on exception
            if (suspended) {
                packagesNeedingRetry = packagesNeedingRetry + packages.toSet()
            }
        }
    }

    /**
     * Retry suspending packages that failed earlier.
     * This handles cases where device owner wasn't set initially or suspension temporarily failed.
     */
    private fun retryFailedSuspensions() {
        // Also check blockedPackages in case some weren't suspended
        val packagesToRetry = (packagesNeedingRetry + blockedPackages).filter { pkg ->
            pkg != packageName && !isSystemCriticalPackage(pkg)
        }.toSet()

        if (packagesToRetry.isEmpty()) return

        if (!isDeviceOwner()) {
            // Still not device owner, keep tracking for later
            return
        }

        Log.d(TAG, "Retrying suspension for ${packagesToRetry.size} packages")

        try {
            val failedPackages = devicePolicyManager.setPackagesSuspended(
                adminComponent,
                packagesToRetry.toTypedArray(),
                true
            )

            if (failedPackages.isEmpty()) {
                Log.d(TAG, "Retry successful: suspended ${packagesToRetry.size} packages")
                packagesNeedingRetry = emptySet()
            } else {
                Log.w(TAG, "Retry partially failed: ${failedPackages.toList()} still unsuspended")
                packagesNeedingRetry = failedPackages.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Retry suspension failed with exception", e)
        }
    }

    private fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(packageName)
    }

    private fun isSystemCriticalPackage(packageName: String): Boolean {
        // Packages that should never be suspended regardless of other rules
        val criticalPackages = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.android.providers.contacts",
            "com.android.providers.telephony",
            "com.android.emergency"
        )
        return packageName in criticalPackages || packageName.startsWith("com.android.providers.")
    }

    private fun goToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun showBlockedToast(packageName: String) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            packageName
        }

        Toast.makeText(
            this,
            "$appName is blocked",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Blocking Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when app blocking is active"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Self Control Active")
            .setContentText("App blocking is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "AppBlockingService"
        private const val CHANNEL_ID = "app_blocking_channel"
        private const val NOTIFICATION_ID = 1
        private const val CHECK_INTERVAL_MS = 1000L // Check every second
        private const val RETRY_INTERVAL_MS = 5000L // Retry failed suspensions every 5 seconds

        const val ACTION_START_MONITORING = "com.tyler.selfcontrol.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.tyler.selfcontrol.STOP_MONITORING"
        const val ACTION_UPDATE_BLOCKS = "com.tyler.selfcontrol.UPDATE_BLOCKS"

        fun start(context: Context) {
            val intent = Intent(context, AppBlockingService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockingService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun updateBlocks(context: Context) {
            Log.d(TAG, "updateBlocks() called - sending ACTION_UPDATE_BLOCKS")
            val intent = Intent(context, AppBlockingService::class.java).apply {
                action = ACTION_UPDATE_BLOCKS
            }
            context.startService(intent)
        }
    }
}
