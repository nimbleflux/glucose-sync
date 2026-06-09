package com.nimbleflux.glucosesync.shared.domain

data class GlucoseHistoryPoint(
    val timestamp: Long,
    val glucoseMmol: Double
)
