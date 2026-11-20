package com.nimbleflux.glucosesync.shared.wear

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint

data class WatchPayload(
    val glucose: Double,
    val timestamp: Long,
    val trend: String,
    val unit: String,
    val iob: Double? = null,
    val delta: Double? = null,
    val batteryPercent: Double? = null,
    val basalRate: Double? = null,
    val lastBolus: Double? = null,
    val lastBolusTime: Long? = null,
    val remainingDose: Double? = null,
    val highThreshold: Double? = null,
    val lowThreshold: Double? = null,
    /** Master alert toggle from the phone's settings. The watch fires its
     *  own threshold alerts and must honour this flag. Null when the phone
     *  build predates the flag — the watch keeps alerting (old behaviour). */
    val alertsEnabled: Boolean? = null,
    /** Repeat window for threshold alerts in minutes, mirroring the phone's
     *  "alert repeat" setting. Null = watch default (5 min). */
    val alertRepeatMinutes: Int? = null,
    /** Fraction of readings in range, 0..1 — not a percentage. */
    val timeInRange: Double? = null,
    val averageGlucose: Double? = null,
    val history: List<GlucoseHistoryPoint> = emptyList()
)

object WatchPayloadCodec {

    const val PATH = "/glucose"

    /**
     * How much history the phone pushes with every sync. 24h so the watch
     * can render its graph and compute windowed stats (TIR/average) over
     * the windows the user picks in the watch app — the watch trims this
     * down to whatever it actually displays. ~288 points at 5-min sampling
     * (~3.5 KB), ~1.4k points at 1-min (~20 KB), both well under the Wear
     * Data API per-item limit.
     */
    const val MAX_HISTORY_AGE_SEC = 86_400L

    private const val KEY_GLUCOSE = "glucose"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_TREND = "trend"
    private const val KEY_UNIT = "unit"
    private const val KEY_IOB = "iob"
    private const val KEY_DELTA = "delta"
    private const val KEY_BATTERY = "batteryPercent"
    private const val KEY_BASAL_RATE = "basalRate"
    private const val KEY_LAST_BOLUS = "lastBolus"
    private const val KEY_LAST_BOLUS_TIME = "lastBolusTime"
    private const val KEY_REMAINING_DOSE = "remainingDose"
    private const val KEY_HIGH_THRESHOLD = "highThreshold"
    private const val KEY_LOW_THRESHOLD = "lowThreshold"
    private const val KEY_ALERTS_ENABLED = "alertsEnabled"
    private const val KEY_ALERT_REPEAT_MINUTES = "alertRepeatMinutes"
    private const val KEY_TIME_IN_RANGE = "timeInRange"
    private const val KEY_AVERAGE_GLUCOSE = "averageGlucose"
    private const val KEY_HISTORY_TS = "history_ts"
    private const val KEY_HISTORY_GL = "history_gl"

    fun toPutDataRequest(payload: WatchPayload): PutDataRequest {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putAll(toDataMap(payload))
        }
        return request.asPutDataRequest().setUrgent()
    }

    fun toDataMap(payload: WatchPayload): DataMap {
        val dataMap = DataMap()
        dataMap.putDouble(KEY_GLUCOSE, payload.glucose)
        dataMap.putLong(KEY_TIMESTAMP, payload.timestamp)
        dataMap.putString(KEY_TREND, payload.trend)
        dataMap.putString(KEY_UNIT, payload.unit)
        payload.iob?.let { dataMap.putDouble(KEY_IOB, it) }
        payload.delta?.let { dataMap.putDouble(KEY_DELTA, it) }
        payload.batteryPercent?.let { dataMap.putDouble(KEY_BATTERY, it) }
        payload.basalRate?.let { dataMap.putDouble(KEY_BASAL_RATE, it) }
        payload.lastBolus?.let { dataMap.putDouble(KEY_LAST_BOLUS, it) }
        payload.lastBolusTime?.let { dataMap.putLong(KEY_LAST_BOLUS_TIME, it) }
        payload.remainingDose?.let { dataMap.putDouble(KEY_REMAINING_DOSE, it) }
        payload.highThreshold?.let { dataMap.putDouble(KEY_HIGH_THRESHOLD, it) }
        payload.lowThreshold?.let { dataMap.putDouble(KEY_LOW_THRESHOLD, it) }
        payload.alertsEnabled?.let { dataMap.putBoolean(KEY_ALERTS_ENABLED, it) }
        payload.alertRepeatMinutes?.let { dataMap.putInt(KEY_ALERT_REPEAT_MINUTES, it) }
        payload.timeInRange?.let { dataMap.putDouble(KEY_TIME_IN_RANGE, it) }
        payload.averageGlucose?.let { dataMap.putDouble(KEY_AVERAGE_GLUCOSE, it) }
        if (payload.history.isNotEmpty()) {
            dataMap.putLongArray(KEY_HISTORY_TS, payload.history.map { it.timestamp }.toLongArray())
            dataMap.putFloatArray(KEY_HISTORY_GL, payload.history.map { it.glucoseMmol.toFloat() }.toFloatArray())
        }
        return dataMap
    }

    fun fromDataMap(dataMap: DataMap): WatchPayload? {
        if (!dataMap.containsKey(KEY_GLUCOSE)) return null
        val historyTs = dataMap.getLongArray(KEY_HISTORY_TS)
        val historyGl = dataMap.getFloatArray(KEY_HISTORY_GL)
        val history: List<GlucoseHistoryPoint> = if (historyTs != null && historyGl != null && historyTs.size == historyGl.size) {
            historyTs.indices.map { i -> GlucoseHistoryPoint(historyTs[i], historyGl[i].toDouble()) }
        } else {
            emptyList()
        }
        return WatchPayload(
            glucose = dataMap.getDouble(KEY_GLUCOSE),
            timestamp = dataMap.getLong(KEY_TIMESTAMP),
            trend = dataMap.getString(KEY_TREND, ""),
            unit = dataMap.getString(KEY_UNIT, "mmol/L"),
            iob = dataMap.getDoubleOrNull(KEY_IOB),
            delta = dataMap.getDoubleOrNull(KEY_DELTA),
            batteryPercent = dataMap.getDoubleOrNull(KEY_BATTERY),
            basalRate = dataMap.getDoubleOrNull(KEY_BASAL_RATE),
            lastBolus = dataMap.getDoubleOrNull(KEY_LAST_BOLUS),
            lastBolusTime = dataMap.getLongOrNull(KEY_LAST_BOLUS_TIME),
            remainingDose = dataMap.getDoubleOrNull(KEY_REMAINING_DOSE),
            highThreshold = dataMap.getDoubleOrNull(KEY_HIGH_THRESHOLD),
            lowThreshold = dataMap.getDoubleOrNull(KEY_LOW_THRESHOLD),
            alertsEnabled = dataMap.getBooleanOrNull(KEY_ALERTS_ENABLED),
            alertRepeatMinutes = dataMap.getIntOrNull(KEY_ALERT_REPEAT_MINUTES),
            timeInRange = dataMap.getDoubleOrNull(KEY_TIME_IN_RANGE),
            averageGlucose = dataMap.getDoubleOrNull(KEY_AVERAGE_GLUCOSE),
            history = history
        )
    }

    private fun DataMap.getDoubleOrNull(key: String): Double? =
        if (containsKey(key)) getDouble(key) else null

    private fun DataMap.getLongOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun DataMap.getBooleanOrNull(key: String): Boolean? =
        if (containsKey(key)) getBoolean(key) else null

    private fun DataMap.getIntOrNull(key: String): Int? =
        if (containsKey(key)) getInt(key) else null
}
