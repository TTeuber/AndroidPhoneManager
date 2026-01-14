package com.tyler.selfcontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tyler.selfcontrol.domain.AppInstallationManager
import com.tyler.selfcontrol.service.AppBlockingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receiver that detects package installations and removals.
 * When a package is installed, it immediately re-suspends the Play Store
 * and cancels any pending resuspension work.
 */
@AndroidEntryPoint
class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }

    @Inject
    lateinit var appInstallationManager: AppInstallationManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}, data=${intent.data}")

        val packageName = intent.data?.schemeSpecificPart
        if (packageName == null) {
            Log.w(TAG, "onReceive: packageName is null, ignoring")
            return
        }

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                Log.d(TAG, "Package installed: $packageName")
                handlePackageInstalled(context, packageName)
            }
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                Log.d(TAG, "Package removed: $packageName")
                handlePackageRemoved(context, packageName)
            }
        }
    }

    private fun handlePackageInstalled(context: Context, packageName: String) {
        Log.d(TAG, "handlePackageInstalled: $packageName")

        // Re-suspend Play Store immediately after any package installation
        // This ensures the Play Store is locked down again
        appInstallationManager.resuspendPlayStoreNow()

        // Trigger the blocking service to re-evaluate which packages to block
        // This will block the newly installed app if it's not on the allowlist
        Log.d(TAG, "Calling AppBlockingService.updateBlocks()")
        AppBlockingService.updateBlocks(context)
    }

    private fun handlePackageRemoved(context: Context, packageName: String) {
        Log.d(TAG, "handlePackageRemoved: $packageName")
        // Currently we don't remove apps from the allowlist when uninstalled
        // This allows the user to reinstall without going through cooldown again
    }
}
