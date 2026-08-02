package com.nimbleflux.glucosesync.shared.provider.xdrip

import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Test

class XdripTrendMappingTest {

    // mapSlopeName is context-free, so a stub context is fine here (mirrors
    // the Nightscout trend mapping test).
    private val provider = XdripBroadcastProvider(android.app.Application())

    @Test
    fun mapSlopeName_canonicalXdripSlopeNames() {
        // Values are the names emitted by xDrip+'s BroadcastGlucose
        // (com.eveningoutpost.dexdrip.utilitymodels.BroadcastGlucose).
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapSlopeName("DoubleUp"))
        assertEquals(TrendArrow.RISING, provider.mapSlopeName("SingleUp"))
        assertEquals(TrendArrow.RISING_SLOWLY, provider.mapSlopeName("FortyFiveUp"))
        assertEquals(TrendArrow.STABLE, provider.mapSlopeName("Flat"))
        assertEquals(TrendArrow.FALLING_SLOWLY, provider.mapSlopeName("FortyFiveDown"))
        assertEquals(TrendArrow.FALLING, provider.mapSlopeName("SingleDown"))
        assertEquals(TrendArrow.FALLING_RAPIDLY, provider.mapSlopeName("DoubleDown"))
    }

    @Test
    fun mapSlopeName_isCaseInsensitive() {
        assertEquals(TrendArrow.STABLE, provider.mapSlopeName("flat"))
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapSlopeName("doubleup"))
        assertEquals(TrendArrow.RISING_RAPIDLY, provider.mapSlopeName("DOUBLEUP"))
    }

    @Test
    fun mapSlopeName_nullAndUnknownYieldUnknown() {
        // `"9"` is what xDrip+ sends when the user has hidden the slope.
        assertEquals(TrendArrow.UNKNOWN, provider.mapSlopeName(null))
        assertEquals(TrendArrow.UNKNOWN, provider.mapSlopeName(""))
        assertEquals(TrendArrow.UNKNOWN, provider.mapSlopeName("9"))
        assertEquals(TrendArrow.UNKNOWN, provider.mapSlopeName("NOT_VALID"))
    }
}
