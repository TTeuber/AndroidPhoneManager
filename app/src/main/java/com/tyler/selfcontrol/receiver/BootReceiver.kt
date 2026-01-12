package com.tyler.selfcontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tyler.selfcontrol.service.AppBlockingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start the blocking service on boot
            AppBlockingService.start(context)
        }
    }
}
