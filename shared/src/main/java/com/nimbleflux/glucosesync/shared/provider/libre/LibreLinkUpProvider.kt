package com.nimbleflux.glucosesync.shared.provider.libre

import android.content.Context
import com.nimbleflux.glucosesync.shared.data.CredentialStore
import com.nimbleflux.glucosesync.shared.domain.GlucoseHistoryPoint
import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import com.nimbleflux.glucosesync.shared.domain.TrendArrow
import com.nimbleflux.glucosesync.shared.provider.*
import kotlinx.coroutines.flow.SharedFlow

class LibreLinkUpProvider(private val context: Context, private val debug: Boolean = false) : GlucoseProvider {

    override val id = "libre_linkup"
    override val displayName = "LibreLinkUp"
    override val authType = AuthType.USERNAME_PASSWORD
    override val realtimeFlow: SharedFlow<GlucoseSnapshot>? = null
    override fun supportsHistory(): Boolean = true
    override fun supportsConnections(): Boolean = true
    override fun supportsPump(): Boolean = false
    override fun supportsDelta(): Boolean = false

    private val credentialStore = CredentialStore(context)

    private var token: String = ""
    private var tokenExpires: Long = 0
    private var userId: String = ""
    private var accountId: String = ""
    private var regionUrl: String = ""
    private var patientId: String = ""
    private var patientName: String = ""
    private var authenticatedApi: LibreLinkUpApi? = null

