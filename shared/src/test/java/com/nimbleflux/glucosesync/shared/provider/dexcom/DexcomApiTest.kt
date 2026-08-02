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
    fun authenticate_returnsBareAccountId() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("\"account-id-from-step1\"")
        )

        val raw = api.authenticate(
            DexcomAuthenticateRequest(
                accountName = "user@example.com",
                password = "pass",
                applicationId = DexcomProvider.APPLICATION_ID
            )
        )

        assertEquals("account-id-from-step1", raw.trim().trim('"'))
    }

    @Test
    fun authenticate_sendsCorrectShape() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("\"acct\"")
        )

        api.authenticate(
            DexcomAuthenticateRequest("user@example.com", "pass", DexcomProvider.APPLICATION_ID)
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("Body must include accountName", body.contains("user@example.com"))
        assertTrue("Body must include applicationId", body.contains(DexcomProvider.APPLICATION_ID))
        assertEquals("Must be POST", "POST", recorded.method)
        assertTrue(
            "Path must hit AuthenticatePublisherAccount",
            recorded.path!!.contains("General/AuthenticatePublisherAccount")
        )
        // The two endpoints MUST NOT be the deprecated by-name login.
        assertTrue(
            "Must not use deprecated LoginPublisherAccountByName",
            !recorded.path!!.contains("LoginPublisherAccountByName")
        )
    }

    @Test
    fun login_usesAccountIdAndReturnsSessionToken() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("\"abc123-session-token\"")
        )

        val token = api.login(
            DexcomLoginByIdRequest(
                accountId = "acct-123",
                password = "pass",
                applicationId = DexcomProvider.APPLICATION_ID
            )
        )

        assertEquals("abc123-session-token", token.trim().trim('"'))

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("Body must include accountId (not accountName)", body.contains("accountId"))
        assertTrue("Body must include the account id value", body.contains("acct-123"))
        assertTrue(
            "Path must hit LoginPublisherAccountById",
            recorded.path!!.contains("General/LoginPublisherAccountById")
        )
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

        val readings = api.fetchGlucose(sessionId = "token", minutes = 1440, maxCount = 288)

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

        val readings = api.fetchGlucose(sessionId = "token", minutes = 60, maxCount = 10)

        assertTrue(readings.isEmpty())
    }

    @Test
    fun fetchGlucose_sendsSessionIdAsQueryParam() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("[]")
        )

        api.fetchGlucose(sessionId = "my-session", minutes = 60, maxCount = 10)

        val recorded = server.takeRequest()
        val path = recorded.path!!
        // sessionID must travel as a query parameter, NOT in the JSON body.
        assertTrue("sessionID must be a query param", path.contains("sessionID=my-session"))
        assertTrue("minutes must be a query param", path.contains("minutes=60"))
        assertTrue("maxCount must be a query param", path.contains("maxCount=10"))
        assertEquals("Must be POST even though it's a read", "POST", recorded.method)
        assertTrue(
            "Path must hit glucose endpoint",
            path.contains("Publisher/ReadPublisherLatestGlucoseValues")
        )
        // Body should be empty (Content-Length: 0), not a JSON object with sessionId.
        val body = recorded.body.readUtf8()
        assertTrue("Body should be empty, not carry sessionId", !body.contains("my-session"))
    }

    @Test
    fun fetchGlucose_toleratesMissingFields() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"DT":"/Date(1700000000000)/","Value":100}]""")
        )

        val readings = api.fetchGlucose(sessionId = "token", minutes = 60, maxCount = 10)

        assertEquals(1, readings.size)
        assertEquals(100, readings[0].Value)
        // Trend null when absent
        assertEquals(null, readings[0].Trend)
    }

    // ---- Provider-level response-parsing tests ----

    private val provider = DexcomProvider(android.app.Application())

    @Test
    fun extractAccountId_bareQuotedString() {
        assertEquals("acct-123", provider.extractAccountId("\"acct-123\""))
    }

    @Test
    fun extractAccountId_bareUnquotedString() {
        assertEquals("acct-123", provider.extractAccountId("acct-123"))
    }

    @Test
    fun extractAccountId_g7EraObjectResponse() {
        // Newer G7-era accounts wrap accountId in an object. This is the shape
        // nightscout-connect explicitly handles.
        val body = """{"accountId":"7a8b9c0d-1234-5678-9abc-def012345678"}"""
        assertEquals("7a8b9c0d-1234-5678-9abc-def012345678", provider.extractAccountId(body))
    }

    @Test
    fun extractAccountId_g7EraObjectWithExtraFields() {
        val body = """{"accountId":"acct-xyz","accountType":"publisher"}"""
        assertEquals("acct-xyz", provider.extractAccountId(body))
    }

    @Test
    fun extractAccountId_emptyObjectYieldsBlank() {
        assertEquals("", provider.extractAccountId("{}"))
    }
}
