package com.nimbleflux.glucosesync.shared.provider.libre

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class LibreTrendMappingTest {

    private val provider = LibreLinkUpProvider(android.app.Application())

    /**
     * LibreLinkUp trend arrows use Abbott's 5-state scale where a LOW value
     * means falling and a HIGH value means rising — the reverse of Dexcom.
     * Verified against nightscout-librelink-up's mapTrendArrow():
     *   1 = SingleDown     2 = FortyFiveDown   3 = Flat
     *   4 = FortyFiveUp    5 = SingleUp        0/else = unknown
     */
    @Test
    fun mapTrend_fullServerRange_1_through_5() {
        assertEquals(TrendArrow.FALLING_RAPIDLY, provider.mapTrend(1))
        assertEquals(TrendArrow.FALLING, provider.mapTrend(2))
        assertEquals(TrendArrow.STABLE, provider.mapTrend(3))
        assertEquals(TrendArrow.RISING, provider.mapTrend(4))
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend(5))
    }

    @Test
    fun mapTrend_nullAndOutOfRange_yieldUnknown() {
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(null))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(0))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(6))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(7))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(8))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(-1))
    }
}
