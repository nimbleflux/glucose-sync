package com.nimbleflux.glucosesync.shared.provider.dexcom

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Test

class DexcomTrendMappingTest {

    private val provider = DexcomProvider(android.app.Application())

    @Test
    fun mapTrend_canonicalDexcomValues_1_through_7() {
        // Same mapping as Libre: 1=DoubleUp through 7=DoubleDown
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend(1))
        assertEquals(TrendArrow.RISING, provider.mapTrend(2))
        assertEquals(TrendArrow.RISING_SLOWLY, provider.mapTrend(3))
        assertEquals(TrendArrow.STABLE, provider.mapTrend(4))
        assertEquals(TrendArrow.FALLING_SLOWLY, provider.mapTrend(5))
        assertEquals(TrendArrow.FALLING, provider.mapTrend(6))
        assertEquals(TrendArrow.FALLING_RAPIDLY, provider.mapTrend(7))
    }

    @Test
    fun mapTrend_noneAndOutOfRange_yieldUnknown() {
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(0))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(null))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(8))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(-1))
    }

    @Test
    fun parseMsDate_extractsEpochMillis() {
        assertEquals(1700000000000L, provider.parseMsDate("/Date(1700000000000-0000)/"))
        assertEquals(1700000000000L, provider.parseMsDate("/Date(1700000000000)/"))
        assertEquals(0L, provider.parseMsDate("not-a-date"))
        assertEquals(0L, provider.parseMsDate(""))
    }

    @Test
    fun parseMsDate_handlesNegativeOffset() {
        assertEquals(1700000000000L, provider.parseMsDate("/Date(1700000000000-0800)/"))
    }
}
