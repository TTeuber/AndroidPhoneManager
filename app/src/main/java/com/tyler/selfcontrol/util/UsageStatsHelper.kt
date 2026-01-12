package com.tyler.selfcontrol.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager: UsageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getCurrentForegroundPackage(): String? {
        if (!hasUsageStatsPermission()) {
            return null
        }

        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 10_000 // Look at last 10 seconds

        val usageEvents = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            beginTime,
            endTime
        )

        if (usageEvents.isNullOrEmpty()) {
            return null
        }

        // Find the most recently used app
        var mostRecentPackage: String? = null
        var mostRecentTime = 0L

        for (usageStats in usageEvents) {
            if (usageStats.lastTimeUsed > mostRecentTime) {
                mostRecentTime = usageStats.lastTimeUsed
                mostRecentPackage = usageStats.packageName
            }
        }

        return mostRecentPackage
    }
}
