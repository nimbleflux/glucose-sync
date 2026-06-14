package com.nimbleflux.glucosesync.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.util.Log
import com.nimbleflux.glucosesync.app.BuildConfig
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.domain.GlucoseAggregator
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.nimbleflux.glucosesync.shared.provider.GlucoseError
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderRegistry
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import com.nimbleflux.glucosesync.shared.wear.WatchPayload
import com.nimbleflux.glucosesync.shared.wear.WatchPayloadCodec
import com.nimbleflux.glucosesync.app.data.SettingsStore
import com.nimbleflux.glucosesync.app.R
import com.nimbleflux.glucosesync.app.ui.MainActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*

class GlucosePollingService : android.app.Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private lateinit var dataClient: DataClient
    private lateinit var credentialStore: CredentialStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var alertManager: GlucoseAlertManager
    private lateinit var coordinator: com.nimbleflux.glucosesync.app.domain.GlucoseCoordinator
    private var provider: GlucoseProvider? = null
    private val accumulatedHistory = mutableListOf<GlucoseHistoryPoint>()
    private var lastGlucose: Double? = null
    private var lastTrend: String = ""
    private var lastDelta: Double? = null
    private var lastUnit: String = "mmol/L"
    private var lastBattery: Double? = null
    private var lastTimestamp: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == "/request_glucose") {
            if (BuildConfig.DEBUG) Log.d(TAG, "Watch requested fresh data")
            scope.launch {
                fetchAndSync()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataClient = Wearable.getDataClient(this)
        credentialStore = CredentialStore(this)
        settingsStore = SettingsStore(this)
        alertManager = GlucoseAlertManager.getInstance(this)
        coordinator = com.nimbleflux.glucosesync.app.domain.GlucoseCoordinator(this, settingsStore)
        Wearable.getMessageClient(this).addListener(messageListener)
        PollingWorker.schedule(this)
        restoreHistory()
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
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = GlucoseNotificationBuilder.buildDefault(this, channelId)
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glucosesync:polling").apply {
            acquire(60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun updateNotification() {
        val glucose = lastGlucose ?: return
        try {
            val notification = GlucoseNotificationBuilder.build(
                context = this,
                channelId = "medtrum_glucose_polling",
                glucose = glucose,
                trend = lastTrend,
                delta = lastDelta,
                unit = lastUnit,
                batteryPercent = lastBattery,
                timestamp = lastTimestamp
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(1, notification)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Notification update failed: ${e.message}")
        }
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
                acquireWakeLock()
                try {
                    fetchAndSync()
                } finally {
                    releaseWakeLock()
                }
                // Schedule a Doze-proof alarm to re-trigger the FGS in case
                // the OS kills our process during the 5-min delay window.
                // The alarm is a safety net; if the process is still alive
                // when it fires, the receiver's startForegroundService is a
                // no-op (just delivers a fresh onStartCommand we ignore).
                try {
                    com.nimbleflux.glucosesync.app.receiver.PollingAlarmReceiver.scheduleNext(
                        this@GlucosePollingService,
                        POLLING_INTERVAL_MS
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Could not schedule polling alarm: ${e.message}")
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchAndSync() {
        val p = provider ?: return
        try {
            coordinator.fetchAndProcess(p, accumulatedHistory.toList())
                .onSuccess { processed ->
                    val snapshot = processed.snapshot
                    val glucose = snapshot.glucose
                    if (glucose != null) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Glucose: $glucose at ${snapshot.timestamp}")

                        accumulatedHistory.clear()
                        accumulatedHistory.addAll(processed.history)
                        saveHistory()

                        lastGlucose = glucose
                        lastTrend = processed.trend.symbol
                        lastDelta = processed.delta
                        lastUnit = snapshot.unit
                        lastBattery = snapshot.batteryPercent
                        lastTimestamp = snapshot.timestamp

                        updateNotification()

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
                    val shouldReauth = when (e) {
                        is GlucoseError.SessionExpired -> true
                        is GlucoseError.ServerError -> e.code == 401 || e.code == 403
                        else -> e.message?.contains("403") == true || e.message?.contains("401") == true
                    }
                    if (shouldReauth && p is LibreLinkUpProvider) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Polling exception: ${e.message}")
        } finally {
            // Always check staleness, regardless of fetch outcome. A failed
            // fetch is the most common cause of stale data, so this belongs
            // in finally, not in onSuccess/onFailure. Skip if we have no
            // reading yet (cold start) - nothing to compare against.
            if (lastTimestamp > 0L) {
                val alertsEnabled = try { settingsStore.getAlertsEnabled() } catch (_: Exception) { true }
                alertManager.checkStaleAlert(
                    lastReadingEpochSec = lastTimestamp,
                    staleThresholdMinutes = 15,
                    alertsEnabled = alertsEnabled
                )
            }
        }
    }

    private fun saveHistory() {
        try {
            val serializer = ListSerializer(GlucoseHistoryPoint.serializer())
            val raw = Json.encodeToString(serializer, accumulatedHistory.toList())
            getSharedPreferences("polling_state", MODE_PRIVATE)
                .edit()
                .putString("accumulated_history", raw)
                .apply()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Could not persist history: ${e.message}")
        }
    }

    private fun restoreHistory() {
        try {
            val raw = getSharedPreferences("polling_state", MODE_PRIVATE)
                .getString("accumulated_history", null) ?: return
            val serializer = ListSerializer(GlucoseHistoryPoint.serializer())
            val restored = Json.decodeFromString(serializer, raw)
            val cutoff = System.currentTimeMillis() / 1000 - 86_400L
            accumulatedHistory.addAll(restored.filter { it.timestamp >= cutoff })
            if (BuildConfig.DEBUG) Log.d(TAG, "Restored ${accumulatedHistory.size} history points from prefs")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Could not restore history: ${e.message}")
        }
    }

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(messageListener)
        pollingJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        try {
            com.nimbleflux.glucosesync.app.receiver.PollingAlarmReceiver.cancel(this)
        } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GlucosePolling"
        private const val POLLING_INTERVAL_MS = 300_000L
    }
}
