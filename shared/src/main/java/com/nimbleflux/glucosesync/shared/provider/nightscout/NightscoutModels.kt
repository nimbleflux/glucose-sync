package com.nimbleflux.glucosesync.shared.provider.nightscout

import kotlinx.serialization.Serializable

@Serializable
data class NightscoutEntry(
    val _id: String? = null,
    val date: Long? = null,        // epoch milliseconds
    val sgv: Double? = null,        // mg/dL
    val direction: String? = null,  // "Flat", "SingleUp", "DoubleUp", "FortyFiveUp", ...
    val delta: Double? = null,      // change since previous reading, mg/dL
    val type: String? = null,
    val noise: Double? = null
)

@Serializable
data class NightscoutStatus(
    val status: String? = null,
    val name: String? = null,
    val version: String? = null,
    val apiEnabled: Boolean? = null,
    val settings: NightscoutSettings? = null
)

@Serializable
data class NightscoutSettings(
    val units: String? = null,
    val thresholds: NightscoutThresholds? = null
)

@Serializable
data class NightscoutThresholds(
    val bgHigh: Double? = null,
    val bgTargetTop: Double? = null,
    val bgTargetBottom: Double? = null,
    val bgLow: Double? = null
)
