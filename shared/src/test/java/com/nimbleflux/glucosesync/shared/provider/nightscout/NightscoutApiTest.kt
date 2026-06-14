package com.nimbleflux.glucosesync.shared.provider.nightscout

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NightscoutApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: NightscoutApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = NightscoutApiClient.create(
            url = server.url("/").toString(),
            token = "test-token",
            debug = false
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getStatus_parsesNameAndApiEnabled() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok","name":"my-nightscout","apiEnabled":true,"version":"15.0.0"}""")
        )

        val status = api.getStatus()

        assertEquals("ok", status.status)
        assertEquals("my-nightscout", status.name)
        assertTrue(status.apiEnabled == true)
        assertEquals("15.0.0", status.version)
    }

    @Test
    fun getEntries_parsesCanonicalEntriesNewestFirst() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {"_id":"a","date":1700000000000,"sgv":180,"direction":"SingleUp","delta":2.0,"type":"sgv"},
                      {"_id":"b","date":1699999940000,"sgv":178,"direction":"Flat","delta":0.0,"type":"sgv"},
                      {"_id":"c","date":1699999880000,"sgv":178,"direction":"Flat","type":"sgv"}
                    ]
                    """.trimIndent()
                )
        )

        val entries = api.getEntries(count = 10)

        assertEquals(3, entries.size)
        // API returns newest-first per Nightscout convention
        assertEquals(1700000000000L, entries[0].date)
        assertEquals(180.0, entries[0].sgv!!, 0.0001)
        assertEquals("SingleUp", entries[0].direction)
        assertEquals(2.0, entries[0].delta!!, 0.0001)
        assertEquals(1699999940000L, entries[1].date)
        // entry c has no delta - should default to null
        assertEquals(null, entries[2].delta)
    }

    @Test
    fun getEntries_handlesEmptyResponse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )

        val entries = api.getEntries(count = 10)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun getEntries_toleratesUnknownFields() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {"_id":"a","date":1700000000000,"sgv":150,"direction":"Flat",
                       "unknownFutureField":"foo","noise":12,"type":"sgv"}
                    ]
                    """.trimIndent()
                )
        )

        val entries = api.getEntries(count = 10)

        assertEquals(1, entries.size)
        assertEquals(150.0, entries[0].sgv!!, 0.0001)
        // Unknown field silently ignored due to Json { ignoreUnknownKeys = true }
    }

    @Test
    fun request_includesApiSecretHeader() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )

        api.getEntries(count = 1)

        val recorded = server.takeRequest()
        // SHA-1 of "test-token" is a8d7a4b1c2e3... - we just verify the header exists
        val secret = recorded.getHeader("api-secret")
        assertTrue("api-secret header must be present", secret != null)
        assertTrue("api-secret must be a 40-char hex SHA-1", secret!!.length == 40)
    }

    @Test
    fun request_urlContainsEntriesPath() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )

        api.getEntries(count = 5)

        val recorded = server.takeRequest()
        assertTrue("Path must include entries.json", recorded.path!!.contains("api/v1/entries.json"))
        assertTrue("Query must carry the count", recorded.path!!.contains("count=5"))
    }
}
