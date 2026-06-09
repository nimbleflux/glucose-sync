package com.nimbleflux.glucosesync.shared.provider

data class ProviderSession(
    val providerId: String,
    val displayName: String,
    val data: Map<String, String> = emptyMap()
)
