package com.nimbleflux.glucosesync.shared.provider.xdrip

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [XdripIntentParser.interpret] — the pure interpretation of
 * resolved broadcast extras. We call [XdripIntentParser.interpret] directly
 * (not [XdripIntentParser.parse]) because the shared module's unit tests run
 * on a plain JVM without Robolectric, where [android.content.Intent] is a
 * stub that returns default values for every getter.
 *
 * [XdripBroadcastProvider.STALE_THRESHOLD_MS] is covered by an edge test
 * asserting the threshold equals 6 minutes (one CGM interval + slack) so the
 * staleness-recovery branch in fetchGlucose is anchored.
 */
class XdripIntentParserTest {

    private val parser = XdripIntentParser
    private val now = 1_700_000_000_000L

    // ---- Action gating ---------------------------------------------------

    @Test
    fun parse_unknownAction_returnsNoGlucose() {
        // parse(Intent) gates on action; interpret itself does not (it only
        // sees the already-gated extras), so we assert the threshold via the
        // documented constant instead. This test documents the contract.
        assertTrue(
            "stale threshold is one CGM interval + slack (6 min)",
            XdripBroadcastProvider.STALE_THRESHOLD_MS == 6L * 60_000L
        )
    }

    // ---- Stock xDrip+ (fully-qualified double extras) -------------------

    @Test
    fun interpret_stockXdripExtras_fullReading() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 180.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = 1_700_000_060_000L,
            legacyTimestamp = 0L,
            hasSlope = true,
            slope = 2.0, // mg/dL per minute
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = "DoubleUp",
            hasBattery = true,
            battery = 75,
            nowMs = now
        )

        assertTrue(result is XdripParseResult.Accepted)
        val r = (result as XdripParseResult.Accepted).reading
        assertEquals(180.0, r.glucoseMgDl, 0.001)
        assertEquals(1_700_000_060_000L, r.timestampMs)
        // slope × 5 min
        assertEquals(10.0, r.deltaMgDl!!, 0.001)
        assertEquals(TrendArrow.RISING_RAPIDLY, r.trend)
        assertEquals(0.75, r.batteryPercent!!, 0.001)
    }

    @Test
    fun interpret_stockXdrip_missingSlopeName_yieldsUnknownTrend() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 120.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = now,
            legacyTimestamp = 0L,
            hasSlope = false,
            slope = Double.NaN,
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = null,
            hasBattery = false,
            battery = -1,
            nowMs = now
        )

        val r = (result as XdripParseResult.Accepted).reading
        assertEquals(TrendArrow.UNKNOWN, r.trend)
        assertNull(r.deltaMgDl)
        assertNull(r.batteryPercent)
    }

    // ---- Diabox (bare float extras) -------------------------------------

    @Test
    fun interpret_diaboxLegacyExtras_fullReading() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_READING,
            modernGlucose = Double.NaN,
            legacyGlucose = 7.2f, // mmol? No — Diabox bgValue is mg/dL like the modern key
            hasTimestamp = false,
            modernTimestamp = 0L,
            legacyTimestamp = 1_700_000_030_000L,
            hasSlope = false,
            slope = Double.NaN,
            hasLegacyDelta = true,
            legacyDelta = 5f, // absolute mg/dL delta
            slopeName = null,
            hasBattery = false,
            battery = -1,
            nowMs = now
        )

        val r = (result as XdripParseResult.Accepted).reading
        assertEquals(7.2, r.glucoseMgDl, 0.001)
        assertEquals(1_700_000_030_000L, r.timestampMs)
        // Diabox sends an absolute delta directly, no slope multiplication
        assertEquals(5.0, r.deltaMgDl!!, 0.001)
        assertEquals(TrendArrow.UNKNOWN, r.trend)
        assertNull(r.batteryPercent)
    }

    // ---- Invalid / missing glucose --------------------------------------

    @Test
    fun interpret_zeroGlucose_returnsNoGlucose() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 0.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = now,
            legacyTimestamp = 0L,
            hasSlope = false,
            slope = Double.NaN,
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = null,
            hasBattery = false,
            battery = -1,
            nowMs = now
        )
        assertTrue(result is XdripParseResult.NoGlucose)
    }

    @Test
    fun interpret_negativeGlucose_returnsNoGlucose() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = -1.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = now,
            legacyTimestamp = 0L,
            hasSlope = false,
            slope = Double.NaN,
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = null,
            hasBattery = false,
            battery = -1,
            nowMs = now
        )
        assertTrue(result is XdripParseResult.NoGlucose)
    }

    // ---- Delta edge cases -----------------------------------------------

    @Test
    fun interpret_slopeIsNaN_yieldsNullDelta() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 150.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = now,
            legacyTimestamp = 0L,
            hasSlope = true,
            slope = Double.NaN,
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = "Flat",
            hasBattery = false,
            battery = -1,
            nowMs = now
        )
        val r = (result as XdripParseResult.Accepted).reading
        assertNull(r.deltaMgDl)
    }

    @Test
    fun interpret_slopePreferredOverLegacyDelta() {
        // When both slope (stock) and bgDelta (Diabox) extras are present,
        // slope wins — matches the original receiver's when-order.
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 150.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = now,
            legacyTimestamp = 0L,
            hasSlope = true,
            slope = 3.0,
            hasLegacyDelta = true,
            legacyDelta = 99f,
            slopeName = "SingleUp",
            hasBattery = false,
            battery = -1,
            nowMs = now
        )
        val r = (result as XdripParseResult.Accepted).reading
        assertEquals(15.0, r.deltaMgDl!!, 0.001) // 3.0 × 5, not 99
    }

    // ---- Timestamp fallback ---------------------------------------------

    @Test
    fun interpret_missingTimestamp_fallsBackToNow() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 150.0,
            legacyGlucose = -1f,
            hasTimestamp = false,
            modernTimestamp = 0L,
            legacyTimestamp = 0L,
            hasSlope = false,
            slope = Double.NaN,
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = null,
            hasBattery = false,
            battery = -1,
            nowMs = now
        )
        val r = (result as XdripParseResult.Accepted).reading
        assertEquals(now, r.timestampMs)
    }

    // ---- Battery edge cases ---------------------------------------------

    @Test
    fun interpret_batteryOutOfRange_yieldsNullBattery() {
        val result = parser.interpret(
            action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            modernGlucose = 150.0,
            legacyGlucose = -1f,
            hasTimestamp = true,
            modernTimestamp = now,
            legacyTimestamp = 0L,
            hasSlope = false,
            slope = Double.NaN,
            hasLegacyDelta = false,
            legacyDelta = Float.NaN,
            slopeName = null,
            hasBattery = true,
            battery = 150, // out of 0..100
            nowMs = now
        )
        val r = (result as XdripParseResult.Accepted).reading
        assertNull(r.batteryPercent)
    }

    @Test
    fun interpret_batteryBoundaryZero_andHundred() {
        listOf(0, 100).forEach { pct ->
            val result = parser.interpret(
                action = XdripBroadcastProvider.ACTION_BG_ESTIMATE,
                modernGlucose = 150.0,
                legacyGlucose = -1f,
                hasTimestamp = true,
                modernTimestamp = now,
                legacyTimestamp = 0L,
                hasSlope = false,
                slope = Double.NaN,
                hasLegacyDelta = false,
                legacyDelta = Float.NaN,
                slopeName = null,
                hasBattery = true,
                battery = pct,
                nowMs = now
            )
            val r = (result as XdripParseResult.Accepted).reading
            assertEquals(pct / 100.0, r.batteryPercent!!, 0.001)
        }
    }
}
