package com.nimbleflux.medtrumwatch.shared.domain

data class GlucoseHistoryPoint(
    val timestamp: Long,
    val glucoseMmol: Double
)
