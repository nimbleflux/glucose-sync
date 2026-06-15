package com.nimbleflux.glucosesync.shared.provider.dexcom

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

class DexcomApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DexcomApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Build a minimal API pointing at the mock server.
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val client = okhttp3.OkHttpClient.Builder().build()
        api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DexcomApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun login_returnsSessionToken() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("\"abc123-session-token\"")
        )

        val token = api.login(
            DexcomLoginRequest(
                accountName = "user@example.com",
                password = "pass",
                applicationId = "d89443d2-327c-4865-8335-5a21b165a614"
            )
        )

        assertEquals("abc123-session-token", token.trim().trim('"'))
    }

    @Test
    fun login_sendsApplicationId() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("\"token\"")
        )

        api.login(
            DexcomLoginRequest("user", "pass", "test-app-id")
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("Body must include accountName", body.contains("user"))
        assertTrue("Body must include applicationId", body.contains("test-app-id"))
        assertTrue("Must be POST", recorded.method == "POST")
        assertTrue("Path must include login endpoint",
            recorded.path!!.contains("General/LoginPublisherAccountByName"))
    }

    @Test
    fun fetchGlucose_parsesServerEGVArray() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {"DT":"/Date(1700000600000)/","ST":"/Date(1700000600000)/","Trend":4,"Value":120},
                      {"DT":"/Date(1700000000000)/","ST":"/Date(1700000000000)/","Trend":3,"Value":115}
                    ]
                    """.trimIndent()
                )
        )

        val readings = api.fetchGlucose(
            DexcomGlucoseRequest(sessionId = "token", minutes = 1440, maxCount = 288)
        )

        assertEquals(2, readings.size)
        // Newest first per Dexcom API convention
        assertEquals(120, readings[0].Value)
        assertEquals(4, readings[0].Trend)
        assertEquals("/Date(1700000600000)/", readings[0].DT)
        assertEquals(115, readings[1].Value)
        assertEquals(3, readings[1].Trend)
    }

    @Test
    fun fetchGlucose_handlesEmptyResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("[]")
        )

        val readings = api.fetchGlucose(
            DexcomGlucoseRequest(sessionId = "token", minutes = 60, maxCount = 10)
        )

        assertTrue(readings.isEmpty())
    }

    @Test
    fun fetchGlucose_sendsSessionIdInBody() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("[]")
        )

        api.fetchGlucose(
            DexcomGlucoseRequest(sessionId = "my-session", minutes = 60, maxCount = 10)
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("Body must include sessionId", body.contains("my-session"))
        assertTrue("Must be POST", recorded.method == "POST")
        assertTrue("Path must include glucose endpoint",
            recorded.path!!.contains("Publisher/ReadPublisherLatestGlucoseValues"))
    }

    @Test
    fun fetchGlucose_toleratesMissingFields() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"DT":"/Date(1700000000000)/","Value":100}]""")
        )

        val readings = api.fetchGlucose(
            DexcomGlucoseRequest(sessionId = "token", minutes = 60, maxCount = 10)
        )

        assertEquals(1, readings.size)
        assertEquals(100, readings[0].Value)
        // Trend null when absent
        assertEquals(null, readings[0].Trend)
    }
}
