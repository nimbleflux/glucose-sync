package com.nimbleflux.glucosesync.shared.provider.xdrip

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class XdripWebServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var api: XdripWebServiceApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Build a client pointing at the mock server (overrides the real
        // http://127.0.0.1:17580 base so we can assert requests/responses).
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val client = okhttp3.OkHttpClient.Builder().build()
        api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(XdripWebServiceApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getSgv_parsesNightscoutShape() = runBlocking {
        // xDrip's Web Service emits Nightscout-format sgv entries. We only
        // consume date/sgv/direction; the rest (noise, type, etc.) must be
        // tolerated by ignoreUnknownKeys, not rejected.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {"date":1700000600000,"sgv":180,"direction":"Flat","noise":1.2,"type":"sgv","_id":"abc"},
                      {"date":1700000000000,"sgv":175,"direction":"FortyFiveUp"}
                    ]
                    """.trimIndent()
                )
        )

        val entries = api.getSgv(count = 288)

        assertEquals(2, entries.size)
        // Newest-first per Nightscout convention.
        assertEquals(1700000600000L, entries[0].date)
        assertEquals(180, entries[0].sgv)
        assertEquals("Flat", entries[0].direction)
        assertEquals(175, entries[1].sgv)
    }

    @Test
    fun getSgv_sendsCountQueryParam() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        api.getSgv(count = 288)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue("count must be a query param", recorded.path!!.contains("count=288"))
        assertTrue("must hit sgv.json", recorded.path!!.contains("sgv.json"))
    }

    @Test
    fun getSgv_toleratesEmptyArray() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val entries = api.getSgv(count = 288)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun getSgv_toleratesMissingFields() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"date":1700000000000,"sgv":120}]""")
        )

        val entries = api.getSgv(count = 10)

        assertEquals(1, entries.size)
        assertEquals(120, entries[0].sgv)
        assertEquals(null, entries[0].direction)
    }
}
