package com.nimbleflux.glucosesync.wear.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatsTest {

    private val nowSec = 1_000_000L

    /** Readings every 5 min ending at [nowSec], covering [hours] hours. */
    private fun history(hours: Double, glucose: (i: Int) -> Double): List<Pair<Long, Double>> {
        val count = (hours * 12).toInt() + 1
        return (0 until count).map { i ->
            (nowSec - (count - 1 - i) * 300L) to glucose(i)
        }
    }

    private val flatHistory = history(24.0) { 5.5 }
    private val fallback = WatchWindowStats(timeInRange = 0.25, averageGlucose = 9.9, coveredWindow = false)

    @Test
    fun coveredWindow_computesLocallyOverWindowOnly() {
        // History spans 24h; the 6h window must only use the newest 6h.
        val subject = history(24.0) { if (it < 36) 20.0 else 5.0 } // old = high, recent = low
        val stats = computeWindowStats(
            subject, windowHours = 6, lowThreshold = 3.9, highThreshold = 10.0,
            fallback = fallback, nowSec = nowSec
        )
        assertTrue(stats!!.coveredWindow)
        assertEquals(1.0, stats.timeInRange!!, 0.0001)      // window contains only 5.0 readings
        assertEquals(5.0, stats.averageGlucose!!, 0.0001)   // not 9.9 from the fallback
    }

    @Test
    fun windowBoundaryIsInclusive() {
        // Oldest point exactly at the window start counts as covered and is
        // included in the computation.
        val subject = listOf(nowSec - 6 * 3600L to 5.0, nowSec - 3600L to 5.0, nowSec to 5.0)
        val stats = computeWindowStats(
            subject, windowHours = 6, lowThreshold = 3.9, highThreshold = 10.0,
            fallback = fallback, nowSec = nowSec
        )
        assertTrue(stats!!.coveredWindow)
        assertEquals(1.0, stats.timeInRange!!, 0.0001)
    }

    @Test
    fun uncoveredWindow_fallsBackToPhoneStats() {
        // Only 2h of stored history cannot cover a 24h window.
        val subject = history(2.0) { 5.5 }
        val stats = computeWindowStats(
            subject, windowHours = 24, lowThreshold = 3.9, highThreshold = 10.0,
            fallback = fallback, nowSec = nowSec
        )
        assertEquals(fallback, stats)
        assertFalse(stats!!.coveredWindow)
    }

    @Test
    fun emptyHistory_fallsBackToPhoneStats() {
        val stats = computeWindowStats(
            emptyList(), windowHours = 24, lowThreshold = 3.9, highThreshold = 10.0,
            fallback = fallback, nowSec = nowSec
        )
        assertEquals(fallback, stats)
    }

    @Test
    fun emptyHistoryWithoutFallback_returnsNull() {
        val stats = computeWindowStats(
            emptyList(), windowHours = 24, lowThreshold = 3.9, highThreshold = 10.0,
            fallback = null, nowSec = nowSec
        )
        assertNull(stats)
    }

    @Test
    fun inRangeBoundsAreInclusive() {
        val subject = history(3.0) { if (it % 2 == 0) 3.9 else 10.0 } // both exactly at thresholds
        val stats = computeWindowStats(
            subject, windowHours = 3, lowThreshold = 3.9, highThreshold = 10.0,
            fallback = null, nowSec = nowSec
        )
        assertTrue(stats!!.coveredWindow)
        assertEquals(1.0, stats.timeInRange!!, 0.0001)
    }
}
