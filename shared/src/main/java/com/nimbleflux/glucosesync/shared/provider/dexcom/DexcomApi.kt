package com.nimbleflux.glucosesync.shared.provider.dexcom

import retrofit2.http.Body
import retrofit2.http.POST

interface DexcomApi {

    /**
     * Authenticate with Dexcom Share. Returns a session token string
     * (not JSON-wrapped — the response body IS the token, possibly quoted).
     */
    @POST("General/LoginPublisherAccountByName")
    suspend fun login(@Body request: DexcomLoginRequest): String

    /**
     * Fetch recent glucose readings. Returns newest-first per the Share API.
     */
    @POST("Publisher/ReadPublisherLatestGlucoseValues")
    suspend fun fetchGlucose(@Body request: DexcomGlucoseRequest): List<DexcomReading>
}
