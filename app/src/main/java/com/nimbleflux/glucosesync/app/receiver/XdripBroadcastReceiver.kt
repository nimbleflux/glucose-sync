package com.nimbleflux.glucosesync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nimbleflux.glucosesync.app.BuildConfig
import com.nimbleflux.glucosesync.app.service.GlucosePollingService
import com.nimbleflux.glucosesync.shared.provider.xdrip.XdripIntentParser
import com.nimbleflux.glucosesync.shared.provider.xdrip.XdripLatestReadingStore
import com.nimbleflux.glucosesync.shared.provider.xdrip.XdripParseResult

/**
 * Manifest-declared receiver for xDrip+/Diabox glucose broadcasts — the
 * durability backstop for the runtime receiver in
 * [com.nimbleflux.glucosesync.shared.provider.xdrip.XdripBroadcastProvider].
 *
 * Why this exists: the runtime receiver only captures broadcasts while the
 * GlucoseSync process is alive AND [XdripBroadcastProvider.enableBroadcasts]
 * has been called. When the process is killed (Doze, app update, OEM battery
 * killer, low memory) broadcasts are silently dropped because xDrip+ intents
 * are not sticky. This manifest receiver is registered at install time and
 * captures the broadcast even while the rest of the app is idle, then:
 *
 *  1. Persists the reading to [XdripLatestReadingStore] so the next
 *     [XdripBroadcastProvider.restoreSession] can seed the dashboard
 *     immediately after a cold start — even if the xDrip Web Service is off.
 *  2. Ensures [GlucosePollingService] is running so the FGS picks the reading
 *     up and pushes it to alerts/the watch. The FGS self-terminates if no
 *     provider is selected, so starting it here is a no-op when the user
 *     isn't using xDrip+.
 *
 * This does NOT replace the runtime receiver, which remains the live path
 * while the app/service is in the foreground (it's faster and feeds the
 * in-memory history buffer for charting). Parsing is shared via
 * [XdripIntentParser] so both paths stay identical.
 *
 * Registered in AndroidManifest.xml with `android:exported="true"` and gated
 * by `android:permission="com.eveningoutpost.dexdrip.permissions.RECEIVE_BG_ESTIMATE"`,
 * mirroring how xDrip+ sends (sendBroadcast with a receiver permission).
 */
class XdripBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Parsing is cheap and allocation-free; safe on the main thread.
        val reading = when (val result = XdripIntentParser.parse(intent)) {
            is XdripParseResult.NoGlucose -> {
                if (BuildConfig.DEBUG) {
                    Log.w(
                        TAG,
                        "Rejected broadcast: action=${intent.action}, " +
                            "extras=${intent.extras?.keySet()?.joinToString()}"
                    )
                }
                return
            }
            is XdripParseResult.Accepted -> result.reading
        }

        // Persist synchronously so the value survives even if the process is
        // reaped immediately after onReceive returns. SharedPreferences.apply()
        // is asynchronous to disk but holds the value in memory, and
        // commit() would block the main thread — apply() is the right call
        // here (a crash after apply() but before fsync still keeps the last
        // committed value, and a rare loss of the very last reading is
        // acceptable given the next broadcast is ~5 min away).
        runCatching { XdripLatestReadingStore(context).save(reading) }

        // Hand off the FGS start to a background thread via goAsync() so we
        // don't block the main thread or risk an ANR. startForegroundService
        // is allowed from a receiver (it's the documented pattern for
        // converting a broadcast into background work).
        val pending = goAsync()
        Thread {
            try {
                val serviceIntent = Intent(context, GlucosePollingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Could not start polling service: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "XdripManifestReceiver"
    }
}
