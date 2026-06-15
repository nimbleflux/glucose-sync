package com.nimbleflux.glucosesync.shared.provider.dexcom

import android.content.Context
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.AuthType
import com.nimbleflux.glucosesync.shared.provider.GlucoseError
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderCredentials
import com.nimbleflux.glucosesync.shared.provider.ProviderSession
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Dexcom Share provider. Reads glucose data from Dexcom's Share cloud API.
 *
 * Works with Dexcom G6, G7, Dexcom ONE, and Stelo — all sensor generations
 * share the same Dexcom account and the same Share API backend.
 *
 * Authentication uses username + password + a well-known application ID.
 * The API returns a session token used for subsequent data fetches.
 * All endpoints are POST (even reads).
 *
 * The user must have "Share" enabled in their Dexcom app settings, and
 * both phones need internet connectivity.
 */
class DexcomProvider(
    private val context: Context,
    private val debug: Boolean = false
) : GlucoseProvider {

    override val id: String = "dexcom_share"
    override val displayName: String = "Dexcom"
    override val authType: AuthType = AuthType.USERNAME_PASSWORD

    override fun supportsHistory(): Boolean = true
    override fun supportsConnections(): Boolean = false
    override fun supportsPump(): Boolean = false
    override fun supportsDelta(): Boolean = true

    private val credentialStore = CredentialStore(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var api: DexcomApi? = null
    private var sessionToken: String? = null
    private var baseUrl: String = DexcomRegions.US

    companion object {
        // Well-known public Dexcom Share application ID, used by the DIY
        // community for years (Nightscout, xDrip+, Loop, Spike, etc.).
        // Not the same as the per-app GUIDs inside the G6/G7 APKs.
        private const val APPLICATION_ID = "d89443d2-327c-4865-8335-5a21b165a614"
        private const val TAG = "DexcomProvider"
    }

    private fun buildApi(url: String): DexcomApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DexcomApi::class.java)
    }

    override suspend fun login(credentials: ProviderCredentials): Result<ProviderSession> {
        val creds = credentials as? ProviderCredentials.UsernamePassword
            ?: return Result.failure(
                IllegalArgumentException("Dexcom requires ProviderCredentials.UsernamePassword")
            )

        // Determine region from baseUrl field (reused as region selector).
        // "ous" or any non-US value maps to the OUS endpoint.
        baseUrl = DexcomRegions.urlForRegion(creds.baseUrl.ifBlank { "us" })

        return try {
            val service = buildApi(baseUrl)
            val rawToken = service.login(
                DexcomLoginRequest(
                    accountName = creds.username,
                    password = creds.password,
                    applicationId = APPLICATION_ID
                )
            )
            // The API returns the token as a JSON-quoted string ("abc123").
            // Strip quotes if present.
            sessionToken = rawToken.trim().trim('"')
            api = service

            credentialStore.saveDexcomSession(sessionToken!!, baseUrl)

            Result.success(
                ProviderSession(
                    providerId = id,
                    displayName = "Dexcom",
                    data = mapOf("url" to baseUrl)
                )
            )
        } catch (e: java.io.IOException) {
            Result.failure(GlucoseError.NetworkError(e))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Login failed", e))
        }
    }

    override suspend fun restoreSession(): Boolean {
        val token = credentialStore.getDexcomToken() ?: return false
        val url = credentialStore.getDexcomUrl() ?: DexcomRegions.US
        return try {
            baseUrl = url
            api = buildApi(url)
            sessionToken = token
            // Validate with a lightweight fetch
            val test = fetchGlucoseInternal()
            test.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        val service = api ?: return Result.failure(GlucoseError.NotLoggedIn)
        val token = sessionToken ?: return Result.failure(GlucoseError.NotLoggedIn)

        return try {
            val readings = service.fetchGlucose(
                DexcomGlucoseRequest(
                    sessionId = token,
                    minutes = 1440,
                    maxCount = 288
                )
            )
            if (readings.isEmpty()) return Result.failure(GlucoseError.NoData)

            val valid = readings.filter { it.Value != null && it.Value > 0 && it.DT != null }
            if (valid.isEmpty()) return Result.failure(GlucoseError.NoData)

            val latest = valid.first()
            val glucoseMmol = latest.Value!!.toDouble() / 18.0
            val timestampSec = parseMsDate(latest.DT!!) / 1000L
            val trend = mapTrend(latest.Trend)
            val delta = if (valid.size >= 2) {
                (latest.Value!!.toDouble() - valid[1].Value!!.toDouble()) / 18.0
            } else null

            val history = valid
                .reversed()
                .mapNotNull { r ->
                    val ts = parseMsDate(r.DT ?: return@mapNotNull null)
                    val g = r.Value ?: return@mapNotNull null
                    if (g > 0) GlucoseHistoryPoint(ts / 1000L, g.toDouble() / 18.0) else null
                }
                .distinctBy { it.timestamp }

            Result.success(
                GlucoseSnapshot(
                    glucose = glucoseMmol,
                    timestamp = timestampSec,
                    trend = trend,
                    unit = "mmol/L",
                    sensorActive = true,
                    delta = delta,
                    history = history
                )
            )
        } catch (e: java.io.IOException) {
            Result.failure(GlucoseError.NetworkError(e))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Fetch failed", e))
        }
    }

    private suspend fun fetchGlucoseInternal(): Result<GlucoseSnapshot> = fetchGlucose()

    override fun logout() {
        sessionToken = null
        api = null
    }

    /**
     * Map Dexcom ServerTrendArrow (0-7) to our TrendArrow enum.
     * Same mapping as Libre: 1=DoubleUp through 7=DoubleDown.
     */
    internal fun mapTrend(trend: Int?): TrendArrow = when (trend) {
        1 -> TrendArrow.RISING_RAPIDLY
        2 -> TrendArrow.RISING
        3 -> TrendArrow.RISING_SLOWLY
        4 -> TrendArrow.STABLE
        5 -> TrendArrow.FALLING_SLOWLY
        6 -> TrendArrow.FALLING
        7 -> TrendArrow.FALLING_RAPIDLY
        else -> TrendArrow.UNKNOWN
    }

    /**
     * Parse Microsoft ASP.NET AJAX date format: "/Date(1700000000000-0000)/"
     * Extracts the epoch milliseconds from between "(" and "-" or ")".
     */
    internal fun parseMsDate(dateStr: String): Long {
        // Match digits after "("
        val match = Regex("/Date\\((\\d+)").find(dateStr)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
