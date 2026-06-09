package com.nimbleflux.medtrumwatch.shared.provider

data class ProviderSession(
    val providerId: String,
    val displayName: String,
    val data: Map<String, String> = emptyMap()
)
