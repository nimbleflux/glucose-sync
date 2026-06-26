package com.nimbleflux.glucosesync.shared.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlucoseAggregatorTest {

    @Test
    fun computeDelta_returnsNullForEmptyHistory() {
        assertNull(GlucoseAggregator.computeDelta(emptyList(), deltaMinutes = 5))
    }

    @Test
    fun computeDelta_returnsNullForSinglePoint() {
        val history = listOf(GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0))
        assertNull(GlucoseAggregator.computeDelta(history, deltaMinutes = 5))
    }

    @Test
    fun computeDelta_returnsNullWhenNoPointAtOrBeforeTargetTime() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 6.0)
        )
        // deltaMinutes=5 -> targetTime = 1100 - 300 = 800; both points are after target
        assertNull(GlucoseAggregator.computeDelta(history, deltaMinutes = 5))
    }

    @Test
    fun computeDelta_returnsDifferenceAgainstClosestPointBeforeTarget() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 800L, glucoseMmol = 4.0),
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 6.0)
        )
        // targetTime = 1100 - 300 = 800; closest point at 800 -> delta = 6 - 4 = 2
        val delta = GlucoseAggregator.computeDelta(history, deltaMinutes = 5)
        assertEquals(2.0, delta!!, 0.0001)
    }

    @Test
    fun computeDelta_picksClosestWhenMultipleCandidatesBeforeTarget() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 700L, glucoseMmol = 3.0), // further from 800
            GlucoseHistoryPoint(timestamp = 790L, glucoseMmol = 4.0), // closest to 800
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 6.0)
        )
        val delta = GlucoseAggregator.computeDelta(history, deltaMinutes = 5)
        assertEquals(2.0, delta!!, 0.0001)
    }

    @Test
    fun computeDelta_supportsNegativeDelta() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 800L, glucoseMmol = 7.0),
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 5.0)
        )
        val delta = GlucoseAggregator.computeDelta(history, deltaMinutes = 5)
        assertEquals(-2.0, delta!!, 0.0001)
    }

    @Test
    fun mergeHistory_emptyExisting_returnsProviderHistory() {
        val providerHistory = listOf(
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 5.5)
        )
        val merged = GlucoseAggregator.mergeHistory(emptyList(), providerHistory)
        assertEquals(providerHistory, merged)
    }

    @Test
    fun mergeHistory_emptyProviderHistory_returnsExisting() {
        val existing = listOf(
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0)
        )
        val merged = GlucoseAggregator.mergeHistory(existing, emptyList())
        assertEquals(existing, merged)
    }

    @Test
    fun mergeHistory_dedupesByTimestamp_preferringProvider() {
        val existing = listOf(
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 99.0) // overwritten by provider
        )
        val providerHistory = listOf(
            GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 5.5),
            GlucoseHistoryPoint(timestamp = 1_200L, glucoseMmol = 6.0)
        )
        val merged = GlucoseAggregator.mergeHistory(existing, providerHistory)
        assertEquals(
            listOf(
                GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0),
                GlucoseHistoryPoint(timestamp = 1_100L, glucoseMmol = 5.5),
                GlucoseHistoryPoint(timestamp = 1_200L, glucoseMmol = 6.0)
            ),
            merged
        )
    }

    @Test
    fun mergeHistory_returnsSortedByTimestamp() {
        val existing = listOf(
            GlucoseHistoryPoint(timestamp = 1_500L, glucoseMmol = 7.0)
        )
        val providerHistory = listOf(
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 800L, glucoseMmol = 4.0)
        )
        val merged = GlucoseAggregator.mergeHistory(existing, providerHistory)
        assertEquals(
            listOf(800L, 1_000L, 1_500L),
            merged.map { it.timestamp }
        )
    }

    @Test
    fun trimHistory_dropsPointsOlderThanMaxAge() {
        val now = 10_000L
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 100L, glucoseMmol = 4.0), // dropped (older than 1000)
            GlucoseHistoryPoint(timestamp = 9_000L, glucoseMmol = 5.0), // kept
            GlucoseHistoryPoint(timestamp = 9_500L, glucoseMmol = 6.0)  // kept
        )
        val trimmed = GlucoseAggregator.trimHistory(history, maxAgeSec = 1_000L, nowSec = now)
        assertEquals(
            listOf(9_000L, 9_500L),
            trimmed.map { it.timestamp }
        )
    }

    @Test
    fun trimHistory_keepsBoundaryInclusive() {
        val now = 10_000L
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 9_000L, glucoseMmol = 5.0), // exactly on cutoff
            GlucoseHistoryPoint(timestamp = 8_999L, glucoseMmol = 4.0)  // just past cutoff
        )
        val trimmed = GlucoseAggregator.trimHistory(history, maxAgeSec = 1_000L, nowSec = now)
        assertEquals(listOf(9_000L), trimmed.map { it.timestamp })
    }

    @Test
    fun trimTo24h_uses86400SecondWindow() {
        val now = 100_000L
        val history = listOf(
            GlucoseHistoryPoint(timestamp = now - 86_400L, glucoseMmol = 5.0), // boundary
            GlucoseHistoryPoint(timestamp = now - 86_401L, glucoseMmol = 4.0)  // dropped
        )
        val trimmed = GlucoseAggregator.trimTo24h(history, nowSec = now)
        assertEquals(listOf(now - 86_400L), trimmed.map { it.timestamp })
    }

    @Test
    fun resolveTrend_passesThroughNonUnknown() {
        assertEquals(
            TrendArrow.RISING,
            GlucoseAggregator.resolveTrend(TrendArrow.RISING, computedDelta = -5.0)
        )
    }

    @Test
    fun resolveTrend_unknownWithNullDelta_defaultsToStable() {
        assertEquals(
            TrendArrow.STABLE,
            GlucoseAggregator.resolveTrend(TrendArrow.UNKNOWN, computedDelta = null)
        )
    }

    @Test
    fun resolveTrend_unknownWithLargePositiveDelta_yieldsRisingRapidly() {
        assertEquals(
            TrendArrow.RISING_RAPIDLY,
            GlucoseAggregator.resolveTrend(TrendArrow.UNKNOWN, computedDelta = 2.5)
        )
    }

    @Test
    fun resolveTrend_unknownWithLargeNegativeDelta_yieldsFallingRapidly() {
        assertEquals(
            TrendArrow.FALLING_RAPIDLY,
            GlucoseAggregator.resolveTrend(TrendArrow.UNKNOWN, computedDelta = -2.5)
        )
    }

    @Test
    fun resolveTrend_unknownWithNullDeltaAndNullRate_yieldsStable() {
        assertEquals(
            TrendArrow.STABLE,
            GlucoseAggregator.resolveTrend(TrendArrow.UNKNOWN)
        )
    }

    @Test
    fun resolveTrend_prefersRateOverDeltaWhenBothProvided() {
        assertEquals(
            TrendArrow.RISING_RAPIDLY,
            GlucoseAggregator.resolveTrend(
                snapshotTrend = TrendArrow.UNKNOWN,
                computedDelta = 0.6,
                computedRate = 0.20
            )
        )
    }

    @Test
    fun resolveTrend_appliesSensitivityMultiplier() {
        // rate 0.08: with standard sensitivity (1.0) → RISING_SLOWLY (0.08 > 0.06)
        // with conservative (1.5) → STABLE (0.08 < 0.06 * 1.5 = 0.09)
        assertEquals(
            TrendArrow.RISING_SLOWLY,
            GlucoseAggregator.resolveTrend(
                TrendArrow.UNKNOWN, computedRate = 0.08, sensitivity = 1.0
            )
        )
        assertEquals(
            TrendArrow.STABLE,
            GlucoseAggregator.resolveTrend(
                TrendArrow.UNKNOWN, computedRate = 0.08, sensitivity = 1.5
            )
        )
    }

    @Test
    fun resolveTrend_sensitiveMakesTrendReactFaster() {
        // rate 0.045: with standard (1.0) → STABLE (0.045 < 0.06)
        // with sensitive (0.7) → RISING_SLOWLY (0.045 > 0.06 * 0.7 = 0.042)
        assertEquals(
            TrendArrow.STABLE,
            GlucoseAggregator.resolveTrend(
                TrendArrow.UNKNOWN, computedRate = 0.045, sensitivity = 1.0
            )
        )
        assertEquals(
            TrendArrow.RISING_SLOWLY,
            GlucoseAggregator.resolveTrend(
                TrendArrow.UNKNOWN, computedRate = 0.045, sensitivity = 0.7
            )
        )
    }

    @Test
    fun computeSmoothedRate_returnsNullForShortHistory() {
        assertNull(GlucoseAggregator.computeSmoothedRate(
            listOf(
                GlucoseHistoryPoint(1000, 5.0),
                GlucoseHistoryPoint(2000, 5.5),
                GlucoseHistoryPoint(3000, 6.0)
            ),
            windowMinutes = 5
        ))
    }

    @Test
    fun computeSmoothedRate_averagesNoiseFromBothGroups() {
        // 6 readings: older 3 avg = 6.0, recent 3 avg = 6.0 (despite noise)
        // Rate should be ~0 (flat)
        val history = listOf(
            GlucoseHistoryPoint(0, 5.7),
            GlucoseHistoryPoint(60, 6.2),
            GlucoseHistoryPoint(120, 6.1),   // older avg = 6.0
            GlucoseHistoryPoint(300, 5.8),   // 5 min later
            GlucoseHistoryPoint(360, 6.3),
            GlucoseHistoryPoint(420, 5.9)    // recent avg = 6.0
        )
        val rate = GlucoseAggregator.computeSmoothedRate(history, windowMinutes = 5)!!
        assertEquals(0.0, rate, 0.01)
    }

    @Test
    fun computeSmoothedRate_detectsRealTrendThroughNoise() {
        // Clear upward trend with noise: each group averages higher
        val history = listOf(
            GlucoseHistoryPoint(0, 5.0),
            GlucoseHistoryPoint(60, 5.3),
            GlucoseHistoryPoint(120, 4.9),   // older avg ≈ 5.07
            GlucoseHistoryPoint(300, 6.0),
            GlucoseHistoryPoint(360, 6.4),
            GlucoseHistoryPoint(420, 5.8)    // recent avg ≈ 6.07
        )
        val rate = GlucoseAggregator.computeSmoothedRate(history, windowMinutes = 5)!!
        // ~1.0 mmol/L change over ~5 min → rate ≈ 0.2 mmol/L/min
        assertEquals(0.2, rate, 0.05)
    }

    @Test
    fun resolveTrend_fallsBackToDeltaWhenRateIsNull() {
        assertEquals(
            TrendArrow.RISING,
            GlucoseAggregator.resolveTrend(
                snapshotTrend = TrendArrow.UNKNOWN,
                computedDelta = 1.5,
                computedRate = null
            )
        )
    }

    @Test
    fun computeRatePerMinute_returnsNullForEmptyHistory() {
        assertNull(GlucoseAggregator.computeRatePerMinute(emptyList()))
    }

    @Test
    fun computeRatePerMinute_returnsNullForSinglePoint() {
        assertNull(GlucoseAggregator.computeRatePerMinute(
            listOf(GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0))
        ))
    }

    @Test
    fun computeRatePerMinute_returnsNullForIdenticalTimestamps() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 1_000L, glucoseMmol = 6.0)
        )
        assertNull(GlucoseAggregator.computeRatePerMinute(history))
    }

    @Test
    fun computeRatePerMinute_oneMinuteSpacing_returnsRawDelta() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 0L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 60L, glucoseMmol = 5.5) // +0.5 over 1 min
        )
        assertEquals(0.5, GlucoseAggregator.computeRatePerMinute(history)!!, 0.0001)
    }

    @Test
    fun computeRatePerMinute_fiveMinuteSpacing_normalisesToPerMinute() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 0L, glucoseMmol = 5.0),
            GlucoseHistoryPoint(timestamp = 300L, glucoseMmol = 7.5) // +2.5 over 5 min => 0.5/min
        )
        assertEquals(0.5, GlucoseAggregator.computeRatePerMinute(history)!!, 0.0001)
    }

    @Test
    fun computeRatePerMinute_supportsNegativeRate() {
        val history = listOf(
            GlucoseHistoryPoint(timestamp = 0L, glucoseMmol = 7.0),
            GlucoseHistoryPoint(timestamp = 60L, glucoseMmol = 6.0) // -1.0 over 1 min
        )
        assertEquals(-1.0, GlucoseAggregator.computeRatePerMinute(history)!!, 0.0001)
    }
}
