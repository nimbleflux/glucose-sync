package com.nimbleflux.glucosesync.shared.provider.nightscout

import retrofit2.http.GET
import retrofit2.http.Query

interface NightscoutApi {

    /**
     * Recent glucose entries, newest-first per the Nightscout API spec.
     * Default count matches the historical 24h window (~288 readings
     * at 5-minute spacing).
     */
    @GET("api/v1/entries.json")
    suspend fun getEntries(
        @Query("count") count: Int = 288
    ): List<NightscoutEntry>

    /** Site status - validates the API token + URL on login. */
    @GET("api/v1/status.json")
    suspend fun getStatus(): NightscoutStatus
}
