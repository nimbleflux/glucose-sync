package com.nimbleflux.glucosesync.shared.provider.libre

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class LibreTrendMappingTest {

    private val provider = LibreLinkUpProvider(android.app.Application())

    /**
     * LibreLinkUp trend arrow values (community-documented):
     *   1 = rising rapidly   2 = rising         3 = rising slowly
     *   4 = stable           5 = falling slowly 6 = falling
     *   7 = falling rapidly  0/else = unknown
     */
    @Test
    fun mapTrend_fullServerRange_1_through_7() {
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend(1))
        assertEquals(TrendArrow.RISING, provider.mapTrend(2))
        assertEquals(TrendArrow.RISING_SLOWLY, provider.mapTrend(3))
        assertEquals(TrendArrow.STABLE, provider.mapTrend(4))
        assertEquals(TrendArrow.FALLING_SLOWLY, provider.mapTrend(5))
        assertEquals(TrendArrow.FALLING, provider.mapTrend(6))
        assertEquals(TrendArrow.FALLING_RAPIDLY, provider.mapTrend(7))
    }

    @Test
    fun mapTrend_nullAndOutOfRange_yieldUnknown() {
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(null))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(0))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(8))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(-1))
    }
}
