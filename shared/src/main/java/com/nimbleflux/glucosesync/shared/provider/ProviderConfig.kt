package com.nimbleflux.glucosesync.shared.provider

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val description: String,
    val authType: AuthType,
    val available: Boolean,
    val icon: String = "",
    /**
     * True when the provider returns TrendArrow.UNKNOWN and lets
     * GlucoseAggregator derive the arrow locally — i.e. when the
     * "Trend sensitivity" setting actually affects the displayed arrow.
     * False for providers whose server trend passes through unchanged.
     */
    val supportsLocalTrend: Boolean = false
)
