package com.nimbleflux.medtrumwatch.shared.domain

enum class TrendArrow(val symbol: String) {
    RISING_RAPIDLY("\u2B06\uFE0F"),
    RISING("\u2197\uFE0F"),
    RISING_SLOWLY("\u2197"),
    STABLE("\u2192"),
    FALLING_SLOWLY("\u2198"),
    FALLING("\u2198\uFE0F"),
    FALLING_RAPIDLY("\u2B07\uFE0F"),
    UNKNOWN("?");

    companion object {
        fun fromDelta(delta: Double): TrendArrow = when {
            delta > 2.0 -> RISING_RAPIDLY
            delta > 1.0 -> RISING
            delta > 0.3 -> RISING_SLOWLY
            delta < -2.0 -> FALLING_RAPIDLY
            delta < -1.0 -> FALLING
            delta < -0.3 -> FALLING_SLOWLY
            else -> STABLE
        }

        fun fromRate(ratePerMinute: Double): TrendArrow = when {
            ratePerMinute > 0.10 -> RISING_RAPIDLY
            ratePerMinute > 0.06 -> RISING
            ratePerMinute > 0.02 -> RISING_SLOWLY
            ratePerMinute < -0.10 -> FALLING_RAPIDLY
            ratePerMinute < -0.06 -> FALLING
            ratePerMinute < -0.02 -> FALLING_SLOWLY
            else -> STABLE
        }
    }
}
