package com.nimbleflux.glucosesync.shared.provider

sealed class ProviderCredentials {
    data class UsernamePassword(
        val username: String,
        val password: String,
        val baseUrl: String
    ) : ProviderCredentials()

    data class ApiToken(
        val url: String,
        val token: String
    ) : ProviderCredentials()

    data class OAuth(
        val clientId: String,
        val clientSecret: String,
        val authCode: String? = null
    ) : ProviderCredentials()

    data object None : ProviderCredentials()
}
