package com.nimbleflux.glucosesync.shared.provider.nightscout

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Test

class NightscoutTrendMappingTest {

    private val provider = NightscoutProvider(android.app.Application())

    @Test
    fun mapTrend_canonicalNightscoutDirections() {
        // Values are canonical per the Nightscout spec.
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend("DoubleUp"))
        assertEquals(TrendArrow.RISING, provider.mapTrend("SingleUp"))
        assertEquals(TrendArrow.RISING_SLOWLY, provider.mapTrend("FortyFiveUp"))
        assertEquals(TrendArrow.STABLE, provider.mapTrend("Flat"))
        assertEquals(TrendArrow.FALLING_SLOWLY, provider.mapTrend("FortyFiveDown"))
        assertEquals(TrendArrow.FALLING, provider.mapTrend("SingleDown"))
        assertEquals(TrendArrow.FALLING_RAPIDLY, provider.mapTrend("DoubleDown"))
    }

    @Test
    fun mapTrend_isCaseInsensitive() {
        assertEquals(TrendArrow.STABLE, provider.mapTrend("flat"))
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend("doubleup"))
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapTrend("DOUBLEUP"))
    }

    @Test
    fun mapTrend_nullAndUnknownYieldUnknown() {
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(null))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend(""))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend("NOT_VALID"))
        assertEquals(TrendArrow.UNKNOWN, provider.mapTrend("none"))
    }
}
