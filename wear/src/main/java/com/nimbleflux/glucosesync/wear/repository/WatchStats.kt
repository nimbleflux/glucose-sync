package com.nimbleflux.glucosesync.wear.repository

import com.nimbleflux.glucosesync.shared.domain.GlucoseAggregator
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint

/**
 * Stats the watch computes locally from its stored history over the user's
 * configured window (see [WatchSettings]). When the stored history doesn't
 * span the window — a fresh install, or an older phone build that only
 * pushes 2h — the phone-computed payload values are used as fallback, and
 * [coveredWindow] is false so the UI can label them with the phone's 24h
 * basis instead of the configured window.
 */
data class WatchWindowStats(
    val timeInRange: Double?,
    val averageGlucose: Double?,
    val coveredWindow: Boolean
)

fun computeWindowStats(
    history: List<Pair<Long, Double>>,
    windowHours: Int,
    lowThreshold: Double,
    highThreshold: Double,
    fallback: WatchWindowStats?,
    nowSec: Long
): WatchWindowStats? {
    if (history.isEmpty()) return fallback
    val cutoff = nowSec - windowHours * 3600L
    // Covered only when the oldest reading predates the window start —
    // otherwise "TIR over the chosen window" would really be TIR over a
    // partial span, mislabeled.
    val covered = history.first().first <= cutoff
    if (!covered) return fallback
    val windowed = history
        .filter { it.first >= cutoff }
        .map { GlucoseHistoryPoint(it.first, it.second) }
    return WatchWindowStats(
        timeInRange = GlucoseAggregator.computeTimeInRange(windowed, lowThreshold, highThreshold),
        averageGlucose = windowed.takeIf { it.isNotEmpty() }?.map { it.glucoseMmol }?.average(),
        coveredWindow = true
    )
}
