package com.nimbleflux.glucosesync.shared.provider.dexcom

import android.content.Context
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.data.Credentials
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.AuthType
import com.nimbleflux.glucosesync.shared.provider.GlucoseError
import com.nimbleflux.glucosesync.shared.provider.GlucoseProvider
import com.nimbleflux.glucosesync.shared.provider.ProviderCredentials
import com.nimbleflux.glucosesync.shared.provider.ProviderSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Dexcom Share provider. Reads glucose data from Dexcom's Share cloud API.
 *
 * Works with Dexcom G6, G7, Dexcom ONE, and Stelo — all sensor generations
 * share the same Dexcom account and the same Share API backend.
 *
 * Authentication is the community-standard two-step "by-id" flow:
 *   1. `AuthenticatePublisherAccount(accountName, password)` → accountId
 *   2. `LoginPublisherAccountById(accountId, password)`       → sessionID
 *
 * `sessionID` then authorizes glucose fetches as a query parameter.
 * This matches pydexcom, nightscout-connect, and share2nightscout-bridge.
 * The deprecated single-step `LoginPublisherAccountByName` endpoint was
 * retired by Dexcom (HTTP 500 ApplicationNotAuthenticated) and is NOT used.
 *
 * The user must have "Share" enabled in their Dexcom app settings, and the
 * credentials must be the primary publisher account (not a follower/dependent,
 * and not a newer G7-era account that only permits email/web login).
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
        // Well-known public Dexcom Share application ID used by the DIY
        // community (pydexcom, nightscout-connect, share2nightscout-bridge,
        // xDrip+, Loop, Spike, ...). Not the same as the per-app GUIDs inside
        // the G6/G7 APKs.
        internal const val APPLICATION_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"
        internal const val SESSION_INVALID_PREFIX = "Session"
        internal const val ACCOUNT_PASSWORD_INVALID = "AccountPasswordInvalid"
        internal const val SSO_AUTHENTICATE_PASSWORD_INVALID = "SSO_AuthenticatePasswordInvalid"
        internal const val APPLICATION_NOT_AUTHENTICATED = "ApplicationNotAuthenticated"
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

        // Determine region from baseUrl field, which carries the region code
        // ("us" / "ous") selected in the login screen. If a previously-stored
        // full URL sneaks through, urlForCode falls back to US.
        baseUrl = DexcomRegions.urlForCode(creds.baseUrl.ifBlank { "us" })

        return try {
            val service = buildApi(baseUrl)

            // Step 1: accountName -> accountId
            val rawAccountId = service.authenticate(
                DexcomAuthenticateRequest(
                    accountName = creds.username,
                    password = creds.password,
                    applicationId = APPLICATION_ID
                )
            )
            val accountId = extractAccountId(rawAccountId)
            if (accountId.isBlank()) {
                return Result.failure(
                    GlucoseError.ParseError("Dexcom returned no accountId: ${rawAccountId.take(120)}")
                )
            }

            // Step 2: accountId -> sessionID
            val rawSession = service.login(
                DexcomLoginByIdRequest(
                    accountId = accountId,
                    password = creds.password,
                    applicationId = APPLICATION_ID
                )
            )
            val sessionId = rawSession.trim().trim('"')
            if (sessionId.isBlank()) {
                return Result.failure(GlucoseError.ParseError("Dexcom returned no sessionID"))
            }

            sessionToken = sessionId
            api = service

            // Persist credentials (for re-auth) + the live session + region.
            credentialStore.saveCredentials(Credentials(creds.username, creds.password, baseUrl))
            credentialStore.saveDexcomSession(sessionId, baseUrl)

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
        } catch (e: HttpException) {
            classifyLoginError(e)?.let { return Result.failure(it) }
            Result.failure(GlucoseError.ServerError(e.code(), e.message()))
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
            fetchGlucoseInternal().isSuccess
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        val service = api ?: return Result.failure(GlucoseError.NotLoggedIn)
        val token = sessionToken ?: return Result.failure(GlucoseError.NotLoggedIn)

        return try {
            val readings = service.fetchGlucose(
                sessionId = token,
                minutes = 1440,
                maxCount = 288
            )
            if (readings.isEmpty()) return Result.failure(GlucoseError.NoData)

            val valid = readings.filter { it.Value != null && it.Value > 0 && it.DT != null }
            if (valid.isEmpty()) return Result.failure(GlucoseError.NoData)

            val latest = valid.first()
            val latestValue = latest.Value!!
            val glucoseMmol = latestValue.toDouble() / 18.0
            val timestampSec = parseMsDate(latest.DT!!) / 1000L
            val trend = mapTrend(latest.Trend)
            val delta = if (valid.size >= 2) {
                (latestValue.toDouble() - valid[1].Value!!.toDouble()) / 18.0
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
        } catch (e: HttpException) {
            // A bad/expired sessionID yields HTTP 400 (per share2nightscout-bridge
            // tests) or a 500 carrying a Session* error code. Surface as
            // SessionExpired so the polling service's re-auth branch can fire.
            if (e.code() == 400 || (e.code() == 500 && hasSessionError(e))) {
                Result.failure(GlucoseError.SessionExpired)
            } else {
                Result.failure(GlucoseError.ServerError(e.code(), e.message()))
            }
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Fetch failed", e))
        }
    }

    private suspend fun fetchGlucoseInternal(): Result<GlucoseSnapshot> = fetchGlucose()

    /**
     * Re-run the two-step login from the persisted username/password. Used by
     * the polling service when a fetch fails with [GlucoseError.SessionExpired].
     */
    suspend fun reAuthenticate(): Boolean {
        val creds = credentialStore.getCredentials() ?: return false
        return try {
            val result = login(
                ProviderCredentials.UsernamePassword(creds.username, creds.password, creds.baseUrl)
            )
            result.isSuccess
        } catch (_: Exception) {
            false
        }
    }

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

    /**
     * The authenticate endpoint normally returns the accountId as a bare
     * JSON-quoted string (`"abc-123"`). Newer (G7-era) accounts return it
     * wrapped in an object (`{"accountId":"abc-123"}`). Tolerate either shape
     * by trying the object form first, then stripping quotes.
     *
     * Mirrors nightscout-connect's dexcomshare.js: if the parsed body is an
     * object with `accountId`, return that; otherwise return the raw value.
     */
    internal fun extractAccountId(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return try {
                val obj = json.parseToJsonElement(trimmed).jsonObject
                obj["accountId"]?.jsonPrimitive?.contentOrNull.orEmpty()
            } catch (_: Exception) {
                ""
            }
        }
        return trimmed.trim('"')
    }

    /**
     * Map an HTTP failure from the auth endpoints to a typed [GlucoseError]:
     *  - AccountPasswordInvalid / SSO_AuthenticatePasswordInvalid → InvalidCredentials
     *  - ApplicationNotAuthenticated → ServerError (app ID rejected; misconfig)
     * Returns null when the failure isn't recognized (caller falls back).
     */
    private fun classifyLoginError(e: HttpException): GlucoseError? {
        val body = errorBody(e)
        val code = substringBetween(body, "\"Code\":\"", "\"")
            ?: substringBetween(body, "\"ErrorCode\":\"", "\"")
        return when (code) {
            ACCOUNT_PASSWORD_INVALID, SSO_AUTHENTICATE_PASSWORD_INVALID -> GlucoseError.InvalidCredentials
            APPLICATION_NOT_AUTHENTICATED ->
                GlucoseError.ServerError(e.code(), "Dexcom rejected the applicationId")
            else -> null
        }
    }

    private fun hasSessionError(e: HttpException): Boolean {
        val body = errorBody(e)
        val code = substringBetween(body, "\"Code\":\"", "\"")
            ?: substringBetween(body, "\"ErrorCode\":\"", "\"")
        return code != null && code.startsWith(SESSION_INVALID_PREFIX, ignoreCase = true)
    }

    private fun errorBody(e: HttpException): String =
        try { e.response()?.errorBody()?.string() ?: "" } catch (_: Exception) { "" }

    private fun substringBetween(haystack: String, prefix: String, suffix: String): String? {
        val start = haystack.indexOf(prefix)
        if (start < 0) return null
        val valueStart = start + prefix.length
        val end = haystack.indexOf(suffix, valueStart)
        if (end < 0) return null
        return haystack.substring(valueStart, end)
    }
}
