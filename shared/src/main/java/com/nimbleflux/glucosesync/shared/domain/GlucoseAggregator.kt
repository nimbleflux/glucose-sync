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

    /**
     * Resolve a snapshot's trend arrow. If the provider already returned a
     * known trend (not UNKNOWN), it is preserved. Otherwise derive from the
     * computed rate when available (more accurate - normalises for history
     * spacing), falling back to delta, falling back to STABLE.
     */
    fun resolveTrend(
        snapshotTrend: TrendArrow,
        computedDelta: Double? = null,
        computedRate: Double? = null
    ): TrendArrow {
        if (snapshotTrend != TrendArrow.UNKNOWN) return snapshotTrend
        return when {
            computedRate != null -> TrendArrow.fromRate(computedRate)
            computedDelta != null -> TrendArrow.fromDelta(computedDelta)
            else -> TrendArrow.STABLE
        }
    }

    /**
     * Per-minute glucose rate from the last two history points, in mmol/L/min.
     * Null when insufficient history or when the last two points have an
     * identical timestamp (would divide by zero).
     */
    fun computeRatePerMinute(history: List<GlucoseHistoryPoint>): Double? {
        if (history.size < 2) return null
        val last = history.last()
        val prev = history[history.size - 2]
        val seconds = last.timestamp - prev.timestamp
        if (seconds <= 0) return null
        val minutes = seconds / 60.0
        return (last.glucoseMmol - prev.glucoseMmol) / minutes
    }
}
