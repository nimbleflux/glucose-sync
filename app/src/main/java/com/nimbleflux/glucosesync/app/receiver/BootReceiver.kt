package com.nimbleflux.glucosesync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nimbleflux.glucosesync.app.service.GlucosePollingService
import com.nimbleflux.glucosesync.app.service.PollingWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PollingWorker.schedule(context)
            val serviceIntent = Intent(context, GlucosePollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
