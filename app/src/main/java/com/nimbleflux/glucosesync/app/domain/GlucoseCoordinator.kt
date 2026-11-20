package com.nimbleflux.glucosesync.app.domain

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.nimbleflux.glucosesync.app.data.SettingsStore
import com.nimbleflux.glucosesync.shared.domain.GlucoseAggregator
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.wear.WatchPayload
import com.nimbleflux.glucosesync.shared.wear.WatchPayloadCodec

/**
 * Result of one fetch+aggregate+sync cycle. Callers (MainViewModel and
 * GlucosePollingService) react to this differently - VM updates UI state,
 * service updates the foreground notification and may fire alerts.
 */
data class ProcessedSnapshot(
    val snapshot: GlucoseSnapshot,
    val history: List<GlucoseHistoryPoint>,
    val delta: Double?,
    val trend: TrendArrow
)

/**
 * Owns the fetch + aggregate + watch-sync pipeline that was previously
 * duplicated between [com.nimbleflux.glucosesync.app.viewmodel.MainViewModel.refreshGlucose]
 * and [com.nimbleflux.glucosesync.app.service.GlucosePollingService.fetchAndSync].
 *
 * Whichever polling loop invokes [fetchAndProcess] gets the same behaviour:
 * provider fetch, 24h history merge, delta computation, trend resolution,
 * and an urgent watch sync.
 *
 * Alert firing and notification updates stay with the caller because they
 * differ between foreground (VM) and background (service) contexts.
 */
class GlucoseCoordinator(
    private val context: Context,
    private val settingsStore: SettingsStore
) {

    suspend fun fetchAndProcess(
        provider: GlucoseProvider,
        existingHistory: List<GlucoseHistoryPoint>
    ): Result<ProcessedSnapshot> {
        return provider.fetchGlucose().map { snapshot ->
            val merged = if (snapshot.history.isNotEmpty()) {
                GlucoseAggregator.mergeHistory(existingHistory, snapshot.history)
            } else {
                val g = snapshot.glucose
                if (g != null && snapshot.sensorActive) {
                    GlucoseAggregator.mergeHistory(
                        existingHistory,
                        listOf(GlucoseHistoryPoint(snapshot.timestamp, g))
                    )
                } else {
                    existingHistory
                }
            }
            val trimmed = GlucoseAggregator.trimTo24h(merged)
            val deltaMin = try { settingsStore.getDeltaMinutes() } catch (_: Exception) { 5 }
            val computedDelta = GlucoseAggregator.computeDelta(trimmed, deltaMin) ?: snapshot.delta

            // Use smoothed rate (3+3 reading moving average) when enough
            // history is available. Falls back to delta-window rate for
            // short histories. The sensitivity multiplier widens/narrows
            // the STABLE band per the user's preference.
            val sensitivity = try { settingsStore.getTrendSensitivityMultiplier() } catch (_: Exception) { 1.5 }
            val rateOverWindow = GlucoseAggregator.computeSmoothedRate(trimmed, deltaMin)
                ?: computedDelta?.let { it / deltaMin }
            val trend = GlucoseAggregator.resolveTrend(
                snapshot.trend,
                computedDelta = computedDelta,
                computedRate = rateOverWindow,
                sensitivity = sensitivity
            )

            if (snapshot.glucose != null) {
                syncToWatch(snapshot, trimmed, trend.symbol, computedDelta)
            }

            ProcessedSnapshot(snapshot, trimmed, computedDelta, trend)
        }
    }

    /**
     * Push a snapshot to the watch without going through the provider fetch
     * pipeline. Used by demo mode, where the snapshot is synthesised locally.
     */
    suspend fun pushToWatch(
        snapshot: GlucoseSnapshot,
        history: List<GlucoseHistoryPoint>,
        trendSymbol: String,
        delta: Double?
    ) {
        if (snapshot.glucose != null) {
            syncToWatch(snapshot, history, trendSymbol, delta)
        }
    }

    private suspend fun syncToWatch(
        snapshot: GlucoseSnapshot,
        history: List<GlucoseHistoryPoint>,
        trendSymbol: String,
        delta: Double?
    ) {
        try {
            // Alert config always comes from the phone's settings — the watch
            // fires its own threshold alerts from whatever the payload says,
            // so provider-specific snapshot thresholds (Medtrum's pump values)
            // must not leak into it. Alert toggling and repeat windows mirror
            // GlucoseAlertManager's behaviour so the two devices agree.
            val lowThreshold = try { settingsStore.getLowThresholdMmol() } catch (_: Exception) { 3.9 }
            val highThreshold = try { settingsStore.getHighThresholdMmol() } catch (_: Exception) { 10.0 }
            val alertsEnabled = try { settingsStore.getAlertsEnabled() } catch (_: Exception) { true }
            val alertRepeatMinutes = try { settingsStore.getAlertRepeatMinutes() } catch (_: Exception) { 5 }
            val payload = WatchPayload(
                glucose = snapshot.glucose ?: return,
                timestamp = snapshot.timestamp,
                trend = trendSymbol,
                unit = snapshot.unit,
                iob = snapshot.iob,
                delta = delta,
                batteryPercent = snapshot.batteryPercent,
                basalRate = snapshot.basalRate,
                lastBolus = snapshot.lastBolus,
                lastBolusTime = snapshot.lastBolusTime,
                remainingDose = snapshot.remainingDose,
                highThreshold = highThreshold,
                lowThreshold = lowThreshold,
                alertsEnabled = alertsEnabled,
                alertRepeatMinutes = alertRepeatMinutes,
                // Stats are computed from the merged history with the same
                // thresholds the phone UI uses, so the watch shows the same
                // numbers for every provider.
                timeInRange = GlucoseAggregator.computeTimeInRange(history, lowThreshold, highThreshold),
                averageGlucose = history.takeIf { it.isNotEmpty() }?.map { it.glucoseMmol }?.average(),
                history = GlucoseAggregator.trimHistory(history, WatchPayloadCodec.MAX_HISTORY_AGE_SEC)
            )
            val dataClient = Wearable.getDataClient(context)
            dataClient.putDataItem(WatchPayloadCodec.toPutDataRequest(payload))
        } catch (_: Exception) {
            // Watch sync is best-effort; failures shouldn't break the pipeline.
        }
    }
}
