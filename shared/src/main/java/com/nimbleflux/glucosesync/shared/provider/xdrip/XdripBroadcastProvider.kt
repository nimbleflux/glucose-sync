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
 * Reads glucose data directly from xDrip+ via local broadcast intents.
 *
 * xDrip+ is an open-source app that reads from CGM sensors directly (Libre,
 * Dexcom, etc.) via NFC/BLE. It broadcasts each new reading to other apps
 * on the same phone.
 *
 * Stock xDrip+ sends the action `com.eveningoutpost.dexdrip.BgEstimate`
 * with fully-qualified extras (`...Extras.BgEstimate`, `...Extras.Time`,
 * `...Extras.BgSlope`, `...Extras.BgSlopeName`, `...Extras.SensorBattery`,
 * …). The legacy `com.eveningoutpost.dexdrip.BgReading` action with bare
 * extras `bgValue`/`bgTimestamp`/`bgDelta` is the **Diabox** convention
 * (some third-party xDrip forks too); we listen for both so the provider
 * works regardless of which app is the source.
 *
 * Beyond glucose/timestamp/delta, the stock xDrip+ broadcast also carries
 * the trend arrow (from `BgSlopeName`) and the transmitter battery
 * (`SensorBattery`), which we surface on the snapshot. Insulin/pump data
 * (IOB, basal, bolus) is NOT available from plain xDrip+ — it only appears
 * if the user also runs a loop system (AAPS/Loop) feeding the separate
 * `com.eveningoutpost.dexdrip.ExternalStatusline` broadcast, which is out
 * of scope here. High/low thresholds are not exposed by xDrip+ either.
 *
 * This provider requires NO cloud dependency, NO account, and NO internet
 * connection. The user runs xDrip+ (which handles sensor-specific BLE/NFC
 * protocol, encryption, calibration, and warmup) and GlucoseSync receives
 * the decoded readings locally.
 *
 * The provider maintains an in-memory history buffer that accumulates
 * readings as broadcasts arrive. On session restore it also attempts a
 * one-shot backfill from xDrip+'s local Web Service
 * (http://127.0.0.1:17580/sgv.json) so the chart starts with up to 24h of
 * data instead of filling point-by-point. The backfill is best-effort: if
 * the Web Service is off or unreachable, the provider keeps working in
 * broadcast-only mode and the chart fills as new readings arrive.
 *
 * The 5-minute polling loop (via [fetchGlucose]) serves as a fallback that
 * returns the last cached reading, ensuring stale-data detection still works
 * if broadcasts stop.
 *
 * Setup requirements for the user:
 *  1. Install xDrip+ and configure it for their sensor
 *  2. In xDrip+ → Settings → Inter-App Settings → enable "Broadcast Locally"
 *     (required for any data to arrive)
 *  3. Optionally enable "xDrip Web Service" in the same screen — this lets
 *     GlucoseSync backfill up to 24h of history on connect so the chart
 *     isn't empty for the first ~5 minutes per point
 *  4. Select xDrip+ as the provider in GlucoseSync
 */
class XdripBroadcastProvider(
    private val context: Context,
    private val debug: Boolean = false
) : GlucoseProvider {

    override val id: String = "xdrip"
    override val displayName: String = "xDrip+ (Direct Sensor)"
    override val authType: AuthType = AuthType.NONE

    override fun supportsHistory(): Boolean = true
    override fun supportsConnections(): Boolean = false
    override fun supportsPump(): Boolean = false
    override fun supportsDelta(): Boolean = true

    @Volatile
    private var lastReading: XdripReading? = null

    // Synchronizes mutation of [lastReading] and [accumulatedHistory]. The
    // broadcast receiver runs on the main thread while [fetchGlucose] and
    // [backfillHistory] read/write from IO coroutines — without a lock the
    // receiver's removeAll/add interleavings could race a concurrent read.
    private val lock = Any()

    private val accumulatedHistory = mutableListOf<GlucoseHistoryPoint>()

    private var receiverRegistered = false

    /**
     * Diagnostics for the setup "Check Connection" flow. Counting every
     * incoming BgEstimate/BgReading intent separately from the ones we
     * actually accept lets the setup screen distinguish:
     *  - seen == 0           -> xDrip+ isn't broadcasting (Broadcast Locally
     *                           off, xDrip+ not running, or wrong action).
     *  - seen > 0, accepted == 0 -> broadcasts arrive but the glucose extra
     *                           is missing/invalid (a key/format mismatch).
     */
    @Volatile
    private var broadcastsSeen = 0
    @Volatile
    private var broadcastsAccepted = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            broadcastsSeen++

            // Parsing is shared with the manifest-declared receiver so both
            // paths stay identical. See [XdripIntentParser].
            val reading = when (val result = XdripIntentParser.parse(intent)) {
                is XdripParseResult.NoGlucose -> {
                    // Broadcasts arriving but with no usable glucose value — most
                    // often a key/format mismatch between this app and the source.
                    // Log the keys we actually received so the cause is
                    // diagnosable instead of a silent drop.
                    if (debug) {
                        android.util.Log.w(
                            TAG,
                            "Rejected BgEstimate/BgReading broadcast: " +
                                "action=${intent.action}, " +
                                "extras=${intent.extras?.keySet()?.joinToString()}"
                        )
                    }
                    return
                }
                is XdripParseResult.Accepted -> result.reading
            }

            broadcastsAccepted++

            // Persist the latest reading so it survives process death and can
            // seed the dashboard after a cold start even if the Web Service is
            // off. Best-effort — a failure here must not block the live path.
            runCatching {
                XdripLatestReadingStore(context).save(reading)
            }

            synchronized(lock) {
                lastReading = reading

                // Accumulate history for chart rendering
                val mmol = reading.glucoseMgDl / 18.0
                val ts = reading.timestampMs / 1000
                accumulatedHistory.removeAll { it.timestamp == ts }
                accumulatedHistory.add(GlucoseHistoryPoint(ts, mmol))

                // Trim to 24h
                val cutoff = System.currentTimeMillis() / 1000 - 86_400L
                accumulatedHistory.removeAll { it.timestamp < cutoff }
            }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    fun enableBroadcasts() {
        if (receiverRegistered) return
        // Register for both the stock xDrip+ action and the Diabox-style
        // legacy action so the provider works with either source app.
        val filter = IntentFilter().apply {
            addAction(ACTION_BG_ESTIMATE)
            addAction(ACTION_BG_READING)
        }
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
        // Backfill on first-time setup too (not just restoreSession) so the
        // Check Connection flow can succeed immediately from historic data,
        // and the chart renders as soon as the user lands on the dashboard —
        // no need to wait ~5 min for the first live broadcast.
        backfillHistory()
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
        // Seed the last-known reading from the persisted store. The manifest
        // receiver writes there on every broadcast, so even after a process
        // kill (Doze, app update, OEM killer) the dashboard can render the
        // most recent reading immediately instead of NoData — independent of
        // whether the Web Service is enabled.
        seedFromPersistedStore()
        // Best-effort historic backfill so the chart isn't empty on first
        // launch. No-op (returns empty) if the xDrip Web Service is off.
        backfillHistory()
        return true
    }

    /**
     * Seed [lastReading] from the persisted store captured by the manifest
     * receiver, but only when it is newer than whatever is already cached (a
     * live broadcast may have arrived between process start and this call).
     * Also folds it into [accumulatedHistory] so the chart shows the point.
     * Best-effort: any failure is swallowed so it never blocks session
     * restore.
     */
    private fun seedFromPersistedStore() {
        val persisted = runCatching {
            XdripLatestReadingStore(context).load()
        }.getOrNull() ?: return
        synchronized(lock) {
            val current = lastReading
            if (current == null || persisted.timestampMs > current.timestampMs) {
                lastReading = persisted
                val mmol = persisted.glucoseMgDl / 18.0
                val ts = persisted.timestampMs / 1000
                accumulatedHistory.removeAll { it.timestamp == ts }
                accumulatedHistory.add(GlucoseHistoryPoint(ts, mmol))
            }
        }
    }

    /**
     * Pull up to 24h of historic SGV entries from xDrip+'s local Web Service
     * and merge them into [accumulatedHistory]. Also seeds [lastReading] from
     * the newest entry when no live broadcast has arrived yet (or when the
     * cached reading is stale and the Web Service has something newer), so the
     * chart and current value can render immediately instead of waiting up to
     * 5 min for the first broadcast. Best-effort: any failure (service
     * disabled, wrong secret, parse error) is swallowed so the provider keeps
     * working in broadcast-only mode.
     */
    private suspend fun backfillHistory() {
        try {
            val service = XdripWebService.create(secret = null, debug = debug)
            val entries = service.getSgv(count = 288)
            if (entries.isEmpty()) return

            val cutoff = System.currentTimeMillis() / 1000 - 86_400L
            var newestMs: Long = 0
            var newestMgdl: Int = 0
            var newestDirection: String? = null
            val merged = mutableListOf<GlucoseHistoryPoint>()
            entries.forEach { e ->
                val ms = e.date ?: return@forEach
                val mgdl = e.sgv ?: return@forEach
                if (mgdl <= 0) return@forEach
                val ts = ms / 1000
                if (ts < cutoff) return@forEach
                val mmol = mgdl / 18.0
                merged.removeAll { it.timestamp == ts }
                merged.add(GlucoseHistoryPoint(ts, mmol))
                if (ms > newestMs) {
                    newestMs = ms
                    newestMgdl = mgdl
                    newestDirection = e.direction
                }
            }

            synchronized(lock) {
                // Dedup by timestamp against anything a live broadcast already
                // contributed, then merge the Web Service points in.
                merged.forEach { ws ->
                    accumulatedHistory.removeAll { it.timestamp == ws.timestamp }
                    accumulatedHistory.add(ws)
                }
                accumulatedHistory.sortBy { it.timestamp }

                // Seed a current reading from the newest historic entry so the
                // chart/value can render before the first live broadcast arrives.
                // A real broadcast later overwrites lastReading with fresher data
                // (and typically a transmitter battery + slope-derived delta that
                // the Web Service doesn't expose).
                // Also re-seed when the cached reading is stale and the Web
                // Service has a newer entry — this is what makes the dashboard
                // recover missed readings instead of showing stale data forever.
                val current = lastReading
                val shouldSeed = current == null ||
                    (newestMs > 0 && newestMs > current.timestampMs)
                if (newestMs > 0 && shouldSeed) {
                    lastReading = XdripReading(
                        glucoseMgDl = newestMgdl.toDouble(),
                        timestampMs = newestMs,
                        deltaMgDl = null,
                        trend = XdripIntentParser.mapSlopeName(newestDirection)
                    )
                }
            }

            if (debug) {
                android.util.Log.d(
                    TAG,
                    "xDrip Web Service backfill: ${accumulatedHistory.size} points" +
                        (if (lastReading != null) ", seeded lastReading" else "")
                )
            }
        } catch (e: java.io.IOException) {
            // Web Service off / unreachable — expected when the user hasn't
            // enabled it. Not an error; broadcast accumulation still works.
            if (debug) android.util.Log.d(TAG, "xDrip Web Service backfill skipped: ${e.message}")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (debug) android.util.Log.w(TAG, "xDrip Web Service backfill failed: ${e.message}")
        }
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        val reading = synchronized(lock) { lastReading } ?: run {
            // No cached reading yet — re-attempt the Web Service backfill
            // before giving up. This reuses the caller's polling loop (the
            // app's ~60s auto-refresh, or pull-to-refresh) as a natural
            // retry: each tick tries again until the Web Service responds or
            // a live broadcast arrives. Self-suppresses once lastReading is
            // set, so there's no ongoing Web Service polling once data flows.
            backfillHistory()
            synchronized(lock) { lastReading } ?: return Result.failure(GlucoseError.NoData)
        }

        // Staleness recovery: if the cached reading is older than one CGM
        // interval, a broadcast was likely missed (process killed, Doze,
        // receiver not registered). Re-pull from the Web Service so a newer
        // entry — if one exists — can refresh lastReading before we return.
        // Self-suppresses for users with the Web Service off (IOException is
        // swallowed in backfillHistory, and we return the existing reading).
        if (System.currentTimeMillis() - reading.timestampMs > STALE_THRESHOLD_MS) {
            backfillHistory()
        }

        val current = synchronized(lock) { lastReading } ?: reading
        val mmol = current.glucoseMgDl / 18.0
        val deltaMmol = current.deltaMgDl?.let { it / 18.0 }
        // Use the trend arrow xDrip+ sent in the broadcast (from BgSlopeName).
        // If the source didn't include one, fall back to UNKNOWN and let the
        // GlucoseCoordinator derive an arrow from accumulatedHistory instead.
        val trend = if (current.trend != TrendArrow.UNKNOWN) current.trend else TrendArrow.UNKNOWN

        return Result.success(
            GlucoseSnapshot(
                glucose = mmol,
                timestamp = current.timestampMs / 1000,
                trend = trend,
                unit = "mmol/L",
                sensorActive = true,
                delta = deltaMmol,
                batteryPercent = current.batteryPercent,
                history = synchronized(lock) { accumulatedHistory.toList() }
            )
        )
    }

    override fun logout() {
        disableBroadcasts()
        synchronized(lock) {
            lastReading = null
            accumulatedHistory.clear()
        }
    }

    /**
     * Whether at least one reading has been received from xDrip+.
     * Used by the setup screen's "Check Connection" flow.
     */
    fun hasReceivedReading(): Boolean = lastReading != null

    /** Number of BgReading broadcasts seen since [enableBroadcasts]. */
    fun getBroadcastsSeen(): Int = broadcastsSeen

    /** Number of broadcasts that passed the glucose validation. */
    fun getBroadcastsAccepted(): Int = broadcastsAccepted

    /**
     * Reset the seen/accepted counters. Called at the start of a "Check
     * Connection" attempt so each attempt reports only the broadcasts that
     * arrived during that attempt — letting the user see whether a change
     * they just made in xDrip+ (e.g. enabling Broadcast Locally) had effect.
     */
    fun resetDiagnostics() {
        broadcastsSeen = 0
        broadcastsAccepted = 0
    }

    /**
     * Map an xDrip+ slope-name string to our TrendArrow enum. Delegates to the
     * shared parser so the mapping has a single source of truth. Kept as an
     * instance method for the setup screen's "Check Connection" diagnostics.
     */
    internal fun mapSlopeName(slopeName: String?): TrendArrow =
        XdripIntentParser.mapSlopeName(slopeName)

    companion object {
        private const val TAG = "XdripBroadcastProvider"

        /**
         * A cached reading older than this is considered stale and triggers a
         * Web Service re-pull in [fetchGlucose]. One CGM interval (5 min) plus
         * a minute of slack — matches the rate at which xDrip+ emits and
         * avoids hammering the Web Service on every poll.
         */
        const val STALE_THRESHOLD_MS = 6L * 60_000L

        // Stock xDrip+ action and fully-qualified extras
        // (com.eveningoutpost.dexdrip.utilitymodels.Intents / BroadcastGlucose).
        const val ACTION_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
        const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
        const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery"

        // Diabox-style legacy action and bare extras (kept for compatibility).
        const val ACTION_BG_READING = "com.eveningoutpost.dexdrip.BgReading"
        const val EXTRA_BG_VALUE = "bgValue"
        const val EXTRA_BG_TIMESTAMP = "bgTimestamp"
        const val EXTRA_BG_DELTA = "bgDelta"
    }
}