    override suspend fun login(credentials: ProviderCredentials): Result<ProviderSession> {
        val creds = credentials as? ProviderCredentials.UsernamePassword
            ?: return Result.failure(IllegalArgumentException("LibreLinkUp requires email/password"))

        val baseUrl = creds.baseUrl
        regionUrl = baseUrl

        return try {
            val api = LibreApiClient.createUnauthenticated(baseUrl, debug)
            val response = api.login(LibreLoginRequest(creds.username, creds.password))

            if (response.status == 2) {
                return Result.failure(GlucoseError.InvalidCredentials)
            }
            if (response.status == 4) {
                return Result.failure(GlucoseError.TermsNotAccepted)
            }

            val data = response.data ?: return Result.failure(GlucoseError.ParseError("Empty login response"))

            if (data.redirect && data.region != null) {
                val correctUrl = LibreRegions.urlForCode(data.region)
                if (correctUrl != baseUrl) {
                    regionUrl = correctUrl
                    return loginWithRedirect(creds.username, creds.password, correctUrl)
                }
            }

            val user = data.user ?: return Result.failure(GlucoseError.ParseError("No user data in response"))
            val authTicket = data.authTicket ?: return Result.failure(GlucoseError.ParseError("No auth ticket in response"))

            storeSession(user, authTicket, creds.username, creds.password, creds.baseUrl)
            buildAuthenticatedApi()

            val connections = fetchConnections()
            val displayName = if (connections.size == 1) connections[0].fullName else user.firstName

            if (connections.size == 1) {
                selectPatient(connections.first().patientId)
            }

            Result.success(
                ProviderSession(
                    providerId = id,
                    displayName = displayName,
                    data = mapOf(
                        "userId" to userId,
                        "connectionCount" to connections.size.toString()
                    )
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

    private suspend fun loginWithRedirect(email: String, password: String, baseUrl: String): Result<ProviderSession> {
        return try {
            val api = LibreApiClient.createUnauthenticated(baseUrl, debug)
            val response = api.login(LibreLoginRequest(email, password))
            val data = response.data ?: return Result.failure(GlucoseError.ParseError("Empty login response after redirect"))
            val user = data.user ?: return Result.failure(GlucoseError.ParseError("No user data"))
            val authTicket = data.authTicket ?: return Result.failure(GlucoseError.ParseError("No auth ticket"))

            storeSession(user, authTicket, email, password, baseUrl)
            buildAuthenticatedApi()

            val connections = fetchConnections()
            val displayName = if (connections.size == 1) connections[0].fullName else user.firstName

            if (connections.size == 1) {
                selectPatient(connections.first().patientId)
            }

            Result.success(
                ProviderSession(
                    providerId = id,
                    displayName = displayName,
                    data = mapOf(
                        "userId" to userId,
                        "connectionCount" to connections.size.toString()
                    )
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

    private suspend fun storeSession(user: LibreUser, authTicket: LibreAuthTicket, email: String, password: String, baseUrl: String) {
        userId = user.id
        accountId = LibreCrypto.sha256Hex(user.id)
        token = authTicket.token
        tokenExpires = authTicket.expires
        patientName = user.firstName

        credentialStore.saveCredentials(
            com.nimbleflux.glucosesync.shared.data.Credentials(email, password, baseUrl)
        )
        credentialStore.saveLibreSession(token, tokenExpires, userId, accountId, regionUrl)
    }

    override suspend fun getConnections(): List<Connection> {
        return try {
            fetchConnections()
        } catch (_: Exception) { emptyList() }
    }

    internal suspend fun fetchConnections(): List<LibreConnection> {
        val api = authenticatedApi ?: return emptyList()
        val response = api.getConnections()
        if (response.status != 0) return emptyList()
        return response.data ?: emptyList()
    }

    override suspend fun selectPatient(id: String) {
        patientId = id
        credentialStore.saveLibrePatient(id)
    }

    private fun buildAuthenticatedApi() {
        authenticatedApi = LibreApiClient.createAuthenticated(regionUrl, token, accountId, debug)
    }

    override suspend fun restoreSession(): Boolean {
        token = credentialStore.getLibreToken() ?: return false
        tokenExpires = credentialStore.getLibreTokenExpires()
        userId = credentialStore.getLibreUserId() ?: return false
        accountId = credentialStore.getLibreAccountId() ?: return false
        regionUrl = credentialStore.getLibreRegion() ?: return false
        patientId = credentialStore.getLibrePatientId() ?: ""
        patientName = credentialStore.getLibrePatientName() ?: ""

        if (tokenExpires > 0 && System.currentTimeMillis() / 1000 > tokenExpires) {
            val creds = credentialStore.getCredentials() ?: return false
            val result = login(ProviderCredentials.UsernamePassword(creds.username, creds.password, creds.baseUrl))
            return result.isSuccess
        }

        buildAuthenticatedApi()

        if (patientId.isBlank()) {
            val connections = fetchConnections()
            if (connections.size == 1) {
                selectPatient(connections.first().patientId)
            }
        }

        return true
    }

    override suspend fun fetchGlucose(): Result<GlucoseSnapshot> {
        val api = authenticatedApi ?: return Result.failure(GlucoseError.NotLoggedIn)
        val pid = patientId.ifBlank { return Result.failure(GlucoseError.NoPatientSelected) }

        if (tokenExpires > 0 && System.currentTimeMillis() / 1000 > tokenExpires) {
            val reAuthed = reAuthenticate()
            if (!reAuthed) return Result.failure(GlucoseError.SessionExpired)
        }

        return try {
            val response = api.getGraph(pid)
            if (response.status != 0) {
                return Result.failure(GlucoseError.ServerError(response.status, "Graph request failed"))
            }

            val data = response.data ?: return Result.failure(GlucoseError.NoData)
            val connection = data.connection
            val graphData = data.graphData ?: emptyList()

            val latestMeasurement = connection?.glucoseMeasurement
            val glucoseMmol = latestMeasurement?.ValueInMgPerDl?.let { it / 18.0 }
            val timestamp = LibreTimestamp.parseToEpochSeconds(latestMeasurement?.FactoryTimestamp ?: latestMeasurement?.Timestamp)
            val sensorActive = connection?.sensorActive ?: false
            val trend = mapTrend(latestMeasurement?.TrendArrow)

            val history = graphData.mapNotNull { point ->
                val ts = LibreTimestamp.parseToEpochSeconds(point.FactoryTimestamp ?: point.Timestamp)
                val mg = point.ValueInMgPerDl ?: return@mapNotNull null
                if (ts > 0) GlucoseHistoryPoint(ts, mg / 18.0) else null
            }.distinctBy { it.timestamp }.sortedBy { it.timestamp }

            Result.success(
                GlucoseSnapshot(
                    glucose = glucoseMmol,
                    timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis() / 1000,
                    trend = trend,
                    unit = "mmol/L",
                    sensorActive = sensorActive,
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

    override fun logout() {
        token = ""
        tokenExpires = 0
        userId = ""
        accountId = ""
        patientId = ""
        authenticatedApi = null
    }

    suspend fun reAuthenticate(): Boolean {
        val creds = credentialStore.getCredentials() ?: return false
        return try {
            val result = login(ProviderCredentials.UsernamePassword(creds.username, creds.password, creds.baseUrl))
            result.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    internal fun mapTrend(arrow: Int?): TrendArrow = when (arrow) {
        // FreeStyle Libre trend arrows (Abbott's 5-state scale, stable at 3):
        //   1 = rising rapidly   2 = rising   3 = stable
        //   4 = falling          5 = falling rapidly   0/else = unknown
        1 -> TrendArrow.RISING_RAPIDLY
        2 -> TrendArrow.RISING
        3 -> TrendArrow.STABLE
        4 -> TrendArrow.FALLING
        5 -> TrendArrow.FALLING_RAPIDLY
        else -> TrendArrow.UNKNOWN
    }
}
