package com.nimbleflux.glucosesync.shared.provider.dexcom

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface DexcomApi {

    /**
     * Step 1 of the two-step Share login: exchange the account name + password
     * for the user's `accountId`. The response is the accountId as a bare
     * JSON string; newer (G7-era) accounts may return it wrapped in an
     * `{"accountId":"..."}` object (see [DexcomProvider.extractAccountId]).
     *
     * Returns the raw response body so the provider can tolerate either shape.
     */
    @POST("General/AuthenticatePublisherAccount")
    suspend fun authenticate(@Body request: DexcomAuthenticateRequest): String

    /**
     * Step 2 of the two-step Share login: exchange the accountId + password
     * for a `sessionID` (a 36-char GUID). Returns the raw response body.
     */
    @POST("General/LoginPublisherAccountById")
    suspend fun login(@Body request: DexcomLoginByIdRequest): String

    /**
     * Fetch recent glucose readings (ServerEGV). Newest-first per the Share
     * API. `sessionID`, `minutes` and `maxCount` travel as query parameters
     * with an empty body (Content-Length: 0) — NOT as a JSON body. All Share
     * endpoints are POST, even this read.
     */
    @POST("Publisher/ReadPublisherLatestGlucoseValues")
    suspend fun fetchGlucose(
        @Query("sessionID") sessionId: String,
        @Query("minutes") minutes: Int = 1440,
        @Query("maxCount") maxCount: Int = 288
    ): List<DexcomReading>
}
