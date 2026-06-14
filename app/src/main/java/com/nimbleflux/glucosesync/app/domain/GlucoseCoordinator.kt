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
            val trend = GlucoseAggregator.resolveTrend(snapshot.trend, computedDelta)

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
    fun pushToWatch(
        snapshot: GlucoseSnapshot,
        history: List<GlucoseHistoryPoint>,
        trendSymbol: String,
        delta: Double?
    ) {
        if (snapshot.glucose != null) {
            syncToWatch(snapshot, history, trendSymbol, delta)
        }
    }

    private fun syncToWatch(
        snapshot: GlucoseSnapshot,
        history: List<GlucoseHistoryPoint>,
        trendSymbol: String,
        delta: Double?
    ) {
        try {
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
                highThreshold = snapshot.highThreshold,
                lowThreshold = snapshot.lowThreshold,
                timeInRange = snapshot.timeInRange,
                averageGlucose = snapshot.averageGlucose,
                history = GlucoseAggregator.trimHistory(history, WatchPayloadCodec.MAX_HISTORY_AGE_SEC)
            )
            val dataClient = Wearable.getDataClient(context)
            dataClient.putDataItem(WatchPayloadCodec.toPutDataRequest(payload))
        } catch (_: Exception) {
            // Watch sync is best-effort; failures shouldn't break the pipeline.
        }
    }
}
