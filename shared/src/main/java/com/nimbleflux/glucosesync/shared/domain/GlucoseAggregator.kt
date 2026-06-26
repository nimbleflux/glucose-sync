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
        computedRate: Double? = null,
        sensitivity: Double = 1.0
    ): TrendArrow {
        if (snapshotTrend != TrendArrow.UNKNOWN) return snapshotTrend
        return when {
            computedRate != null -> TrendArrow.fromRate(computedRate, sensitivity)
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

    /**
     * Smoothed per-minute glucose rate using a 3+3 reading moving average.
     * Compares the average of the last 3 readings with the average of 3
     * readings from approximately [windowMinutes] ago. This filters
     * single-reading sensor noise that causes false trend signals.
     *
     * Returns null when there aren't enough readings for smoothing —
     * callers should fall back to [computeRatePerMinute] or
     * [computeDelta] in that case.
     */
    fun computeSmoothedRate(
        history: List<GlucoseHistoryPoint>,
        windowMinutes: Int
    ): Double? {
        if (history.size < 6) return null

        val recent = history.takeLast(3)
        val recentAvg = recent.map { it.glucoseMmol }.average()

        val windowStart = recent.last().timestamp - windowMinutes * 60L
        val olderCandidates = history.filter { it.timestamp <= windowStart }
        if (olderCandidates.size < 3) return null

        val older = olderCandidates.takeLast(3)
        val olderAvg = older.map { it.glucoseMmol }.average()

        val timeDiffSec = recent.last().timestamp - older.last().timestamp
        if (timeDiffSec <= 0) return null

        return (recentAvg - olderAvg) / (timeDiffSec / 60.0)
    }
}
