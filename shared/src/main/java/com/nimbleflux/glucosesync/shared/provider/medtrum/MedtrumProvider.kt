package com.nimbleflux.glucosesync.shared.provider.medtrum

import android.content.Context
import android.util.Base64
import com.nimbleflux.glucosesync.shared.api.model.*
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.data.Credentials
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class MedtrumProvider(private val context: Context, private val debug: Boolean = false) : GlucoseProvider {

    override val id = "medtrum"
    override val displayName = "Medtrum EasyView"
    override val authType = AuthType.USERNAME_PASSWORD
    override val realtimeFlow: SharedFlow<GlucoseSnapshot>? = null
    override fun supportsHistory(): Boolean = true

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val credentialStore = CredentialStore(context)

    private var uid: Long = 0
    private var realname: String = ""
    private var api: MedtrumApi? = null

    override suspend fun login(credentials: ProviderCredentials): Result<ProviderSession> {
        val creds = credentials as? ProviderCredentials.UsernamePassword
            ?: return Result.failure(IllegalArgumentException("Medtrum requires username/password"))

        return try {
            val service = buildApi(creds.baseUrl)
            val response = service.login(LoginRequest(creds.username, creds.password))
            if (response.error != 0) {
                Result.failure(Exception("Login failed with error: ${response.error}"))
            } else {
                uid = response.uid
                realname = response.realname
                api = service
                credentialStore.saveCredentials(Credentials(creds.username, creds.password, creds.baseUrl))
                credentialStore.saveSession(response.uid, response.realname)
                Result.success(
                    ProviderSession(
                        providerId = id,
                        displayName = response.realname,
                        data = mapOf("uid" to response.uid.toString(), "realname" to response.realname)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreSession(): Boolean {
        val creds = credentialStore.getCredentials() ?: return false
        val session = credentialStore.getSession() ?: return false
        api = buildApi(creds.baseUrl)
        uid = session.first
        realname = session.second
        return true
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        return try {
            val service = api ?: return Result.failure(Exception("Not logged in"))
            val param = buildTodayParam()
            val response = service.getStatus(uid, param)
            if (response.error != 0 || response.data == null) {
                Result.failure(Exception("Status failed with error: ${response.error}"))
            } else {
                val sensor = response.data.sensor_status
                val chart = response.data.chart
                val glucose = sensor.glucose
                val hasSensor = glucose != null && glucose > 0

                Result.success(
                    GlucoseSnapshot(
                        glucose = glucose,
                        timestamp = sensor.updateTime ?: System.currentTimeMillis() / 1000,
                        trend = if (hasSensor) mapTrend(glucose, chart) else TrendArrow.UNKNOWN,
                        unit = chart.glucose_unit.ifEmpty { "mmol/L" },
                        sensorActive = hasSensor,
                        history = emptyList()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logout() {
        uid = 0
        realname = ""
        api = null
    }

    fun getRealname(): String = realname

    private fun mapTrend(glucose: Double?, chart: ChartData): TrendArrow {
        return TrendArrow.UNKNOWN
    }

    private fun buildApi(baseUrl: String): MedtrumApi {
        val client = OkHttpClient.Builder()
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

    private fun buildTodayParam(): String {
        val now = System.currentTimeMillis() / 1000
        val startOfDay = now - (now % 86400)
        val endOfDay = startOfDay + 86399
        val paramJson = """{"ts":[$startOfDay,$endOfDay],"tz":0}"""
        return Base64.encodeToString(paramJson.toByteArray(), Base64.NO_WRAP)
    }
}
