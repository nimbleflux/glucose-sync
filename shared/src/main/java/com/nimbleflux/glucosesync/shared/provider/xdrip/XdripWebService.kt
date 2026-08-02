package com.nimbleflux.glucosesync.shared.provider.xdrip

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * xDrip+ exposes a Nightscout-compatible local REST server ("xDrip Web
 * Service") on http://127.0.0.1:17580. Unlike the broadcast (which fires only
 * on new readings), this endpoint serves historic SGV entries — letting us
 * backfill up to 24h of chart history on first connect instead of starting
 * empty and filling one point per ~5 minutes.
 *
 * The server is OFF by default; the user enables it in xDrip+ → Settings →
 * Inter-App Settings → "xDrip Web Service". If it's not running, requests
 * simply fail and the provider falls back to broadcast-only operation.
 *
 * Auth rule (per xDrip's WebServiceEngine): a request from 127.0.0.1 with no
 * `api-secret` header is accepted when the user hasn't set a secret, but a
 * request that sends a header while no secret is configured is REJECTED (and
 * vice versa). So we only add the header when a secret was provided.
 */
interface XdripWebServiceApi {

    /**
     * Recent SGV entries, newest-first (Nightscout convention). `sgv` is in
     * mg/dL, `date` is epoch milliseconds. `count` is capped at 1000 by xDrip;
     * 288 = 24h at the standard 5-minute CGM spacing.
     */
    @GET("sgv.json")
    suspend fun getSgv(@Query("count") count: Int = 288): List<XdripEntry>
}

@Serializable
data class XdripEntry(
    val date: Long? = null,
    val sgv: Int? = null,
    val direction: String? = null
)

object XdripWebService {

    const val BASE_URL = "http://127.0.0.1:17580/"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * @param secret optional xDrip Web Service secret. When non-blank, sent as
     *  an `api-secret: SHA1(secret)` header (hex digest, matching Nightscout).
     *  When blank, no header is added — required for the common case where the
     *  user hasn't set a secret in xDrip+.
     */
    fun create(secret: String? = null, debug: Boolean = false): XdripWebServiceApi {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)

        if (!secret.isNullOrBlank()) {
            val secretHex = MessageDigest.getInstance("SHA-1")
                .digest(secret.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            builder.addInterceptor(XdripSecretInterceptor(secretHex))
        }

        builder.addInterceptor(HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(builder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(XdripWebServiceApi::class.java)
    }
}

/** Adds the Nightscout-style `api-secret` SHA-1 hex digest header. */
private class XdripSecretInterceptor(private val secretHex: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain) =
        chain.proceed(chain.request().newBuilder().addHeader("api-secret", secretHex).build())
}
