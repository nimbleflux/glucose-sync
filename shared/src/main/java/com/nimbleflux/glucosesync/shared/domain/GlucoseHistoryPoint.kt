package com.nimbleflux.glucosesync.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class GlucoseHistoryPoint(
    val timestamp: Long,
    val glucoseMmol: Double
)
