package com.nimbleflux.glucosesync.shared.domain

enum class TrendArrow(val symbol: String) {
    RISING_RAPIDLY("\u2B06"),
    RISING("\u2191"),
    RISING_SLOWLY("\u2197"),
    STABLE("\u2192"),
    FALLING_SLOWLY("\u2198"),
    FALLING("\u2193"),
    FALLING_RAPIDLY("\u2B07"),
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

        /**
         * Map a per-minute glucose rate (mmol/L/min) to a trend arrow.
         * Thresholds aligned with the Dexcom CGM standard, scaled by
         * [sensitivity]:
         *   > 1.0 = conservative (wider STABLE band, needs more change)
         *   1.0   = standard (Dexcom default)
         *   < 1.0 = sensitive (narrower STABLE band, reacts faster)
         *
         * Base thresholds (sensitivity = 1.0):
         *   RISING_RAPIDLY:  > 0.17 mmol/L/min (3.0+ mg/dL/min)
         *   RISING:          > 0.11 mmol/L/min (2.0-3.0 mg/dL/min)
         *   RISING_SLOWLY:   > 0.06 mmol/L/min (1.0-2.0 mg/dL/min)
         *   STABLE:          ±0.06 mmol/L/min (±1.0 mg/dL/min)
         */
        fun fromRate(ratePerMinute: Double, sensitivity: Double = 1.0): TrendArrow {
            val s = sensitivity.coerceIn(0.3, 3.0)
            return when {
                ratePerMinute > 0.17 * s -> RISING_RAPIDLY
                ratePerMinute > 0.11 * s -> RISING
                ratePerMinute > 0.06 * s -> RISING_SLOWLY
                ratePerMinute < -0.17 * s -> FALLING_RAPIDLY
                ratePerMinute < -0.11 * s -> FALLING
                ratePerMinute < -0.06 * s -> FALLING_SLOWLY
                else -> STABLE
            }
        }
    }
}
