package com.nimbleflux.glucosesync.shared.domain

object GlucoseAggregator {

    fun computeDelta(history: List<GlucoseHistoryPoint>, deltaMinutes: Int): Double? {
        if (history.size < 2) return null
        val latest = history.last()
        val targetTime = latest.timestamp - deltaMinutes * 60L
        val closest = history.filter { it.timestamp <= targetTime }
            .minByOrNull { Math.abs(it.timestamp - targetTime) }
            ?: return null
        return latest.glucoseMmol - closest.glucoseMmol
    }

    fun mergeHistory(
        existing: List<GlucoseHistoryPoint>,
        providerHistory: List<GlucoseHistoryPoint>
    ): List<GlucoseHistoryPoint> {
        val providerTimestamps = providerHistory.map { it.timestamp }.toSet()
        val kept = existing.filter { it.timestamp !in providerTimestamps }
        return (kept + providerHistory).sortedBy { it.timestamp }
    }

    fun trimHistory(
        history: List<GlucoseHistoryPoint>,
        maxAgeSec: Long,
        nowSec: Long = System.currentTimeMillis() / 1000
    ): List<GlucoseHistoryPoint> {
        val cutoff = nowSec - maxAgeSec
        return history.filter { it.timestamp >= cutoff }
    }

    fun trimTo24h(
        history: List<GlucoseHistoryPoint>,
        nowSec: Long = System.currentTimeMillis() / 1000
    ): List<GlucoseHistoryPoint> = trimHistory(history, 86_400L, nowSec)

    fun resolveTrend(snapshotTrend: TrendArrow, computedDelta: Double?): TrendArrow =
        if (snapshotTrend == TrendArrow.UNKNOWN) {
            TrendArrow.fromDelta(computedDelta ?: 0.0)
        } else {
            snapshotTrend
        }
}
