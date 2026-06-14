package com.nimbleflux.glucosesync.shared.provider.libre

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class LibreTrendMappingTest {

    private val provider = LibreLinkUpProvider(android.app.Application())

    @Test
    fun mapTrend_publishedServerValues_1_through_5() {
        // The five values LibreLinkUp documents today.
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend(1))
        assertEquals(TrendArrow.RISING, provider.mapTrend(2))
        assertEquals(TrendArrow.STABLE, provider.mapTrend(3))
        assertEquals(TrendArrow.FALLING, provider.mapTrend(4))
        assertEquals(TrendArrow.FALLING_RAPIDLY, provider.mapTrend(5))
    }

    @Test
    fun mapTrend_defensiveValues_6_and_7_mapToSlowlyVariants() {
        // Not documented today; mapped defensively in case a future API
        // revision emits them.
        assertEquals(TrendArrow.RISING_SLOWLY, provider.mapTrend(6))
        assertEquals(TrendArrow.FALLING_SLOWLY, provider.mapTrend(7))
    }

    @Test
    fun mapTrend_nullAndOutOfRange_yieldUnknown() {
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(null))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(0))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(8))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(-1))
    }
}
