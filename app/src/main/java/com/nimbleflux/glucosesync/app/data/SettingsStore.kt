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

    suspend fun getAlertVolumeOverride(): Boolean = prefs.getBoolean("alert_volume_override", false)

    suspend fun setAlertVolumeOverride(enabled: Boolean) {
        prefs.edit().putBoolean("alert_volume_override", enabled).apply()
    }

    suspend fun getAlertAscendingVolume(): Boolean = prefs.getBoolean("alert_ascending_volume", true)

    suspend fun setAlertAscendingVolume(enabled: Boolean) {
        prefs.edit().putBoolean("alert_ascending_volume", enabled).apply()
    }

    suspend fun getAlertVibrateDuration(): Int = prefs.getInt("alert_vibrate_duration", 3)

    suspend fun setAlertVibrateDuration(seconds: Int) {
        prefs.edit().putInt("alert_vibrate_duration", seconds).apply()
    }
}
