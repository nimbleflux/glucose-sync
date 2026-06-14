package com.nimbleflux.glucosesync.shared.provider.medtrum

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.JavaNetCookieJar
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Pluggable persistence backend for [PersistedCookieJar].
 *
 * In production this is satisfied by [com.nimbleflux.glucosesync.shared.data.CredentialStore]
 * (which writes to EncryptedSharedPreferences). Tests pass an in-memory fake.
 */
interface CookiePersistence {
    fun loadCookies(): String?
    fun saveCookies(json: String)
    fun clearCookies()
}

/**
 * Wraps [JavaNetCookieJar] with persistence to [CookiePersistence].
 *
 * On construction, hydrates the in-memory jar from the persistence
 * backend. On every [saveFromResponse], rewrites the store with the
 * currently-persistable cookies (those with a real future expiry -
 * session cookies with expiresAt == Long.MAX_VALUE are kept in memory
 * but not persisted, since they cannot survive process death anyway).
 *
 * Format: compact JSON list of [PersistedCookie] instances.
 */
class PersistedCookieJar(
    private val persistence: CookiePersistence
) : CookieJar {

    private val cookieManager: CookieManager = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }

    private val delegate: JavaNetCookieJar = JavaNetCookieJar(cookieManager)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * All currently-known persistable cookies, keyed by `name@domain+path`.
     * Updated incrementally on each [saveFromResponse] so multiple calls
     * accumulate rather than overwrite. Session cookies (expiresAt ==
     * Long.MAX_VALUE) and already-expired cookies (used by servers to
     * signal deletion) are removed from this map.
     */
    private val knownCookies = mutableMapOf<String, PersistedCookie>()

    init {
        hydrate()
    }

    /**
     * OkHttp treats cookies with expiresAt == HttpDate.MAX_DATE as session
     * (non-persistent) cookies. HttpDate.MAX_DATE is not publicly exposed,
     * but its value (253402300799999L, year 9999) is stable.
     */
    private val sessionCookieExpiry = 253402300799999L

    override fun loadForRequest(url: HttpUrl): List<Cookie> = delegate.loadForRequest(url)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        delegate.saveFromResponse(url, cookies)
        val now = System.currentTimeMillis()
        cookies.forEach { c ->
            val key = "${c.name}@${c.domain}${c.path}"
            if (c.expiresAt <= now || c.expiresAt == sessionCookieExpiry) {
                // Either a server-sent deletion (expired) or a session cookie
                // we can't meaningfully persist. Drop from the persistable set.
                knownCookies.remove(key)
            } else {
                knownCookies[key] = PersistedCookie(
                    name = c.name,
                    value = c.value,
                    domain = c.domain,
                    path = c.path,
                    expiresAt = c.expiresAt,
                    secure = c.secure,
                    httpOnly = c.httpOnly
                )
            }
        }
        if (knownCookies.isEmpty()) {
            persistence.clearCookies()
        } else {
            persistence.saveCookies(json.encodeToString(knownCookies.values.toList()))
        }
    }

    fun clear() {
        knownCookies.clear()
        persistence.clearCookies()
    }

    private fun hydrate() {
        val blob = persistence.loadCookies() ?: return
        val parsed = try {
            json.decodeFromString<List<PersistedCookie>>(blob)
        } catch (_: Exception) {
            return
        }
        val now = System.currentTimeMillis()
        parsed.forEach { p ->
            if (p.expiresAt <= now) return@forEach
            val key = "${p.name}@${p.domain}${p.path}"
            knownCookies[key] = p
            val url = baseUrlFor(p) ?: return@forEach
            val cookie = p.toCookieBuilder(url).build()
            delegate.saveFromResponse(url, listOf(cookie))
        }
    }

    private fun baseUrlFor(p: PersistedCookie): HttpUrl? {
        val scheme = if (p.secure) "https" else "http"
        return runCatching {
            HttpUrl.Builder()
                .scheme(scheme)
                .host(p.domain.trimStart('.'))
                .build()
        }.getOrNull()
    }

    @Serializable
    private data class PersistedCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean
    ) {
        fun toCookieBuilder(url: HttpUrl): Cookie.Builder = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .expiresAt(expiresAt)
            .apply {
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
    }
}

