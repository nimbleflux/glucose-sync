package com.nimbleflux.glucosesync.shared.domain

data class GlucoseSnapshot(
    val glucose: Double?,
    val timestamp: Long,
    val trend: TrendArrow,
    val unit: String,
    val sensorActive: Boolean,
    val history: List<GlucoseHistoryPoint> = emptyList()
) {
    val isStale: Boolean
        get() = if (timestamp == 0L) true else System.currentTimeMillis() / 1000 - timestamp > 600
}
