package com.nimbleflux.glucosesync.shared.domain

data class ProviderInfo(
    val displayName: String,
    val sensorActive: Boolean = false,
    val batteryPercent: Int? = null,
    val serial: String? = null
)
