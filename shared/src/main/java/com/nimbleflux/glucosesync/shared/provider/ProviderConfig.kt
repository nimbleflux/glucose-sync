package com.nimbleflux.glucosesync.shared.provider

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val description: String,
    val authType: AuthType,
    val available: Boolean,
    val icon: String = ""
)
