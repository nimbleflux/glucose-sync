package com.nimbleflux.glucosesync.shared.domain

data class GlucoseSnapshot(
    val glucose: Double?,
    val timestamp: Long,
    val trend: TrendArrow,
    val unit: String,
    val sensorActive: Boolean,
    val history: List<GlucoseHistoryPoint> = emptyList(),
    val iob: Double? = null,
    val basalRate: Double? = null,
    val lastBolus: Double? = null,
    val lastBolusTime: Long? = null,
    val remainingDose: Double? = null,
    val batteryPercent: Double? = null,
    val delta: Double? = null,
    val highThreshold: Double? = null,
    val lowThreshold: Double? = null,
    val timeInRange: Double? = null,
    val averageGlucose: Double? = null
) {
    val isStale: Boolean
        get() = if (timestamp == 0L) true else System.currentTimeMillis() / 1000 - timestamp > 600
}
