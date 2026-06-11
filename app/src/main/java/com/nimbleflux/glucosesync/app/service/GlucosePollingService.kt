package com.nimbleflux.glucosesync.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import com.nimbleflux.glucosesync.app.BuildConfig
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderRegistry
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import com.nimbleflux.glucosesync.app.data.SettingsStore
import com.nimbleflux.glucosesync.app.R
import com.nimbleflux.glucosesync.app.ui.MainActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*

class GlucosePollingService : android.app.Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private lateinit var dataClient: DataClient
    private lateinit var credentialStore: CredentialStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var alertManager: GlucoseAlertManager
    private var provider: GlucoseProvider? = null
    private val accumulatedHistory = mutableListOf<GlucoseHistoryPoint>()

    override fun onCreate() {
        super.onCreate()
        dataClient = Wearable.getDataClient(this)
        credentialStore = CredentialStore(this)
        settingsStore = SettingsStore(this)
        alertManager = GlucoseAlertManager(this)
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()
        startPolling()
        return START_STICKY
    }

    private fun startForeground() {
        val channelId = "medtrum_glucose_polling"
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_monitoring),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_monitoring_desc)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_monitoring_glucose))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val selectedProvider = credentialStore.getSelectedProvider()
            if (selectedProvider == null) {
                stopSelf()
                return@launch
            }

            val p = ProviderRegistry.create(selectedProvider, this@GlucosePollingService, BuildConfig.DEBUG)
            provider = p
            val restored = p.restoreSession()
            if (!restored) {
                stopSelf()
                return@launch
            }

            while (isActive) {
                try {
                    val result = p.fetchGlucose()
                    result.onSuccess { snapshot ->
                        if (snapshot.glucose != null) {
                            val glucose = snapshot.glucose!!
                            val timestamp = snapshot.timestamp
                            if (BuildConfig.DEBUG) Log.d(TAG, "Glucose: $glucose at $timestamp")

                            if (snapshot.history.isNotEmpty()) {
                                accumulatedHistory.clear()
                                accumulatedHistory.addAll(snapshot.history)
                            } else {
                                accumulatedHistory.add(GlucoseHistoryPoint(timestamp, glucose))
                            }
                            val cutoff = System.currentTimeMillis() / 1000 - 86400
                            while (accumulatedHistory.isNotEmpty() && accumulatedHistory.first().timestamp < cutoff) {
                                accumulatedHistory.removeAt(0)
                            }

                            syncToWatch(glucose, timestamp, snapshot.trend.symbol, snapshot.unit, snapshot)

                            val alertsEnabled = try { settingsStore.getAlertsEnabled() } catch (_: Exception) { true }
                            val high = try { settingsStore.getHighThresholdMmol() } catch (_: Exception) { 10.0 }
                            val low = try { settingsStore.getLowThresholdMmol() } catch (_: Exception) { 3.9 }
                            val dnd = try { settingsStore.getOverrideDnd() } catch (_: Exception) { true }
                            val repeat = try { settingsStore.getAlertRepeatMinutes() } catch (_: Exception) { 5 }
                            val sound = try { settingsStore.getAlertSound() } catch (_: Exception) { true }
                            val vibrate = try { settingsStore.getAlertVibrate() } catch (_: Exception) { true }
                            val vibrateDuration = try { settingsStore.getAlertVibrateDuration() } catch (_: Exception) { 3 }

                            alertManager.checkAndAlert(
                                glucoseMmol = glucose,
                                unit = snapshot.unit,
                                highThresholdMmol = high,
                                lowThresholdMmol = low,
                                alertsEnabled = alertsEnabled,
                                overrideDnd = dnd,
                                repeatMinutes = repeat,
                                soundEnabled = sound,
                                vibrateEnabled = vibrate,
                                vibrateDurationSeconds = vibrateDuration
                            )
                        }
                    }.onFailure { e ->
                        if (BuildConfig.DEBUG) Log.e(TAG, "Polling error: ${e.message}")
                        if (p is LibreLinkUpProvider) {
                            val msg = e.message ?: ""
                            if (msg.contains("403") || msg.contains("401") || msg.contains("Session expired")) {
                                if (BuildConfig.DEBUG) Log.d(TAG, "Attempting LibreLinkUp re-auth")
                                val reAuthed = p.reAuthenticate()
                                if (reAuthed) {
                                    credentialStore.saveSelectedProvider("libre_linkup")
                                    if (BuildConfig.DEBUG) Log.d(TAG, "LibreLinkUp re-auth succeeded")
                                } else {
                                    if (BuildConfig.DEBUG) Log.e(TAG, "LibreLinkUp re-auth failed")
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Polling exception: ${e.message}")
                }

                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private fun syncToWatch(glucose: Double, timestamp: Long, trend: String, unit: String, snapshot: GlucoseSnapshot) {
        try {
            val request = PutDataMapRequest.create("/glucose").apply {
                dataMap.putDouble("glucose", glucose)
                dataMap.putLong("timestamp", timestamp)
                dataMap.putString("trend", trend)
                dataMap.putString("unit", unit)
                snapshot.iob?.let { dataMap.putDouble("iob", it) }
                snapshot.delta?.let { dataMap.putDouble("delta", it) }
                snapshot.batteryPercent?.let { dataMap.putDouble("batteryPercent", it) }
                snapshot.basalRate?.let { dataMap.putDouble("basalRate", it) }
                snapshot.lastBolus?.let { dataMap.putDouble("lastBolus", it) }
                snapshot.lastBolusTime?.let { dataMap.putLong("lastBolusTime", it) }
                snapshot.remainingDose?.let { dataMap.putDouble("remainingDose", it) }
                snapshot.highThreshold?.let { dataMap.putDouble("highThreshold", it) }
                snapshot.lowThreshold?.let { dataMap.putDouble("lowThreshold", it) }
                snapshot.timeInRange?.let { dataMap.putDouble("timeInRange", it) }
                snapshot.averageGlucose?.let { dataMap.putDouble("averageGlucose", it) }
                if (accumulatedHistory.isNotEmpty()) {
                    val cutoff = System.currentTimeMillis() / 1000 - 7200
                    val recent = accumulatedHistory.filter { it.timestamp >= cutoff }
                    dataMap.putLongArray("history_ts", recent.map { it.timestamp }.toLongArray())
                    dataMap.putFloatArray("history_gl", recent.map { it.glucoseMmol.toFloat() }.toFloatArray())
                }
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent())
            if (BuildConfig.DEBUG) Log.d(TAG, "Synced to watch: $glucose $unit")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Watch sync failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GlucosePolling"
        private const val POLLING_INTERVAL_MS = 300_000L
    }
}
