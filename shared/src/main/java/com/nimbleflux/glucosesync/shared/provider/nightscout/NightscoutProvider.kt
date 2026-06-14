package com.nimbleflux.glucosesync.shared.provider.nightscout

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

/**
 * Nightscout provider. Connects to a user-supplied Nightscout site via
 * the v1 REST API. Authentication uses the `api-secret` header carrying
 * the SHA-1 hex digest of the user's API token.
 *
 * Capability notes:
 *  - supportsHistory=true  (entries.json gives up to ~24h of readings)
 *  - supportsDelta=true    (Nightscout computes delta server-side)
 *  - supportsConnections=false (one site per credential)
 *  - supportsPump=false    (could be added later via devicestatus.json)
 */
class NightscoutProvider(
    private val context: Context,
    private val debug: Boolean = false
) : GlucoseProvider {

    override val id: String = "nightscout"
    override val displayName: String = "Nightscout"
    override val authType: AuthType = AuthType.API_TOKEN

    override fun supportsHistory(): Boolean = true
    override fun supportsConnections(): Boolean = false
    override fun supportsPump(): Boolean = false
    override fun supportsDelta(): Boolean = true

    private val credentialStore = CredentialStore(context)
    private var api: NightscoutApi? = null
    private var siteUrl: String = ""
    private var apiToken: String = ""

    override suspend fun login(credentials: ProviderCredentials): Result<ProviderSession> {
        val creds = credentials as? ProviderCredentials.ApiToken
            ?: return Result.failure(
                IllegalArgumentException("Nightscout requires ProviderCredentials.ApiToken")
            )

        return try {
            val service = NightscoutApiClient.create(creds.url, creds.token, debug)
            // Validate by hitting status - this is the cheapest authenticated
            // call that also tells us the API is actually enabled.
            val status = service.getStatus()
            if (status.apiEnabled != true) {
                return Result.failure(
                    GlucoseError.Unknown("Nightscout API not enabled at this URL")
                )
            }

            siteUrl = creds.url
            apiToken = creds.token
            api = service

            credentialStore.saveNightscoutSession(creds.url, creds.token)

            Result.success(
                ProviderSession(
                    providerId = id,
                    displayName = status.name ?: "Nightscout",
                    data = mapOf("url" to creds.url)
                )
            )
        } catch (e: java.io.IOException) {
            Result.failure(GlucoseError.NetworkError(e))
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Login failed", e))
        }
    }

    override suspend fun restoreSession(): Boolean {
        val url = credentialStore.getNightscoutUrl() ?: return false
        val token = credentialStore.getNightscoutToken() ?: return false
        return try {
            val service = NightscoutApiClient.create(url, token, debug)
            // Light validation - a 401 here means the token was revoked.
            service.getStatus()
            siteUrl = url
            apiToken = token
            api = service
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        val service = api ?: return Result.failure(GlucoseError.NotLoggedIn)
        return try {
            // 288 entries = 24h at the typical 5-min spacing of CGM
            // upstream sources (Dexcom, Libre via bridge).
            val entries = service.getEntries(count = 288)
            if (entries.isEmpty()) return Result.failure(GlucoseError.NoData)

            val valid = entries
                .filter { it.sgv != null && it.date != null && it.sgv!! > 0 }
                .sortedByDescending { it.date!! }

            val latest = valid.firstOrNull() ?: return Result.failure(GlucoseError.NoData)
            val glucoseMmol = latest.sgv!! / 18.0
            val timestampSec = latest.date!! / 1000L
            val trend = mapTrend(latest.direction)
            // Nightscout's delta is in mg/dL; convert to mmol/L to match our snapshot contract.
            val deltaMmol = latest.delta?.let { it / 18.0 }

            val history = valid
                .reversed()
                .map { GlucoseHistoryPoint(it.date!! / 1000L, it.sgv!! / 18.0) }
                .distinctBy { it.timestamp }

            Result.success(
                GlucoseSnapshot(
                    glucose = glucoseMmol,
                    timestamp = timestampSec,
                    trend = trend,
                    unit = "mmol/L",
                    sensorActive = true,
                    delta = deltaMmol,
                    history = history
                )
            )
        } catch (e: java.io.IOException) {
            Result.failure(GlucoseError.NetworkError(e))
        } catch (e: Exception) {
            Result.failure(GlucoseError.Unknown(e.message ?: "Fetch failed", e))
        }
    }

    override fun logout() {
        api = null
        siteUrl = ""
        apiToken = ""
    }

    /**
     * Map Nightscout direction strings to our TrendArrow enum. Values are
     * canonical per the Nightscout spec.
     */
    internal fun mapTrend(direction: String?): TrendArrow = when (direction?.lowercase()) {
        "doubleup" -> TrendArrow.RISING_RAPIDLY
        "singleup" -> TrendArrow.RISING
        "fortyfiveup" -> TrendArrow.RISING_SLOWLY
        "flat" -> TrendArrow.STABLE
        "fortyfivedown" -> TrendArrow.FALLING_SLOWLY
        "singledown" -> TrendArrow.FALLING
        "doubledown" -> TrendArrow.FALLING_RAPIDLY
        else -> TrendArrow.UNKNOWN
    }
}
