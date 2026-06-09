package com.nimbleflux.medtrumwatch.wear.repository

import android.content.Context
import android.content.SharedPreferences
import com.nimbleflux.medtrumwatch.shared.domain.DemoData
import com.nimbleflux.medtrumwatch.shared.domain.GlucoseHistoryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class WatchGlucoseState(
    val glucose: Double = 0.0,
    val unit: String = "mmol/L",
    val trend: String = "",
    val timestamp: Long = 0L,
    val isDemo: Boolean = false,
    val history: List<Pair<Long, Double>> = emptyList()
) {
    val isStale: Boolean
        get() = if (timestamp == 0L) true else System.currentTimeMillis() / 1000 - timestamp > 600

    val highThreshold: Double get() = if (unit == "mg/dL") 180.0 else 10.0
    val lowThreshold: Double get() = if (unit == "mg/dL") 70.0 else 3.9
}

class GlucoseRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("glucose_data", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: StateFlow<WatchGlucoseState> = _state

    private var demoJob: kotlinx.coroutines.Job? = null

    init {
        val saved = loadFromPrefs()
        if (saved.glucose > 0.0 && !saved.isStale) {
            _state.value = saved
        } else if (saved.glucose <= 0.0) {
            startDemoMode()
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
            .putBoolean(KEY_DEMO, false)
            .apply()
        val history = _state.value.history.toMutableList()
        history.add(timestamp to glucose)
        _state.value = WatchGlucoseState(glucose, unit, trend, timestamp, false, history.trimTo2h())
    }

    fun startDemoMode() {
        demoJob?.cancel()
        val demoHistory = generateDemoHistory()
        _state.value = WatchGlucoseState(
            glucose = demoHistory.last().second,
            unit = "mmol/L",
            trend = "\u2192",
            timestamp = demoHistory.last().first,
            isDemo = true,
            history = demoHistory
        )
        demoJob = scope.launch {
            while (true) {
                val snapshot = DemoData.snapshot(emptyList())
                val history = _state.value.history.toMutableList()
                history.add(snapshot.timestamp to (snapshot.glucose ?: 5.6))
                _state.value = WatchGlucoseState(
                    glucose = snapshot.glucose ?: 5.6,
                    unit = "mmol/L",
                    trend = snapshot.trend.symbol,
                    timestamp = snapshot.timestamp,
                    isDemo = true,
                    history = history.trimTo2h()
                )
                delay(30_000)
            }
        }
    }

    private fun generateDemoHistory(): List<Pair<Long, Double>> {
        val now = System.currentTimeMillis() / 1000
        val points = mutableListOf<Pair<Long, Double>>()
        var value = 5.5
        for (i in 0 until 8) {
            value = (value + Random.nextDouble(-0.4, 0.4)).coerceIn(3.2, 13.0)
            points.add((now - (8 - i) * 900) to value)
        }
        return points
    }

    fun stopDemoMode() { demoJob?.cancel() }

    fun getGlucose(): Float = _state.value.glucose.toFloat()
    fun getTimestamp(): Long = _state.value.timestamp
    fun getTrend(): String = _state.value.trend
    fun getUnit(): String = _state.value.unit
    fun isStale(): Boolean = _state.value.isStale

    private fun loadFromPrefs(): WatchGlucoseState {
        return WatchGlucoseState(
            glucose = prefs.getFloat(KEY_GLUCOSE, 0f).toDouble(),
            unit = prefs.getString(KEY_UNIT, "mmol/L") ?: "mmol/L",
            trend = prefs.getString(KEY_TREND, "") ?: "",
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0),
            isDemo = prefs.getBoolean(KEY_DEMO, false)
        )
    }

    companion object {
        private const val KEY_GLUCOSE = "glucose"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_TREND = "trend"
        private const val KEY_UNIT = "unit"
        private const val KEY_DEMO = "is_demo"
    }
}
