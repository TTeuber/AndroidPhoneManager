package com.tyler.selfcontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    @Inject
    lateinit var appInstallationManager: AppInstallationManager

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                handlePackageInstalled(context, packageName)
            }
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                handlePackageRemoved(context, packageName)
            }
        }
    }

    private fun handlePackageInstalled(context: Context, packageName: String) {
        // Re-suspend Play Store immediately after any package installation
        // This ensures the Play Store is locked down again
        appInstallationManager.resuspendPlayStoreNow()

        // Trigger the blocking service to re-evaluate which packages to block
        // This will block the newly installed app if it's not on the allowlist
        AppBlockingService.updateBlocks(context)
    }

    private fun handlePackageRemoved(context: Context, packageName: String) {
        // Currently we don't remove apps from the allowlist when uninstalled
        // This allows the user to reinstall without going through cooldown again
    }
}
