package com.nimbleflux.medtrumwatch.shared.provider.medtrum

import com.nimbleflux.medtrumwatch.shared.api.model.*
import retrofit2.http.*

interface MedtrumApi {
    @POST("v3/api/v2.0/login")
    @Headers("Content-Type: application/json")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/v2.1/monitor/{uid}/status")
    suspend fun getStatus(
        @Path("uid") uid: Long,
        @Query("param") param: String
    ): StatusResponse

    @POST("api/v2.0/monitor/data")
    suspend fun monitorData(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): MonitorDataResponse

    @POST("api/v2.1/data/{uid}")
    suspend fun dashboard(@Path("uid") uid: Long): DashboardResponse
}
