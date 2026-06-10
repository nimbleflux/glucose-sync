package com.nimbleflux.glucosesync.wear.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WatchGlucoseState(
    val glucose: Double = 0.0,
    val unit: String = "mmol/L",
    val trend: String = "",
    val timestamp: Long = 0L,
    val history: List<Pair<Long, Double>> = emptyList()
) {
    val isStale: Boolean
        get() = if (timestamp == 0L) true else System.currentTimeMillis() / 1000 - timestamp > 600

    val highThreshold: Double get() = if (unit == "mg/dL") 180.0 else 10.0
    val lowThreshold: Double get() = if (unit == "mg/dL") 70.0 else 3.9
}

class GlucoseRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("glucose_data", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: StateFlow<WatchGlucoseState> = _state

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val saved = loadFromPrefs()
        if (saved.glucose > 0.0) {
            _state.value = saved
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

    fun saveGlucose(glucose: Double, timestamp: Long, trend: String, unit: String) {
        prefs.edit()
            .putFloat(KEY_GLUCOSE, glucose.toFloat())
            .putLong(KEY_TIMESTAMP, timestamp)
            .putString(KEY_TREND, trend)
            .putString(KEY_UNIT, unit)
            .apply()
        val history = _state.value.history.toMutableList()
        history.add(timestamp to glucose)
        _state.value = WatchGlucoseState(glucose, unit, trend, timestamp, history.trimTo2h())
    }

    fun getGlucose(): Float = _state.value.glucose.toFloat()
    fun getTrend(): String = _state.value.trend
    fun getUnit(): String = _state.value.unit
    fun isStale(): Boolean = _state.value.isStale

    private fun loadFromPrefs(): WatchGlucoseState {
        return WatchGlucoseState(
            glucose = prefs.getFloat(KEY_GLUCOSE, 0f).toDouble(),
            unit = prefs.getString(KEY_UNIT, "mmol/L") ?: "mmol/L",
            trend = prefs.getString(KEY_TREND, "") ?: "",
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0)
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
    }
}
