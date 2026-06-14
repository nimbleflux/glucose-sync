package com.nimbleflux.glucosesync.shared.provider.medtrum

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedCookieJarTest {

    private val url = "https://example.medtrum.com/login".toHttpUrl()
    private val otherUrl = "https://other.medtrum.com/".toHttpUrl()

    /** Simple in-memory fake for CookiePersistence. */
    private class InMemoryPersistence : CookiePersistence {
        var blob: String? = null
        override fun loadCookies(): String? = blob
        override fun saveCookies(json: String) { blob = json }
        override fun clearCookies() { blob = null }
    }

    private fun persistentCookie(
        name: String = "session",
        value: String = "abc123",
        expiresAt: Long = System.currentTimeMillis() + 3_600_000L,
        domain: String = "example.medtrum.com",
        path: String = "/",
        secure: Boolean = true,
        httpOnly: Boolean = true
    ): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain(domain)
        .path(path)
        .expiresAt(expiresAt)
        .apply {
            if (secure) secure()
            if (httpOnly) httpOnly()
        }
        .build()

    @Test
    fun saveThenLoad_immediately_returnsTheSameCookie() {
        val jar = PersistedCookieJar(InMemoryPersistence())
        jar.saveFromResponse(url, listOf(persistentCookie()))
        val loaded = jar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("session", loaded[0].name)
        assertEquals("abc123", loaded[0].value)
    }

    @Test
    fun hydrationRoundTrip_recreatesCookieAcrossJarInstances() {
        // First jar: save and discard
        val persistence = InMemoryPersistence()
        val firstJar = PersistedCookieJar(persistence)
        firstJar.saveFromResponse(url, listOf(persistentCookie(value = "round-tripped")))

        // Second jar with the same persistence simulates process death + relaunch
        val secondJar = PersistedCookieJar(persistence)
        val loaded = secondJar.loadForRequest(url)
        assertTrue("Expected hydrated cookie to be loaded", loaded.isNotEmpty())
        assertEquals("round-tripped", loaded.first { it.name == "session" }.value)
    }

    @Test
    fun sessionCookiesWithMaxValueExpiry_areNotPersisted() {
        val persistence = InMemoryPersistence()
        val jar = PersistedCookieJar(persistence)
        // OkHttp clamps Long.MAX_VALUE to HttpDate.MAX_DATE (253402300799999L),
        // which it treats as "session cookie" internally. Construct via
        // Long.MAX_VALUE and verify the cookie doesn't survive hydrate.
        val sessionCookie = Cookie.Builder()
            .name("ephemeral")
            .value("xyz")
            .domain("example.medtrum.com")
            .path("/")
            .expiresAt(Long.MAX_VALUE)
            .build()
        jar.saveFromResponse(url, listOf(sessionCookie))

        val rehydrated = PersistedCookieJar(persistence)
        val loaded = rehydrated.loadForRequest(url)
        // Session-only cookies do not survive process death by design
        assertTrue(loaded.none { it.name == "ephemeral" })
    }

    @Test
    fun expiredCookies_areDroppedOnHydrate() {
        val persistence = InMemoryPersistence()
        val jar = PersistedCookieJar(persistence)
        val alreadyExpired = persistentCookie(
            name = "stale",
            expiresAt = System.currentTimeMillis() - 1_000L
        )
        jar.saveFromResponse(url, listOf(alreadyExpired))

        val rehydrated = PersistedCookieJar(persistence)
        val loaded = rehydrated.loadForRequest(url)
        assertTrue(loaded.none { it.name == "stale" })
    }

    @Test
    fun clear_emptiesPersistedStore() {
        val persistence = InMemoryPersistence()
        val jar = PersistedCookieJar(persistence)
        jar.saveFromResponse(url, listOf(persistentCookie()))
        jar.clear()

        // New jar should hydrate nothing
        val rehydrated = PersistedCookieJar(persistence)
        assertTrue(rehydrated.loadForRequest(url).none { it.name == "session" })
    }

    @Test
    fun multipleCookiesForDifferentUrls_allRoundTrip() {
        val persistence = InMemoryPersistence()
        val jar = PersistedCookieJar(persistence)
        jar.saveFromResponse(url, listOf(persistentCookie(name = "a", value = "1")))
        jar.saveFromResponse(otherUrl, listOf(persistentCookie(name = "b", value = "2", domain = "other.medtrum.com")))

        val rehydrated = PersistedCookieJar(persistence)
        val firstUrlLoaded = rehydrated.loadForRequest(url)
        assertTrue(firstUrlLoaded.any { it.name == "a" })
    }

    @Test
    fun corruptedBlob_isIgnoredAndDoesNotThrow() {
        val persistence = InMemoryPersistence().apply { blob = "not valid json {{{" }
        // Should not throw; should hydrate to empty jar
        val jar = PersistedCookieJar(persistence)
        assertTrue(jar.loadForRequest(url).isEmpty())
    }
}
