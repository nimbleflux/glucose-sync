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
    val sg_rate_unit: String = "mmol/L/min",
    val blos_high: Double = 10.0,
    val blos_low: Double = 3.9,
    val hypo: Double = 3.1,
    val st: Double = 0.0,
    val et: Double = 0.0,
    val SGStateText: Map<String, String> = emptyMap(),
    val basal: List<JsonElement> = emptyList(),
    val bolus: List<JsonElement> = emptyList(),
    val calibRecord: List<JsonElement> = emptyList(),
    val sensor_alarm: List<JsonElement> = emptyList(),
    val pump_alarm: List<JsonElement> = emptyList(),
    val alarm_display: String? = null,
    val sleeptime: List<Int> = emptyList()
)

@Serializable
data class MonitorDataResponse(
    val data: MonitorData? = null,
    val error: Int = -1
)

@Serializable
data class MonitorData(
    val sg: List<JsonElement> = emptyList(),
    val sensor: SensorStatus = SensorStatus(),
    val pump: PumpStatus = PumpStatus(),
    val eventNo: Long = 0
)

@Serializable
data class DashboardResponse(
    val data: DashboardData? = null,
    val error: Int = -1
)

@Serializable
data class DashboardData(
    val target_high: Double? = null,
    val target_low: Double? = null,
    val data_source: String = ""
)
