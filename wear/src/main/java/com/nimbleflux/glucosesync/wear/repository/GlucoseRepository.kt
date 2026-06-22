package com.nimbleflux.glucosesync.wear.repository

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.nimbleflux.glucosesync.wear.complication.BatteryComplicationProviderService
import com.nimbleflux.glucosesync.wear.complication.DeltaComplicationProviderService
import com.nimbleflux.glucosesync.wear.complication.GlucoseComplicationProviderService
import com.nimbleflux.glucosesync.wear.complication.GlucoseDeltaComplicationProviderService
import com.nimbleflux.glucosesync.wear.complication.IobComplicationProviderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WatchGlucoseState(
    val glucose: Double = 0.0,
    val unit: String = "mmol/L",
    val trend: String = "",
    val timestamp: Long = 0L,
    val history: List<Pair<Long, Double>> = emptyList(),
    val iob: Double? = null,
    val delta: Double? = null,
    val batteryPercent: Double? = null,
    val basalRate: Double? = null,
    val lastBolus: Double? = null,
    val lastBolusTime: Long? = null,
    val remainingDose: Double? = null,
    val highThreshold: Double = 10.0,
    val lowThreshold: Double = 3.9,
    val timeInRange: Double? = null,
    val averageGlucose: Double? = null
) {
    val isStale: Boolean
        get() = if (timestamp == 0L) true else System.currentTimeMillis() / 1000 - timestamp > 600
}

class GlucoseRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("glucose_data", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: StateFlow<WatchGlucoseState> = _state

    @Volatile
    private var saving = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!saving) {
            val saved = loadFromPrefs()
            if (saved.glucose > 0.0) {
                _state.value = saved
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val saved = loadFromPrefs()
        if (saved.glucose > 0.0 && !saved.isStale) {
            _state.value = saved
        }
    }

    private fun List<Pair<Long, Double>>.trimTo2h(): List<Pair<Long, Double>> {
        val cutoff = System.currentTimeMillis() / 1000 - 7200
        return this.filter { it.first >= cutoff }
    }

    fun saveGlucose(
        glucose: Double,
        timestamp: Long,
        trend: String,
        unit: String,
        iob: Double? = null,
        delta: Double? = null,
        batteryPercent: Double? = null,
        basalRate: Double? = null,
        lastBolus: Double? = null,
        lastBolusTime: Long? = null,
        remainingDose: Double? = null,
        highThreshold: Double? = null,
        lowThreshold: Double? = null,
        timeInRange: Double? = null,
        averageGlucose: Double? = null,
        history: List<Pair<Long, Double>>? = null
    ) {
        saving = true
        prefs.edit()
            .putFloat(KEY_GLUCOSE, glucose.toFloat())
            .putLong(KEY_TIMESTAMP, timestamp)
            .putString(KEY_TREND, trend)
            .putString(KEY_UNIT, unit)
            .apply { iob?.let { putFloat(KEY_IOB, it.toFloat()) } }
            .apply { delta?.let { putFloat(KEY_DELTA, it.toFloat()) } }
            .apply { batteryPercent?.let { putFloat(KEY_BATTERY, it.toFloat()) } }
            .apply { basalRate?.let { putFloat(KEY_BASAL_RATE, it.toFloat()) } }
            .apply { lastBolus?.let { putFloat(KEY_LAST_BOLUS, it.toFloat()) } }
            .apply { lastBolusTime?.let { putLong(KEY_LAST_BOLUS_TIME, it) } }
            .apply { remainingDose?.let { putFloat(KEY_REMAINING_DOSE, it.toFloat()) } }
            .apply { highThreshold?.let { putFloat(KEY_HIGH_THRESHOLD, it.toFloat()) } }
            .apply { lowThreshold?.let { putFloat(KEY_LOW_THRESHOLD, it.toFloat()) } }
            .apply { timeInRange?.let { putFloat(KEY_TIME_IN_RANGE, it.toFloat()) } }
            .apply { averageGlucose?.let { putFloat(KEY_AVERAGE_GLUCOSE, it.toFloat()) } }
            .apply()
        val updatedHistory = if (history != null) {
            history.trimTo2h()
        } else {
            val existing = _state.value.history.toMutableList()
            existing.add(timestamp to glucose)
            existing.trimTo2h()
        }
        persistHistory(updatedHistory)
        _state.value = WatchGlucoseState(
            glucose, unit, trend, timestamp, updatedHistory,
            iob, delta, batteryPercent, basalRate, lastBolus, lastBolusTime, remainingDose,
            highThreshold ?: _state.value.highThreshold,
            lowThreshold ?: _state.value.lowThreshold,
            timeInRange, averageGlucose
        )
        saving = false
        requestComplicationUpdate()
        checkThresholdVibration(glucose)
    }

    @Volatile
    private var lastVibrationTime: Long = 0L

    private fun checkThresholdVibration(glucose: Double) {
        if (glucose <= 0.0) return
        val state = _state.value
        val outOfRange = glucose < state.lowThreshold || glucose > state.highThreshold
        if (!outOfRange) return

        // Throttle: only vibrate once every 5 minutes to avoid buzzing
        // on every reading when the sensor updates frequently.
        val now = System.currentTimeMillis()
        val repeatMs = 5 * 60_000L
        if (now - lastVibrationTime < repeatMs) return
        lastVibrationTime = now

        try {
            val vibrator = appContext.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                as android.os.Vibrator
            // Double-buzz pattern: two short pulses with a gap
            val pattern = longArrayOf(0, 200, 100, 200)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
            // Vibrator not available on this device
        }
    }

    /**
     * Proactively tell the system to re-request all complication data.
     * Without this, complications only update on their fixed schedule
     * (every 5 min via UPDATE_PERIOD_SECONDS). This makes complications
     * update at the same time as the Wear OS app — whenever the phone
     * pushes new data.
     */
    private fun requestComplicationUpdate() {
        val services = listOf(
            GlucoseComplicationProviderService::class.java,
            GlucoseDeltaComplicationProviderService::class.java,
            DeltaComplicationProviderService::class.java,
            IobComplicationProviderService::class.java,
            BatteryComplicationProviderService::class.java
        )
        services.forEach { cls ->
            try {
                ComplicationDataSourceUpdateRequester
                    .create(appContext, ComponentName(appContext, cls))
                    .requestUpdateAll()
            } catch (_: Exception) {
                // Complication system may not be ready (e.g., during boot).
                // The fixed UPDATE_PERIOD_SECONDS fallback will catch up.
            }
        }
    }

    fun getGlucose(): Float = _state.value.glucose.toFloat()
    fun getTrend(): String = _state.value.trend
    fun getUnit(): String = _state.value.unit
    fun isStale(): Boolean = _state.value.isStale

    private fun persistHistory(history: List<Pair<Long, Double>>) {
        if (history.isEmpty()) return
        val ts = history.joinToString(",") { it.first.toString() }
        val gl = history.joinToString(",") { String.format("%.2f", it.second) }
        prefs.edit()
            .putString(KEY_HISTORY_TS, ts)
            .putString(KEY_HISTORY_GL, gl)
            .apply()
    }

    private fun loadHistory(): List<Pair<Long, Double>> {
        val ts = prefs.getString(KEY_HISTORY_TS, null) ?: return emptyList()
        val gl = prefs.getString(KEY_HISTORY_GL, null) ?: return emptyList()
        val timestamps = ts.split(",").mapNotNull { it.toLongOrNull() }
        val glucoses = gl.split(",").mapNotNull { it.toDoubleOrNull() }
        if (timestamps.size != glucoses.size) return emptyList()
        val cutoff = System.currentTimeMillis() / 1000 - 7200
        return timestamps.zip(glucoses).filter { it.first >= cutoff }
    }

    fun injectDemoData() {
        val now = System.currentTimeMillis() / 1000
        val history = mutableListOf<Pair<Long, Double>>()
        val pattern = listOf(
            5.2, 5.3, 5.1, 4.9, 4.7, 4.5, 4.4, 4.5, 4.7, 4.9,
            5.1, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.7, 5.6, 5.5,
            5.5, 5.6, 5.6, 5.6
        )
        for (i in pattern.indices) {
            val ts = now - (pattern.size - 1 - i) * 300L
            history.add(ts to pattern[i])
        }

        _state.value = WatchGlucoseState(
            glucose = 5.6,
            unit = "mmol/L",
            trend = "\u2192",
            timestamp = now,
            history = history,
            iob = 1.2,
            delta = 0.3,
            batteryPercent = 0.91,
            basalRate = 0.4,
            lastBolus = 3.0,
            lastBolusTime = now - 3600,
            remainingDose = 122.0,
            highThreshold = 10.0,
            lowThreshold = 3.9,
            timeInRange = 0.85,
            averageGlucose = 5.8
        )
    }

    private fun loadFromPrefs(): WatchGlucoseState {
        return WatchGlucoseState(
            glucose = prefs.getFloat(KEY_GLUCOSE, 0f).toDouble(),
            unit = prefs.getString(KEY_UNIT, "mmol/L") ?: "mmol/L",
            trend = prefs.getString(KEY_TREND, "") ?: "",
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0),
            history = loadHistory(),
            iob = prefs.getFloat(KEY_IOB, -1f).let { if (it < 0) null else it.toDouble() },
            delta = prefs.getFloat(KEY_DELTA, -999f).let { if (it < -998) null else it.toDouble() },
            batteryPercent = prefs.getFloat(KEY_BATTERY, -1f).let { if (it < 0) null else it.toDouble() },
            basalRate = prefs.getFloat(KEY_BASAL_RATE, -1f).let { if (it < 0) null else it.toDouble() },
            lastBolus = prefs.getFloat(KEY_LAST_BOLUS, -1f).let { if (it < 0) null else it.toDouble() },
            lastBolusTime = prefs.getLong(KEY_LAST_BOLUS_TIME, 0).let { if (it == 0L) null else it },
            remainingDose = prefs.getFloat(KEY_REMAINING_DOSE, -1f).let { if (it < 0) null else it.toDouble() },
            highThreshold = prefs.getFloat(KEY_HIGH_THRESHOLD, 10f).toDouble(),
            lowThreshold = prefs.getFloat(KEY_LOW_THRESHOLD, 3.9f).toDouble(),
            timeInRange = prefs.getFloat(KEY_TIME_IN_RANGE, -1f).let { if (it < 0) null else it.toDouble() },
            averageGlucose = prefs.getFloat(KEY_AVERAGE_GLUCOSE, -1f).let { if (it < 0) null else it.toDouble() }
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: GlucoseRepository? = null

        fun getInstance(context: Context): GlucoseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GlucoseRepository(context).also { INSTANCE = it }
            }
        }

        private const val KEY_GLUCOSE = "glucose"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_TREND = "trend"
        private const val KEY_UNIT = "unit"
        private const val KEY_IOB = "iob"
        private const val KEY_DELTA = "delta"
        private const val KEY_BATTERY = "battery"
        private const val KEY_BASAL_RATE = "basal_rate"
        private const val KEY_LAST_BOLUS = "last_bolus"
        private const val KEY_LAST_BOLUS_TIME = "last_bolus_time"
        private const val KEY_REMAINING_DOSE = "remaining_dose"
        private const val KEY_HIGH_THRESHOLD = "high_threshold"
        private const val KEY_LOW_THRESHOLD = "low_threshold"
        private const val KEY_TIME_IN_RANGE = "time_in_range"
        private const val KEY_AVERAGE_GLUCOSE = "average_glucose"
        private const val KEY_HISTORY_TS = "history_ts"
        private const val KEY_HISTORY_GL = "history_gl"
    }
}
