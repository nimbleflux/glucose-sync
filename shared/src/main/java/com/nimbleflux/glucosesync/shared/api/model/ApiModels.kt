package com.nimbleflux.glucosesync.shared.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(
    val user_name: String,
    val password: String,
    val user_type: String = "P"
)

@Serializable
data class LoginResponse(
    val uid: Long = 0,
    val realname: String = "",
    val username: String = "",
    val error: Int = -1,
    val country: String = "",
    val user_type: String = ""
)

@Serializable
data class StatusResponse(
    val data: StatusData? = null,
    val error: Int = -1
)

@Serializable
data class StatusData(
    val sensor_status: SensorStatus = SensorStatus(),
    val pump_status: PumpStatus = PumpStatus(),
    val chart: ChartData = ChartData(),
    val event_no: Long = 0,
    val data_source: String = ""
)

@Serializable
data class SensorStatus(
    val glucose: Double? = null,
    val updateTime: Long? = null,
    val serial: Long? = null,
    val status: Int? = null,
    val sensorLifetimeTotalCount: Int? = null,
    val batteryPercent: Double? = null,
)

@Serializable
data class PumpStatus(
    val status: Int? = null,
    val remainingTime: Int? = null,
    val remainingDose: Double? = null,
    val updateTime: Long? = null,
    val bGTarget: Double? = null,
    val basalSum: Double? = null,
    val bolusSum: Double? = null,
    val basalRate: Double? = null,
    val bolusDeliveredTime: Long? = null,
    val bolusDelivered: Double? = null,
    val iob: Double? = null
)

@Serializable
data class ChartData(
    val sg: List<JsonElement> = emptyList(),
    val glucose_unit: String = "mmol/L",
    val blos_high: Double = 10.0,
    val blos_low: Double = 3.9,
    val hypo: Double = 3.1,
    val st: Double = 0.0,
    val et: Double = 0.0,
    val sensor_alarm: List<JsonElement> = emptyList(),
    val pump_alarm: List<JsonElement> = emptyList(),
    val alarm_display: String? = null
)

@Serializable
data class MonitorConnectionsResponse(
    val error: Int = -1,
    val data: MonitorConnectionsData? = null
)

@Serializable
data class MonitorConnectionsData(
    val items: List<MonitorConnection> = emptyList(),
    val pages: Int = 0,
    val has_next: Boolean = false,
    val has_prev: Boolean = false
)

@Serializable
data class MonitorConnection(
    val uid: Long = 0,
    val alias: String = "",
    val real_name: String = "",
    val sensor_status: SensorStatus = SensorStatus(),
    val pump_status: PumpStatus = PumpStatus()
) : com.nimbleflux.glucosesync.shared.provider.Connection {
    override val id: String get() = uid.toString()
    override val displayName: String get() = alias.ifBlank { real_name }
    override val sensorActive: Boolean get() = sensor_status.glucose?.let { it > 0 } ?: false
    override val lastGlucoseMmol: Double? get() = sensor_status.glucose
    override val displayUnit: String get() = "mmol/L"
}
