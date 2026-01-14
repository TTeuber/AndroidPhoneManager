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
import kotlinx.coroutines.flow.collectLatest
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
    private var allowedPackages = setOf<String>()
    private var blockRulePackages = setOf<String>() // Packages from user-defined blocks
    private var blacklistedPackages = setOf<String>() // Packages on the blacklist
    private var isMonitoring = false

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
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        observeAllowedPackages()
        observeBlockRulePackages()
        observeBlacklistedPackages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_BLOCKS -> updateSuspendedApps()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Observe the allowlist and recalculate blocked packages when it changes.
     */
    private fun observeAllowedPackages() {
        serviceScope.launch {
            appInstallationRepository.getAllowedPackageNamesFlow().collectLatest { allowed ->
                allowedPackages = allowed
                Log.d(TAG, "Allowlist updated: ${allowed.size} packages")
                recalculateBlockedPackages()
            }
        }
    }

    /**
     * Observe the block rules (user-defined blocks with app rules) and recalculate when they change.
     */
    private fun observeBlockRulePackages() {
        serviceScope.launch {
            blockRepository.getBlockedPackageNames().collectLatest { blocked ->
                blockRulePackages = blocked.toSet()
                Log.d(TAG, "Block rules updated: ${blocked.size} packages: $blocked")
                recalculateBlockedPackages()
            }
        }
    }

    /**
     * Observe the blacklist and recalculate when it changes.
     * Blacklisted apps should ALWAYS be blocked, even if they're system apps.
     */
    private fun observeBlacklistedPackages() {
        serviceScope.launch {
            appInstallationRepository.getBlacklistedApps().collectLatest { apps ->
                blacklistedPackages = apps.map { it.packageName }.toSet()
                Log.d(TAG, "Blacklist updated: ${blacklistedPackages.size} packages")
                recalculateBlockedPackages()
            }
        }
    }

    /**
     * Recalculate which packages should be blocked based on:
     * 1. Packages explicitly blocked by user-defined blocks (blockRulePackages)
     * 2. Packages on the blacklist (blacklistedPackages) - blocked regardless of system status
     * 3. Packages not on the allowlist (and not system packages)
     *
     * The final set is the UNION of all sources.
     */
    private fun recalculateBlockedPackages() {
        val allInstalledPackages = getInstalledPackages()

        // Source 1: Packages from user-defined blocks (original Phase 3-5 functionality)
        // These override system package status - if user explicitly blocks it, block it
        val fromBlockRules = blockRulePackages.filter { pkg ->
            pkg != packageName
        }.toSet()

        // Source 2: Packages on the blacklist - ALWAYS blocked regardless of system status
        val fromBlacklist = blacklistedPackages.filter { pkg ->
            pkg != packageName && allInstalledPackages.contains(pkg)
        }.toSet()

        // Source 3: Packages not on allowlist (Phase 7 allowlist-based blocking)
        // Only applies to non-system packages
        val notOnAllowlist = allInstalledPackages.filter { pkg ->
            !isSystemPackage(pkg) &&
            !allowedPackages.contains(pkg) &&
            pkg != packageName
        }.toSet()

        // Combine all sources
        val newBlockedPackages = fromBlockRules + fromBlacklist + notOnAllowlist

        val previouslyBlocked = blockedPackages
        blockedPackages = newBlockedPackages

        Log.d(TAG, "Recalculating blocks - Block rules: ${fromBlockRules.size}, Blacklist: ${fromBlacklist.size}, Not on allowlist: ${notOnAllowlist.size}, Total: ${newBlockedPackages.size}")

        // Unsuspend apps that are no longer blocked
        val toUnsuspend = previouslyBlocked - newBlockedPackages
        if (toUnsuspend.isNotEmpty()) {
            Log.d(TAG, "Unsuspending ${toUnsuspend.size} apps: ${toUnsuspend.take(5)}")
            setPackagesSuspended(toUnsuspend.toTypedArray(), false)
        }

        // Suspend newly blocked apps
        val toSuspend = newBlockedPackages - previouslyBlocked
        if (toSuspend.isNotEmpty()) {
            Log.d(TAG, "Suspending ${toSuspend.size} apps: ${toSuspend.take(5)}")
            setPackagesSuspended(toSuspend.toTypedArray(), true)
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
        // Recalculate and apply blocks
        recalculateBlockedPackages()
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
            Log.w(TAG, "Cannot suspend packages: not device owner")
            return
        }

        try {
            // Filter out system-critical packages and our own app
            val filteredPackages = packages.filter { pkg ->
                pkg != packageName && !isSystemCriticalPackage(pkg)
            }.toTypedArray()

            if (filteredPackages.isNotEmpty()) {
                Log.d(TAG, "Calling setPackagesSuspended(suspended=$suspended) for: ${filteredPackages.toList()}")
                val failedPackages = devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    filteredPackages,
                    suspended
                )
                if (failedPackages.isNotEmpty()) {
                    Log.w(TAG, "Failed to suspend these packages: ${failedPackages.toList()}")
                } else {
                    Log.d(TAG, "Successfully ${if (suspended) "suspended" else "unsuspended"} ${filteredPackages.size} packages")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set packages suspended: $suspended", e)
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
            val intent = Intent(context, AppBlockingService::class.java).apply {
                action = ACTION_UPDATE_BLOCKS
            }
            context.startService(intent)
        }
    }
}
