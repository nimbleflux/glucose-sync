package com.nimbleflux.glucosesync.shared.provider.nightscout

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Nightscout's API auth uses an `api-secret` header carrying the
 * SHA-1 hex digest of the user's API token. The token itself is never
 * sent over the wire.
 */
class NightscoutAuthInterceptor(private val secretHex: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .addHeader("api-secret", secretHex)
            .build()
        return chain.proceed(request)
    }
}

object NightscoutApiClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun create(url: String, token: String, debug: Boolean = false): NightscoutApi {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(token.toByteArray(Charsets.UTF_8))
        val secretHex = sha1.joinToString("") { "%02x".format(it) }

        val client = OkHttpClient.Builder()
            .addInterceptor(NightscoutAuthInterceptor(secretHex))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(url.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NightscoutApi::class.java)
    }
}
