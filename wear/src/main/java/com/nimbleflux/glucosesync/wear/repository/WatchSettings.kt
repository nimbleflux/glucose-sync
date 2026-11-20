package com.nimbleflux.glucosesync.wear.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-local display settings. The phone pushes up to 24h of history on
 * every sync (WatchPayloadCodec.MAX_HISTORY_AGE_SEC); these settings only
 * control what the watch shows and computes from that data — they are
 * never sent back to the phone.
 *
 * Persisted in plain SharedPreferences: nothing here is sensitive, so
 * unlike the phone's SettingsStore there is no need for encrypted storage.
 */
data class WatchDisplaySettings(
    /** Time span rendered by the dashboard sparkline. */
    val graphWindowHours: Int = 2,
    /** Time span the watch computes TIR (and average glucose) over. */
    val tirWindowHours: Int = 24
)

class WatchSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("watch_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<WatchDisplaySettings> = _settings.asStateFlow()

    var graphWindowHours: Int
        get() = _settings.value.graphWindowHours
        set(value) {
            val valid = if (value in GRAPH_WINDOW_OPTIONS) value else DEFAULT_GRAPH_WINDOW_HOURS
            prefs.edit().putInt(KEY_GRAPH_WINDOW, valid).apply()
            _settings.value = _settings.value.copy(graphWindowHours = valid)
        }

    var tirWindowHours: Int
        get() = _settings.value.tirWindowHours
        set(value) {
            val valid = if (value in TIR_WINDOW_OPTIONS) value else DEFAULT_TIR_WINDOW_HOURS
            prefs.edit().putInt(KEY_TIR_WINDOW, valid).apply()
            _settings.value = _settings.value.copy(tirWindowHours = valid)
        }

    private fun load(): WatchDisplaySettings = WatchDisplaySettings(
        graphWindowHours = prefs.getInt(KEY_GRAPH_WINDOW, DEFAULT_GRAPH_WINDOW_HOURS),
        tirWindowHours = prefs.getInt(KEY_TIR_WINDOW, DEFAULT_TIR_WINDOW_HOURS)
    )

    companion object {
        val GRAPH_WINDOW_OPTIONS = listOf(1, 2, 3, 6, 12, 24)
        val TIR_WINDOW_OPTIONS = listOf(3, 6, 12, 24)

        const val DEFAULT_GRAPH_WINDOW_HOURS = 2
        const val DEFAULT_TIR_WINDOW_HOURS = 24

        private const val KEY_GRAPH_WINDOW = "graph_window_hours"
        private const val KEY_TIR_WINDOW = "tir_window_hours"
    }
}
