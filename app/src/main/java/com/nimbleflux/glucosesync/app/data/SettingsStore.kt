package com.nimbleflux.glucosesync.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "settings_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun getUnit(): String = prefs.getString("glucose_unit", "mmol/L") ?: "mmol/L"

    suspend fun setUnit(unit: String) {
        prefs.edit().putString("glucose_unit", unit).apply()
    }

    suspend fun getAlertsEnabled(): Boolean = prefs.getBoolean("alerts_enabled", true)

    suspend fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("alerts_enabled", enabled).apply()
    }

    suspend fun getHighThresholdMmol(): Double = prefs.getString("high_threshold_mmol", "10.0")?.toDoubleOrNull() ?: 10.0

    suspend fun setHighThresholdMmol(value: Double) {
        prefs.edit().putString("high_threshold_mmol", value.toString()).apply()
    }

    suspend fun getLowThresholdMmol(): Double = prefs.getString("low_threshold_mmol", "3.9")?.toDoubleOrNull() ?: 3.9

    suspend fun setLowThresholdMmol(value: Double) {
        prefs.edit().putString("low_threshold_mmol", value.toString()).apply()
    }

    suspend fun getOverrideDnd(): Boolean = prefs.getBoolean("override_dnd", true)

    suspend fun setOverrideDnd(enabled: Boolean) {
        prefs.edit().putBoolean("override_dnd", enabled).apply()
    }

    suspend fun getAlertRepeatMinutes(): Int = prefs.getInt("alert_repeat_minutes", 5)

    suspend fun setAlertRepeatMinutes(minutes: Int) {
        prefs.edit().putInt("alert_repeat_minutes", minutes).apply()
    }

    suspend fun getAlertSound(): Boolean = prefs.getBoolean("alert_sound", true)

    suspend fun setAlertSound(enabled: Boolean) {
        prefs.edit().putBoolean("alert_sound", enabled).apply()
    }

    suspend fun getAlertVibrate(): Boolean = prefs.getBoolean("alert_vibrate", true)

    suspend fun setAlertVibrate(enabled: Boolean) {
        prefs.edit().putBoolean("alert_vibrate", enabled).apply()
    }

    suspend fun getAlertVibrateDuration(): Int = prefs.getInt("alert_vibrate_duration", 3)

    suspend fun setAlertVibrateDuration(seconds: Int) {
        prefs.edit().putInt("alert_vibrate_duration", seconds).apply()
    }

    suspend fun getThemeMode(): String = prefs.getString("theme_mode", "system") ?: "system"

    suspend fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun getDeltaMinutes(): Int = prefs.getInt("delta_minutes", 5)

    suspend fun setDeltaMinutes(minutes: Int) {
        prefs.edit().putInt("delta_minutes", minutes).apply()
    }

    fun getWearBannerDismissed(): Boolean = prefs.getBoolean("wear_banner_dismissed", false)

    suspend fun setWearBannerDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean("wear_banner_dismissed", dismissed).apply()
    }

    fun getLastHighAlertTime(): Long = prefs.getLong("last_high_alert_time", 0L)

    fun setLastHighAlertTime(epochMs: Long) {
        prefs.edit().putLong("last_high_alert_time", epochMs).apply()
    }

    fun getLastLowAlertTime(): Long = prefs.getLong("last_low_alert_time", 0L)

    fun setLastLowAlertTime(epochMs: Long) {
        prefs.edit().putLong("last_low_alert_time", epochMs).apply()
    }

    fun getLastStaleAlertTime(): Long = prefs.getLong("last_stale_alert_time", 0L)

    fun setLastStaleAlertTime(epochMs: Long) {
        prefs.edit().putLong("last_stale_alert_time", epochMs).apply()
    }
}
