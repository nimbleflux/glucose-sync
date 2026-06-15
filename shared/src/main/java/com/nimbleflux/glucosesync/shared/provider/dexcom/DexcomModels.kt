package com.nimbleflux.glucosesync.shared.provider.dexcom

import kotlinx.serialization.Serializable

@Serializable
data class DexcomLoginRequest(
    val accountName: String,
    val password: String,
    val applicationId: String
)

@Serializable
data class DexcomGlucoseRequest(
    val sessionId: String,
    val minutes: Int,
    val maxCount: Int
)

/**
 * Dexcom Share ServerEGV format. Read from
 * Publisher/ReadPublisherLatestGlucoseValues.
 *
 * @param DT  Display Time, Microsoft AJAX format "/Date(<epochMs>)/"
 * @param ST  System Time, same format
 * @param Trend  0=None, 1=DoubleUp, ..., 7=DoubleDown
 * @param Value  Glucose in mg/dL
 */
@Serializable
data class DexcomReading(
    val DT: String? = null,
    val ST: String? = null,
    val Trend: Int? = null,
    val Value: Int? = null
)
