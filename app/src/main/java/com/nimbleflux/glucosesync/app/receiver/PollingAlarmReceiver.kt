package com.nimbleflux.glucosesync.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.nimbleflux.glucosesync.app.BuildConfig
import com.nimbleflux.glucosesync.app.service.GlucosePollingService

/**
 * Doze-proof keepalive for [GlucosePollingService].
 *
 * The FGS runs a coroutine loop with `delay(5 min)` between polls. Under
 * Doze the OS may kill the FGS process entirely, silently breaking
 * alerts until the next reboot or manual app launch. This receiver is
 * scheduled via [AlarmManager.setExactAndAllowWhileIdle] which is one
 * of the few APIs allowed to fire under Doze. When it fires, it starts
 * the FGS again - cheap if the service is already running (just an
 * extra onStartCommand delivery we ignore), life-saving if the process
 * had been killed.
 */
class PollingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Alarm fired - ensuring polling service is alive")
        val serviceIntent = Intent(context, GlucosePollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "PollingAlarm"
        private const val REQUEST_CODE = 4242

        fun scheduleNext(context: Context, intervalMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, PollingAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAt = SystemClock.elapsedRealtime() + intervalMillis
            // setExactAndAllowWhileIdle fires even under Doze. It can be delayed
            // by up to a few minutes under aggressive Doze, but it always fires
            // eventually - far better than delay() which silently suspends.
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                triggerAt,
                pendingIntent
            )
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, PollingAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
