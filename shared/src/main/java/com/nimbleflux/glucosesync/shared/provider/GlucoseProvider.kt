package com.nimbleflux.glucosesync.shared.provider

import com.nimbleflux.glucosesync.shared.domain.GlucoseSnapshot
import kotlinx.coroutines.flow.SharedFlow

interface GlucoseProvider {
    val id: String
    val displayName: String
    val authType: AuthType

    suspend fun login(credentials: ProviderCredentials): Result<ProviderSession>
    suspend fun restoreSession(): Boolean
    suspend fun fetchGlucose(): Result<GlucoseSnapshot>
    fun logout()

    val realtimeFlow: SharedFlow<GlucoseSnapshot>? get() = null

    /** Static per provider — does it ever expose 24h history via fetchGlucose()? */
    fun supportsHistory(): Boolean = true

    /**
     * Dynamic — true if the provider currently exposes multi-patient / carer
     * monitoring. For Medtrum this flips on after login when userType == "M".
     * Callers use this to decide whether to render the patient picker.
     */
    fun supportsConnections(): Boolean = false

    /** Static — does this provider surface insulin-pump fields in snapshots? */
    fun supportsPump(): Boolean = false

    /**
     * Static — does this provider compute delta server-side?
     * When false, callers should fall back to GlucoseAggregator.computeDelta.
     */
    fun supportsDelta(): Boolean = false

    /**
     * List monitored connections. Empty if supportsConnections() is false or
     * no connections are available. Default return is empty for providers
     * that never support multi-patient.
     */
    suspend fun getConnections(): List<Connection> = emptyList()

    /**
     * Select the active patient by id (a [Connection.id]). No-op for
     * providers without multi-patient support.
     */
    suspend fun selectPatient(id: String) {}
}
