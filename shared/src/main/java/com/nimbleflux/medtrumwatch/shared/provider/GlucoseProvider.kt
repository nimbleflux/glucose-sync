package com.nimbleflux.medtrumwatch.shared.provider

import com.nimbleflux.medtrumwatch.shared.domain.GlucoseSnapshot
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
    fun supportsHistory(): Boolean = true
}
