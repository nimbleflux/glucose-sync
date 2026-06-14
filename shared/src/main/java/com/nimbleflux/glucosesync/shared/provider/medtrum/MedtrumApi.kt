package com.nimbleflux.glucosesync.shared.provider.medtrum

import com.nimbleflux.glucosesync.shared.api.model.*
import retrofit2.http.*

interface MedtrumApi {
    @POST("v3/api/v2.0/login")
    @Headers("Content-Type: application/json")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/v2.1/monitor/connections")
    suspend fun getConnections(): MonitorConnectionsResponse

    @GET("api/v2.1/monitor/{uid}/status")
    suspend fun getStatus(
        @Path("uid") uid: Long,
        @Query("param") param: String
    ): StatusResponse
}
