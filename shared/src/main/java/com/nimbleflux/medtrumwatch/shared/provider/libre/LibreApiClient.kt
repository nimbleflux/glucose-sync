package com.nimbleflux.medtrumwatch.shared.provider.libre

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LibreAuthInterceptor(
    private val token: String,
    private val accountId: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("account-id", accountId)
            .addHeader("product", "llu.android")
            .addHeader("version", "4.16.0")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .build()
        return chain.proceed(request)
    }
}

object LibreRegions {
    data class Region(val code: String, val displayName: String, val url: String)

    val all = listOf(
        Region("default", "Default (api.libreview.io)", "https://api.libreview.io"),
        Region("eu", "Europe (EU)", "https://api-eu.libreview.io"),
        Region("eu2", "Europe 2 (EU2)", "https://api-eu2.libreview.io"),
        Region("us", "United States", "https://api-us.libreview.io"),
        Region("de", "Germany (DE)", "https://api-de.libreview.io"),
        Region("fr", "France (FR)", "https://api-fr.libreview.io"),
        Region("jp", "Japan (JP)", "https://api-jp.libreview.io"),
        Region("ap", "Asia Pacific (AP)", "https://api-ap.libreview.io"),
        Region("au", "Australia (AU)", "https://api-au.libreview.io"),
        Region("ae", "Middle East (AE)", "https://api-ae.libreview.io"),
        Region("ca", "Canada (CA)", "https://api-ca.libreview.io"),
        Region("la", "Latin America (LA)", "https://api-la.libreview.io"),
        Region("ru", "Russia (RU)", "https://api.libreview.ru"),
        Region("cn", "China (CN)", "https://api-cn.myfreestyle.cn")
    )

    fun urlForCode(code: String): String {
        return all.find { it.code == code }?.url ?: all.first().url
    }

    fun codeForUrl(url: String): String {
        return all.find { it.url == url }?.code ?: "default"
    }
}

object LibreCrypto {
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

object LibreApiClient {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun createUnauthenticated(baseUrl: String, debug: Boolean = false): LibreLinkUpApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("product", "llu.android")
                    .addHeader("version", "4.16.0")
                    .addHeader("Content-Type", "application/json;charset=UTF-8")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LibreLinkUpApi::class.java)
    }

    fun createAuthenticated(baseUrl: String, token: String, accountId: String, debug: Boolean = false): LibreLinkUpApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(LibreAuthInterceptor(token, accountId))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LibreLinkUpApi::class.java)
    }
}
