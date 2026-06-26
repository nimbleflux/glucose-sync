package com.nimbleflux.glucosesync.shared.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendArrowTest {

    @Test
    fun fromDelta_risingRapidly() {
        assertEquals(TrendArrow.RISING_RAPIDLY, TrendArrow.fromDelta(2.1))
        assertEquals(TrendArrow.RISING_RAPIDLY, TrendArrow.fromDelta(3.0))
    }

    @Test
    fun fromDelta_rising() {
        assertEquals(TrendArrow.RISING, TrendArrow.fromDelta(1.5))
    }

    @Test
    fun fromDelta_risingSlowly() {
        assertEquals(TrendArrow.RISING_SLOWLY, TrendArrow.fromDelta(0.5))
        assertEquals(TrendArrow.RISING_SLOWLY, TrendArrow.fromDelta(1.0))
    }

    @Test
    fun fromDelta_stable() {
        assertEquals(TrendArrow.STABLE, TrendArrow.fromDelta(0.0))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromDelta(0.1))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromDelta(-0.2))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromDelta(0.3))
    }

    @Test
    fun fromDelta_fallingSlowly() {
        assertEquals(TrendArrow.FALLING_SLOWLY, TrendArrow.fromDelta(-0.5))
        assertEquals(TrendArrow.FALLING_SLOWLY, TrendArrow.fromDelta(-1.0))
    }

    @Test
    fun fromDelta_falling() {
        assertEquals(TrendArrow.FALLING, TrendArrow.fromDelta(-1.5))
    }

    @Test
    fun fromDelta_fallingRapidly() {
        assertEquals(TrendArrow.FALLING_RAPIDLY, TrendArrow.fromDelta(-2.1))
        assertEquals(TrendArrow.FALLING_RAPIDLY, TrendArrow.fromDelta(-3.0))
    }

    @Test
    fun fromRate_risingRapidly() {
        assertEquals(TrendArrow.RISING_RAPIDLY, TrendArrow.fromRate(0.18))
        assertEquals(TrendArrow.RISING_RAPIDLY, TrendArrow.fromRate(0.25))
    }

    @Test
    fun fromRate_rising() {
        assertEquals(TrendArrow.RISING, TrendArrow.fromRate(0.12))
        assertEquals(TrendArrow.RISING, TrendArrow.fromRate(0.15))
    }

    @Test
    fun fromRate_risingSlowly() {
        assertEquals(TrendArrow.RISING_SLOWLY, TrendArrow.fromRate(0.07))
        assertEquals(TrendArrow.RISING_SLOWLY, TrendArrow.fromRate(0.10))
    }

    @Test
    fun fromRate_stable() {
        assertEquals(TrendArrow.STABLE, TrendArrow.fromRate(0.0))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromRate(0.03))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromRate(-0.03))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromRate(0.05))
        assertEquals(TrendArrow.STABLE, TrendArrow.fromRate(-0.06))
    }

    @Test
    fun fromRate_fallingSlowly() {
        assertEquals(TrendArrow.FALLING_SLOWLY, TrendArrow.fromRate(-0.07))
        assertEquals(TrendArrow.FALLING_SLOWLY, TrendArrow.fromRate(-0.10))
    }

    @Test
    fun fromRate_falling() {
        assertEquals(TrendArrow.FALLING, TrendArrow.fromRate(-0.12))
        assertEquals(TrendArrow.FALLING, TrendArrow.fromRate(-0.15))
    }

    @Test
    fun fromRate_fallingRapidly() {
        assertEquals(TrendArrow.FALLING_RAPIDLY, TrendArrow.fromRate(-0.18))
        assertEquals(TrendArrow.FALLING_RAPIDLY, TrendArrow.fromRate(-0.25))
    }

    @Test
    fun symbols_areCorrect() {
        assertEquals("\u2B06", TrendArrow.RISING_RAPIDLY.symbol)
        assertEquals("\u2191", TrendArrow.RISING.symbol)
        assertEquals("\u2197", TrendArrow.RISING_SLOWLY.symbol)
        assertEquals("\u2192", TrendArrow.STABLE.symbol)
        assertEquals("\u2198", TrendArrow.FALLING_SLOWLY.symbol)
        assertEquals("\u2193", TrendArrow.FALLING.symbol)
        assertEquals("\u2B07", TrendArrow.FALLING_RAPIDLY.symbol)
        assertEquals("?", TrendArrow.UNKNOWN.symbol)
    }
}
