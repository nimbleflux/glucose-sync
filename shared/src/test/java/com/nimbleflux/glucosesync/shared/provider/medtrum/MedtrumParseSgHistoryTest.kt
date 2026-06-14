package com.nimbleflux.glucosesync.shared.provider.medtrum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedtrumParseSgHistoryTest {

    private fun parse(sg: List<kotlinx.serialization.json.JsonElement>) =
        parseSgHistoryStatic(sg).history

    @Test
    fun parseSgHistory_emptyInput_returnsEmpty() {
        assertTrue(parse(emptyList()).isEmpty())
    }

    @Test
    fun parseSgHistory_validEntries_returnsSorted() {
        val sg = listOf(
            JsonArray(listOf(JsonPrimitive(2000.0), JsonPrimitive(5.5))),
            JsonArray(listOf(JsonPrimitive(1000.0), JsonPrimitive(4.0))),
            JsonArray(listOf(JsonPrimitive(3000.0), JsonPrimitive(6.0)))
        )
        val history = parse(sg)
        assertEquals(3, history.size)
        // Should be sorted ascending by timestamp
        assertEquals(1000L, history[0].timestamp)
        assertEquals(2000L, history[1].timestamp)
        assertEquals(3000L, history[2].timestamp)
    }

    @Test
    fun parseSgHistory_deduplicatesByTimestamp() {
        val sg = listOf(
            JsonArray(listOf(JsonPrimitive(1000.0), JsonPrimitive(4.0))),
            JsonArray(listOf(JsonPrimitive(1000.0), JsonPrimitive(5.0))),  // same ts
            JsonArray(listOf(JsonPrimitive(2000.0), JsonPrimitive(6.0)))
        )
        val history = parse(sg)
        assertEquals(2, history.size)
    }

    @Test
    fun parseSgHistory_skipsZeroOrNegativeTimestamps() {
        val sg = listOf(
            JsonArray(listOf(JsonPrimitive(0.0), JsonPrimitive(5.5))),
            JsonArray(listOf(JsonPrimitive(-1.0), JsonPrimitive(5.5))),
            JsonArray(listOf(JsonPrimitive(1000.0), JsonPrimitive(4.0)))
        )
        val history = parse(sg)
        assertEquals(1, history.size)
        assertEquals(1000L, history[0].timestamp)
    }

    @Test
    fun parseSgHistory_skipsZeroOrNegativeGlucose() {
        val sg = listOf(
            JsonArray(listOf(JsonPrimitive(1000.0), JsonPrimitive(0.0))),
            JsonArray(listOf(JsonPrimitive(2000.0), JsonPrimitive(-1.0))),
            JsonArray(listOf(JsonPrimitive(3000.0), JsonPrimitive(5.5)))
        )
        val history = parse(sg)
        assertEquals(1, history.size)
        assertEquals(5.5, history[0].glucoseMmol, 0.0001)
    }

    @Test
    fun parseSgHistory_skipsMalformedEntries() {
        val sg = listOf(
            JsonArray(listOf(JsonPrimitive(1000.0))),          // only 1 element - too small
            JsonArray(listOf()),                                // empty array
            JsonPrimitive("notAnArray"),                        // not an array at all
            JsonArray(listOf(JsonPrimitive(1000.0), JsonPrimitive(5.5)))  // valid
        )
        val history = parse(sg)
        assertEquals(1, history.size)
    }

    @Test
    fun parseSgHistory_handlesLargeDataset() {
        val sg = (1..500).map { i ->
            JsonArray(listOf(JsonPrimitive(i * 60.0), JsonPrimitive(5.0 + i * 0.01)))
        }
        val history = parse(sg)
        assertEquals(500, history.size)
        // Verify sort order
        for (i in 1 until history.size) {
            assert(history[i - 1].timestamp <= history[i].timestamp)
        }
    }
}
