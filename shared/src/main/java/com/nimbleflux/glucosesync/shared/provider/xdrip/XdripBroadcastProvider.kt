package com.nimbleflux.glucosesync.shared.provider.xdrip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.AuthType
import com.nimbleflux.glucosesync.shared.provider.GlucoseError
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderCredentials
import com.nimbleflux.glucosesync.shared.provider.ProviderSession

/**
 * Data captured from a single xDrip+ BgReading broadcast.
 */
private data class BgReading(
    val glucoseMgDl: Double,
    val timestampMs: Long,
    val deltaMgDl: Double?
)

/**
 * Reads glucose data directly from xDrip+ via local broadcast intents.
 *
 * xDrip+ is an open-source app that reads from CGM sensors directly (Libre,
 * Dexcom, etc.) via NFC/BLE. It broadcasts each new reading to other apps
 * on the same phone via the action `com.eveningoutpost.dexdrip.BgReading`.
 *
 * This provider requires NO cloud dependency, NO account, and NO internet
 * connection. The user runs xDrip+ (which handles sensor-specific BLE/NFC
 * protocol, encryption, calibration, and warmup) and GlucoseSync receives
 * the decoded readings locally.
 *
 * The provider maintains an in-memory history buffer that accumulates
 * readings as broadcasts arrive. The 5-minute polling loop (via
 * [fetchGlucose]) serves as a fallback that returns the last cached
 * reading, ensuring stale-data detection still works if broadcasts stop.
 *
 * Setup requirements for the user:
 *  1. Install xDrip+ and configure it for their sensor
 *  2. In xDrip+ → Settings → Inter-App Settings → enable "Broadcast Locally"
 *  3. Select xDrip+ as the provider in GlucoseSync
 */
class XdripBroadcastProvider(
    private val context: Context,
    private val debug: Boolean = false
) : GlucoseProvider {

    override val id: String = "xdrip"
    override val displayName: String = "xDrip+ (Direct Sensor)"
    override val authType: AuthType = AuthType.NONE

    override fun supportsHistory(): Boolean = false
    override fun supportsConnections(): Boolean = false
    override fun supportsPump(): Boolean = false
    override fun supportsDelta(): Boolean = true

    @Volatile
    private var lastReading: BgReading? = null

    private val accumulatedHistory = mutableListOf<GlucoseHistoryPoint>()

    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_BG_READING) return

            val glucose = intent.getFloatExtra(EXTRA_BG_VALUE, -1f)
            if (glucose <= 0f) return

            val timestamp = intent.getLongExtra(EXTRA_BG_TIMESTAMP, System.currentTimeMillis())
            val delta = intent.getFloatExtra(EXTRA_BG_DELTA, Float.NaN)

            lastReading = BgReading(
                glucoseMgDl = glucose.toDouble(),
                timestampMs = timestamp,
                deltaMgDl = if (!delta.isNaN()) delta.toDouble() else null
            )

            // Accumulate history for chart rendering
            val mmol = glucose.toDouble() / 18.0
            val ts = timestamp / 1000
            accumulatedHistory.removeAll { it.timestamp == ts }
            accumulatedHistory.add(GlucoseHistoryPoint(ts, mmol))

            // Trim to 24h
            val cutoff = System.currentTimeMillis() / 1000 - 86_400L
            accumulatedHistory.removeAll { it.timestamp < cutoff }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    fun enableBroadcasts() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_BG_READING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // RECEIVER_EXPORTED: xDrip+ is a different app, so we must
            // allow external broadcasts. Using NOT_EXPORTED here would
            // silently block all xDrip+ broadcasts.
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            // API 26-32: the flag parameter exists but RECEIVER_EXPORTED
            // constant doesn't. All receivers are effectively exported
            // on these API levels, so the plain overload is correct.
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private fun disableBroadcasts() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    override suspend fun login(credentials: ProviderCredentials): Result<ProviderSession> {
        enableBroadcasts()
        return Result.success(
            ProviderSession(
                providerId = id,
                displayName = displayName,
                data = emptyMap()
            )
        )
    }

    override suspend fun restoreSession(): Boolean {
        enableBroadcasts()
        return true
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        val reading = lastReading
            ?: return Result.failure(GlucoseError.NoData)

        val mmol = reading.glucoseMgDl / 18.0
        val deltaMmol = reading.deltaMgDl?.let { it / 18.0 }
        // Defer trend to GlucoseCoordinator — it derives the arrow from
        // accumulatedHistory via computeSmoothedRate, so the user's Trend
        // sensitivity setting applies. Returning UNKNOWN here keeps xDrip
        // consistent with Medtrum (the other local-derivation provider).
        val trend = TrendArrow.UNKNOWN

        return Result.success(
            GlucoseSnapshot(
                glucose = mmol,
                timestamp = reading.timestampMs / 1000,
                trend = trend,
                unit = "mmol/L",
                sensorActive = true,
                delta = deltaMmol,
                history = accumulatedHistory.toList()
            )
        )
    }

    override fun logout() {
        disableBroadcasts()
        lastReading = null
        accumulatedHistory.clear()
    }

    /**
     * Whether at least one reading has been received from xDrip+.
     * Used by the setup screen's "Check Connection" flow.
     */
    fun hasReceivedReading(): Boolean = lastReading != null

    companion object {
        const val ACTION_BG_READING = "com.eveningoutpost.dexdrip.BgReading"
        const val EXTRA_BG_VALUE = "bgValue"
        const val EXTRA_BG_TIMESTAMP = "bgTimestamp"
        const val EXTRA_BG_DELTA = "bgDelta"
    }
}
