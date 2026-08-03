package com.nimbleflux.glucosesync.shared.provider.xdrip

import android.content.Context
import com.nimbleflux.glucosesync.shared.domain.TrendArrow

/**
 * Persists the most recent xDrip+ reading to SharedPreferences so it survives
 * process death.
 *
 * The runtime receiver in [XdripBroadcastProvider] lives only while the
 * GlucoseSync process is alive AND [XdripBroadcastProvider.enableBroadcasts]
 * has been called. When the process is killed (Doze, app update, OEM battery
 * killer, low memory), in-memory state is lost and any broadcasts emitted in
 * the gap are dropped. This store is written by the manifest-declared
 * [com.nimbleflux.glucosesync.app.receiver.XdripBroadcastReceiver] on every
 * broadcast it captures (even while the rest of the app is idle), giving the
 * provider a durable last-known reading to seed from on the next
 * [XdripBroadcastProvider.restoreSession].
 *
 * Deliberately tiny: only the fields needed to render the dashboard's current
 * value. Full 24h history still comes from the Web Service backfill or live
 * broadcast accumulation.
 *
 * Not encrypted: glucose readings are not PII and this file is private-mode
 * (not exported). Keeping it plain avoids the EncryptedSharedPreferences
 * startup cost on every broadcast.
 */
class XdripLatestReadingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(reading: XdripReading) {
        // Only overwrite if this reading is newer than what's stored. The
        // manifest receiver and the runtime receiver both write here; under
        // delivery reordering (rare, but possible across process restarts)
        // we want the genuinely newest reading to win.
        val existingMs = prefs.getLong(KEY_TIMESTAMP_MS, -1L)
        if (reading.timestampMs < existingMs) return
        prefs.edit()
            .putLong(KEY_TIMESTAMP_MS, reading.timestampMs)
            .putFloat(KEY_GLUCOSE_MGDL, reading.glucoseMgDl.toFloat())
            .putFloat(KEY_DELTA_MGDL, reading.deltaMgDl?.toFloat() ?: Float.NaN)
            .putString(KEY_TREND, reading.trend.name)
            .putFloat(KEY_BATTERY, reading.batteryPercent?.toFloat() ?: Float.NaN)
            .apply()
    }

    fun load(): XdripReading? {
        val ts = prefs.getLong(KEY_TIMESTAMP_MS, -1L)
        if (ts <= 0L) return null
        val glucose = prefs.getFloat(KEY_GLUCOSE_MGDL, -1f)
        if (glucose <= 0f) return null
        val deltaRaw = prefs.getFloat(KEY_DELTA_MGDL, Float.NaN)
        val delta = if (!deltaRaw.isNaN()) deltaRaw.toDouble() else null
        val trendName = prefs.getString(KEY_TREND, null)
        val trend = runCatching { TrendArrow.valueOf(trendName ?: "") }
            .getOrDefault(TrendArrow.UNKNOWN)
        val batteryRaw = prefs.getFloat(KEY_BATTERY, Float.NaN)
        val battery = if (!batteryRaw.isNaN()) batteryRaw.toDouble() else null
        return XdripReading(
            glucoseMgDl = glucose.toDouble(),
            timestampMs = ts,
            deltaMgDl = delta,
            trend = trend,
            batteryPercent = battery
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "xdrip_latest_reading"
        private const val KEY_TIMESTAMP_MS = "timestamp_ms"
        private const val KEY_GLUCOSE_MGDL = "glucose_mgdl"
        private const val KEY_DELTA_MGDL = "delta_mgdl"
        private const val KEY_TREND = "trend"
        private const val KEY_BATTERY = "battery"
    }
}
