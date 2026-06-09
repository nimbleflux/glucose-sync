package com.nimbleflux.glucosesync.shared.provider.libre

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LibreLoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LibreLoginResponse(
    val status: Int = -1,
    val data: LibreLoginData? = null
)

@Serializable
data class LibreLoginData(
    val user: LibreUser? = null,
    val authTicket: LibreAuthTicket? = null,
    val redirect: Boolean = false,
    val region: String? = null,
    val trustedDeviceToken: String? = null
)

@Serializable
data class LibreUser(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val country: String = "",
    val uom: Int = 0,
    val accountType: String = ""
)

@Serializable
data class LibreAuthTicket(
    val token: String = "",
    val expires: Long = 0,
    val duration: Long = 0
)

@Serializable
data class LibreConnectionsResponse(
    val status: Int = -1,
    val data: List<LibreConnection>? = null
)

@Serializable
data class LibreConnection(
    val id: String = "",
    val patientId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val country: String = "",
    val status: Int = 0,
    val targetLow: Int = 70,
    val targetHigh: Int = 180,
    val uom: Int = 0,
    val sensor: LibreSensor? = null,
    val glucoseMeasurement: LibreGlucoseMeasurement? = null,
    val created: Long = 0
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val sensorActive: Boolean get() = sensor?.s == true
    val displayUnit: String get() = if (uom == 1) "mg/dL" else "mmol/L"
}

@Serializable
data class LibreSensor(
    val deviceId: String? = null,
    val sn: String? = null,
    val a: Long? = null,
    val w: Int? = null,
    val pt: Int? = null,
    val s: Boolean? = null,
    val lj: Boolean? = null
)

@Serializable
data class LibreGlucoseMeasurement(
    val FactoryTimestamp: String? = null,
    val Timestamp: String? = null    ,
    val type: Int? = null,
    val ValueInMgPerDl: Int? = null,
    val Value: Double? = null,
    val TrendArrow: Int? = null,
    val MeasurementColor: Int? = null,
    val GlucoseUnits: Int? = null,
    val isHigh: Boolean? = null,
    val isLow: Boolean? = null
)

@Serializable
data class LibreGraphResponse(
    val status: Int = -1,
    val data: LibreGraphData? = null
)

@Serializable
data class LibreGraphData(
    val connection: LibreConnection? = null,
    val graphData: List<LibreGlucoseMeasurement>? = null
)
