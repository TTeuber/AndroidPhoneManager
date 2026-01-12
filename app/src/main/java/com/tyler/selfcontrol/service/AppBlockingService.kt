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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tyler.selfcontrol.MainActivity
import com.tyler.selfcontrol.R
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
    lateinit var blockRepository: BlockRepository

    @Inject
    lateinit var usageStatsHelper: UsageStatsHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringJob: Job? = null

    private var blockedPackages = setOf<String>()
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
        observeBlockedPackages()
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

    private fun observeBlockedPackages() {
        serviceScope.launch {
            blockRepository.getBlockedPackageNames().collectLatest { packages ->
                val newBlockedPackages = packages.toSet()
                val previouslyBlocked = blockedPackages

                blockedPackages = newBlockedPackages

                // Unsuspend apps that are no longer blocked
                val toUnsuspend = previouslyBlocked - newBlockedPackages
                if (toUnsuspend.isNotEmpty()) {
                    setPackagesSuspended(toUnsuspend.toTypedArray(), false)
                }

                // Suspend newly blocked apps
                val toSuspend = newBlockedPackages - previouslyBlocked
                if (toSuspend.isNotEmpty()) {
                    setPackagesSuspended(toSuspend.toTypedArray(), true)
                }
            }
        }
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
        if (blockedPackages.isNotEmpty()) {
            setPackagesSuspended(blockedPackages.toTypedArray(), true)
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
        if (!isDeviceOwner()) return

        try {
            // Filter out system-critical packages and our own app
            val filteredPackages = packages.filter { pkg ->
                pkg != packageName && !isSystemCriticalPackage(pkg)
            }.toTypedArray()

            if (filteredPackages.isNotEmpty()) {
                devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    filteredPackages,
                    suspended
                )
            }
        } catch (e: Exception) {
            // Log but don't crash - some packages may not be suspendable
        }
    }

    private fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(packageName)
    }

    private fun isSystemCriticalPackage(packageName: String): Boolean {
        // Packages that should never be suspended
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
