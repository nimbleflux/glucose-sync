package com.nimbleflux.glucosesync.shared.provider.medtrum

import android.content.Context
import android.util.Base64
import com.nimbleflux.glucosesync.shared.api.model.*
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.data.Credentials
import com.nimbleflux.glucosesync.shared.domain.AlertEntry
import com.nimbleflux.glucosesync.shared.domain.GlucoseAggregator
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MedtrumProvider(private val context: Context, private val debug: Boolean = false) : GlucoseProvider {

    override val id = "medtrum"
    override val displayName = "Medtrum EasyView"
    override val authType = AuthType.USERNAME_PASSWORD
    override val realtimeFlow: SharedFlow<GlucoseSnapshot>? = null
    override fun supportsHistory(): Boolean = true
    override fun supportsConnections(): Boolean = isCarer()
    override fun supportsPump(): Boolean = true
    override fun supportsDelta(): Boolean = true

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val credentialStore = CredentialStore(context)
    private val cookieJar = PersistedCookieJar(object : CookiePersistence {
        override fun loadCookies(): String? = credentialStore.getMedtrumCookies()
        override fun saveCookies(json: String) = credentialStore.saveMedtrumCookies(json)
        override fun clearCookies() = credentialStore.clearMedtrumCookies()
    })

    private var uid: Long = 0
    private var realname: String = ""
    private var userType: String = ""
    private var monitorUid: Long = 0
    private var api: MedtrumApi? = null

    override suspend fun login(credentials: ProviderCredentials): Result<ProviderSession> {
        val creds = credentials as? ProviderCredentials.UsernamePassword
            ?: return Result.failure(IllegalArgumentException("Medtrum requires username/password"))

        return try {
            val service = buildApi(creds.baseUrl)
            val response = service.login(LoginRequest(creds.username, creds.password))
            if (response.error != 0) {
                Result.failure(GlucoseError.ServerError(response.error, "Login failed"))
            } else {
                uid = response.uid
                realname = response.realname
                userType = response.user_type
                api = service
                monitorUid = uid
                credentialStore.saveCredentials(Credentials(creds.username, creds.password, creds.baseUrl))
                credentialStore.saveSession(response.uid, response.realname)
                credentialStore.saveMedtrumUserType(userType)

                val displayName = if (userType == "M") realname else response.realname
                Result.success(
                    ProviderSession(
                        providerId = id,
                        displayName = displayName,
                        data = mapOf("uid" to response.uid.toString(), "realname" to response.realname, "user_type" to response.user_type)
                    )
                )
            }
        } catch (e: java.io.IOException) {
            Result.failure(GlucoseError.NetworkError(e))
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Login failed", e))
        }
    }

    override suspend fun restoreSession(): Boolean {
        val creds = credentialStore.getCredentials() ?: return false

        // Try the persisted cookie jar first; if the server still honours the
        // session we skip the full login round-trip on every cold start.
        api = buildApi(creds.baseUrl)
        val sessionIsValid = try {
            val probe = api?.getConnections()
            probe?.error == 0
        } catch (_: Exception) {
            false
        }

        if (sessionIsValid) {
            val (savedUid, savedRealname) = credentialStore.getSession() ?: (0L to "")
            uid = savedUid
            realname = savedRealname
            userType = credentialStore.getMedtrumUserType() ?: "P"
            monitorUid = uid
            val probe = api?.getConnections()
            cachedConnections = probe?.data?.items.orEmpty()
            applyCarerPatientIfPresent()
            return true
        }

        // Cookie session rejected or never existed - fall back to fresh login.
        return try {
            val result = login(ProviderCredentials.UsernamePassword(creds.username, creds.password, creds.baseUrl))
            if (result.isSuccess) applyCarerPatientIfPresent()
            result.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    /**
     * After either a cookie-based restore or a fresh login, [login] leaves
     * [monitorUid] pointed at the user's own uid. For carers that's wrong:
     * we need to point at the previously-selected patient uid so the next
     * fetchGlucose call queries the right subject.
     */
    private fun applyCarerPatientIfPresent() {
        if (!isCarer()) return
        val savedPatientUid = credentialStore.getMedtrumPatientUidSync()
        if (savedPatientUid > 0) {
            monitorUid = savedPatientUid
            val savedName = credentialStore.getMedtrumPatientNameSync()
            if (savedName != null) {
                credentialStore.saveSessionDisplayNameSync(savedName)
            }
        }
    }

    fun isCarer(): Boolean = userType == "M"

    private var cachedConnections: List<MonitorConnection> = emptyList()

    override suspend fun getConnections(): List<Connection> {
        val service = api ?: return emptyList()
        return try {
            val response = service.getConnections()
            if (response.error == 0 && response.data != null) {
                cachedConnections = response.data.items
                cachedConnections
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun selectPatient(id: String) {
        val uid = id.toLongOrNull() ?: return
        val name = cachedConnections.firstOrNull { it.uid == uid }?.displayName
        monitorUid = uid
        credentialStore.saveMedtrumPatientUidSync(uid)
        if (name != null) {
            credentialStore.saveMedtrumPatientNameSync(name)
            credentialStore.saveSessionDisplayNameSync(name)
        }
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        return try {
            val service = api ?: return Result.failure(GlucoseError.NotLoggedIn)
            val param = buildHistoryParam()
            val response = service.getStatus(monitorUid, param)
            if (response.error != 0 || response.data == null) {
                Result.failure(GlucoseError.ServerError(response.error, "Status failed"))
            } else {
                val sensor = response.data.sensor_status
                val chart = response.data.chart
                val glucose = sensor.glucose
                val hasSensor = glucose != null && glucose > 0

                val sgResult = parseSgHistory(chart.sg)
                val history = sgResult.history
                val delta = if (history.size >= 2) {
                    history.last().glucoseMmol - history[history.size - 2].glucoseMmol
                } else null
                val rate = GlucoseAggregator.computeRatePerMinute(history)
                val trend = if (rate != null) {
                    TrendArrow.fromRate(rate)
                } else if (delta != null) {
                    TrendArrow.fromDelta(delta)
                } else {
                    TrendArrow.UNKNOWN
                }
                val pump = response.data.pump_status

                val timeInRange = if (history.isNotEmpty()) {
                    val high = chart.blos_high
                    val low = chart.blos_low
                    history.count { it.glucoseMmol in low..high }.toDouble() / history.size
                } else null

                val averageGlucose = if (history.isNotEmpty()) {
                    history.map { it.glucoseMmol }.average()
                } else null

                val alerts = parseAlerts(chart.sensor_alarm, chart.pump_alarm)

                Result.success(
                    GlucoseSnapshot(
                        glucose = glucose,
                        timestamp = sensor.updateTime ?: System.currentTimeMillis() / 1000,
                        trend = if (hasSensor) trend else TrendArrow.UNKNOWN,
                        unit = chart.glucose_unit.ifEmpty { "mmol/L" },
                        sensorActive = hasSensor,
                        history = history,
                        iob = pump?.iob,
                        basalRate = pump?.basalRate,
                        lastBolus = pump?.bolusDelivered,
                        lastBolusTime = pump?.bolusDeliveredTime,
                        remainingDose = pump?.remainingDose,
                        batteryPercent = sensor.batteryPercent,
                        delta = delta,
                        highThreshold = chart.blos_high,
                        lowThreshold = chart.blos_low,
                        timeInRange = timeInRange,
                        averageGlucose = averageGlucose,
                        alerts = alerts
                    )
                )
            }
        } catch (e: java.io.IOException) {
            Result.failure(GlucoseError.NetworkError(e))
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Fetch failed", e))
        }
    }

    override fun logout() {
        uid = 0
        realname = ""
        userType = ""
        monitorUid = 0
        cachedConnections = emptyList()
        api = null
        cookieJar.clear()
    }

    fun getRealname(): String = realname

    internal data class SgParseResult(
        val history: List<GlucoseHistoryPoint>
    )

    internal fun parseSgHistory(sg: List<kotlinx.serialization.json.JsonElement>): SgParseResult =
        parseSgHistoryStatic(sg)

    private fun parseAlerts(
        sensorAlarm: List<kotlinx.serialization.json.JsonElement>,
        pumpAlarm: List<kotlinx.serialization.json.JsonElement>
    ): List<AlertEntry> {
        val entries = mutableListOf<AlertEntry>()
        for (element in sensorAlarm) {
            val arr = element as? JsonArray ?: continue
            val ts = (arr.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.toLong() ?: continue
            val msg = (arr.getOrNull(1) as? JsonPrimitive)?.content ?: continue
            entries.add(AlertEntry(ts, msg, "sensor"))
        }
        for (element in pumpAlarm) {
            val arr = element as? JsonArray ?: continue
            val ts = (arr.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.toLong() ?: continue
            val msg = (arr.getOrNull(1) as? JsonPrimitive)?.content ?: continue
            entries.add(AlertEntry(ts, msg, "pump"))
        }
        return entries.sortedByDescending { it.timestamp }
    }

    private fun buildApi(baseUrl: String): MedtrumApi {
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MedtrumApi::class.java)
    }

    private fun buildHistoryParam(): String {
        val zone = ZoneId.systemDefault()
        // Rolling 24h window instead of "today from 00:00" so the chart
        // shows last-24h data correctly even before midnight. Add a small
        // buffer (1 minute into the future) so we don't miss the latest
        // reading when the request crosses the exact timestamp boundary.
        val now = java.time.Instant.now().epochSecond
        val end = now + 60
        val start = now - 86_400
        // Medtrum's API expects tz as seconds east of UTC. Use the actual
        // offset for the local zone, including DST, instead of hard-coded 0.
        val tzOffsetSec = zone.rules.getOffset(java.time.Instant.ofEpochSecond(now)).totalSeconds
        val paramJson = """{"ts":[$start,$end],"tz":$tzOffsetSec}"""
        return Base64.encodeToString(paramJson.toByteArray(), Base64.NO_WRAP)
    }
}

/**
 * Top-level pure function that parses Medtrum's `sg` JSON array into
 * [GlucoseHistoryPoint]s. Extracted from [MedtrumProvider.parseSgHistory]
 * so it can be unit-tested without instantiating the provider (which
 * requires a Context for CredentialStore).
 */
internal fun parseSgHistoryStatic(sg: List<kotlinx.serialization.json.JsonElement>): MedtrumProvider.SgParseResult {
    val history = sg.mapNotNull { element ->
        val arr = element as? JsonArray ?: return@mapNotNull null
        if (arr.size < 2) return@mapNotNull null
        val ts = ((arr[0] as? JsonPrimitive)?.doubleOrNull?.toLong()) ?: return@mapNotNull null
        val glucose = (arr[1] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        if (ts > 0 && glucose > 0) {
            GlucoseHistoryPoint(ts, glucose)
        } else null
    }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
    return MedtrumProvider.SgParseResult(history)
}
