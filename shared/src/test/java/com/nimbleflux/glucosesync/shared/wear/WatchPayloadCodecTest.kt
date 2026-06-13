package com.nimbleflux.glucosesync.shared.wear

import com.google.android.gms.wearable.DataMap
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WatchPayloadCodecTest {

    private val fullPayload = WatchPayload(
        glucose = 6.4,
        timestamp = 1_700_000_000L,
        trend = "\u2191",
        unit = "mmol/L",
        iob = 1.25,
        delta = 0.4,
        batteryPercent = 0.83,
        basalRate = 0.9,
        lastBolus = 2.5,
        lastBolusTime = 1_699_999_000L,
        remainingDose = 120.0,
        highThreshold = 10.0,
        lowThreshold = 3.9,
        timeInRange = 0.82,
        averageGlucose = 6.1,
        history = listOf(
            GlucoseHistoryPoint(timestamp = 1_699_999_400L, glucoseMmol = 5.9),
            GlucoseHistoryPoint(timestamp = 1_699_999_700L, glucoseMmol = 6.0),
            GlucoseHistoryPoint(timestamp = 1_700_000_000L, glucoseMmol = 6.4)
        )
    )

    @Test
    fun roundTrip_preservesAllScalarFields() {
        val dataMap = WatchPayloadCodec.toDataMap(fullPayload)
        val decoded = WatchPayloadCodec.fromDataMap(dataMap)

        assertNotNull(decoded)
        val d = decoded!!
        assertEquals(fullPayload.glucose, d.glucose, 0.0001)
        assertEquals(fullPayload.timestamp, d.timestamp)
        assertEquals(fullPayload.trend, d.trend)
        assertEquals(fullPayload.unit, d.unit)
        assertEquals(fullPayload.iob!!, d.iob!!, 0.0001)
        assertEquals(fullPayload.delta!!, d.delta!!, 0.0001)
        assertEquals(fullPayload.batteryPercent!!, d.batteryPercent!!, 0.0001)
        assertEquals(fullPayload.basalRate!!, d.basalRate!!, 0.0001)
        assertEquals(fullPayload.lastBolus!!, d.lastBolus!!, 0.0001)
        assertEquals(fullPayload.lastBolusTime!!, d.lastBolusTime!!)
        assertEquals(fullPayload.remainingDose!!, d.remainingDose!!, 0.0001)
        assertEquals(fullPayload.highThreshold!!, d.highThreshold!!, 0.0001)
        assertEquals(fullPayload.lowThreshold!!, d.lowThreshold!!, 0.0001)
        assertEquals(fullPayload.timeInRange!!, d.timeInRange!!, 0.0001)
        assertEquals(fullPayload.averageGlucose!!, d.averageGlucose!!, 0.0001)
    }

    @Test
    fun roundTrip_preservesHistoryArrays() {
        val dataMap = WatchPayloadCodec.toDataMap(fullPayload)
        val decoded = WatchPayloadCodec.fromDataMap(dataMap)!!
        assertEquals(fullPayload.history.size, decoded.history.size)
        fullPayload.history.zip(decoded.history).forEach { (expected, actual) ->
            assertEquals(expected.timestamp, actual.timestamp)
            assertEquals(expected.glucoseMmol, actual.glucoseMmol, 0.0001)
        }
    }

    @Test
    fun decode_returnsNullWhenGlucoseKeyMissing() {
        val dataMap = DataMap().apply {
            putLong("timestamp", 1L)
        }
        assertNull(WatchPayloadCodec.fromDataMap(dataMap))
    }

    @Test
    fun decode_handlesAbsentOptionalFields() {
        val dataMap = DataMap().apply {
            putDouble("glucose", 5.5)
            putLong("timestamp", 100L)
            putString("trend", "\u2192")
            putString("unit", "mmol/L")
        }
        val decoded = WatchPayloadCodec.fromDataMap(dataMap)
        assertNotNull(decoded)
        val d = decoded!!
        assertEquals(5.5, d.glucose, 0.0001)
        assertEquals(100L, d.timestamp)
        assertNull(d.iob)
        assertNull(d.delta)
        assertNull(d.batteryPercent)
        assertNull(d.basalRate)
        assertNull(d.lastBolus)
        assertNull(d.lastBolusTime)
        assertNull(d.remainingDose)
        assertNull(d.highThreshold)
        assertNull(d.lowThreshold)
        assertNull(d.timeInRange)
        assertNull(d.averageGlucose)
        assertEquals(emptyList<GlucoseHistoryPoint>(), d.history)
    }

    @Test
    fun decode_ignoresMismatchedHistoryArrays() {
        val dataMap = DataMap().apply {
            putDouble("glucose", 5.5)
            putLong("timestamp", 100L)
            putString("trend", "\u2192")
            putString("unit", "mmol/L")
            putLongArray("history_ts", longArrayOf(1L, 2L, 3L))
            putFloatArray("history_gl", floatArrayOf(5.0f, 6.0f)) // mismatched size
        }
        val decoded = WatchPayloadCodec.fromDataMap(dataMap)!!
        assertEquals(emptyList<GlucoseHistoryPoint>(), decoded.history)
    }
}
