package com.nimbleflux.glucosesync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nimbleflux.glucosesync.app.service.GlucosePollingService
import com.nimbleflux.glucosesync.app.service.PollingWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // BOOT_COMPLETED: device just booted.
        // MY_PACKAGE_REPLACED: app was updated via Play Store / ADB / side-load.
        //   The OS kills the foreground service during an update and doesn't
        //   automatically restart it; without this branch the FGS would stay
        //   dead until the next reboot or manual app launch.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
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
}
