package com.nimbleflux.medtrumwatch.shared.provider.libre

import com.nimbleflux.medtrumwatch.shared.provider.libre.LibreTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibreTimestampTest {

    @Test
    fun parse_typicalTimestamp() {
        val result = LibreTimestamp.parseToEpochSeconds("6/9/2026 10:30:00 AM")
        assert(result > 0L) { "Should parse to a positive epoch second" }
    }

    @Test
    fun parse_withPaddedMonth() {
        val result = LibreTimestamp.parseToEpochSeconds("06/09/2026 10:30:00 AM")
        assert(result > 0L)
    }

    @Test
    fun parse_pmTime() {
        val am = LibreTimestamp.parseToEpochSeconds("6/9/2026 10:30:00 AM")
        val pm = LibreTimestamp.parseToEpochSeconds("6/9/2026 10:30:00 PM")
        assert(pm > am) { "PM should be 12 hours after AM" }
        assertEquals(12 * 3600L, pm - am)
    }

    @Test
    fun parse_midnight() {
        val result = LibreTimestamp.parseToEpochSeconds("6/9/2026 12:00:00 AM")
        assert(result > 0L)
    }

    @Test
    fun parse_noon() {
        val result = LibreTimestamp.parseToEpochSeconds("6/9/2026 12:00:00 PM")
        assert(result > 0L)
    }

    @Test
    fun parse_nullReturnsZero() {
        assertEquals(0L, LibreTimestamp.parseToEpochSeconds(null))
    }

    @Test
    fun parse_blankReturnsZero() {
        assertEquals(0L, LibreTimestamp.parseToEpochSeconds(""))
        assertEquals(0L, LibreTimestamp.parseToEpochSeconds("   "))
    }

    @Test
    fun parse_invalidReturnsZero() {
        assertEquals(0L, LibreTimestamp.parseToEpochSeconds("not a date"))
    }

    @Test
    fun parse_consistentResults() {
        val ts1 = LibreTimestamp.parseToEpochSeconds("6/9/2026 2:30:00 PM")
        val ts2 = LibreTimestamp.parseToEpochSeconds("6/9/2026 2:30:00 PM")
        assertEquals(ts1, ts2)
    }

    @Test
    fun parse_differentDatesDifferentResults() {
        val d1 = LibreTimestamp.parseToEpochSeconds("1/1/2026 12:00:00 PM")
        val d2 = LibreTimestamp.parseToEpochSeconds("1/2/2026 12:00:00 PM")
        assert(d2 > d1) { "Later date should have larger epoch" }
    }
}
