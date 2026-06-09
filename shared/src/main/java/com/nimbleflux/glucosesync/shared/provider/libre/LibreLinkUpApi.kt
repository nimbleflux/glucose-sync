package com.nimbleflux.glucosesync.shared.provider.libre

import retrofit2.http.*

interface LibreLinkUpApi {

    @POST("llu/auth/login")
    @Headers("Content-Type: application/json;charset=UTF-8")
    suspend fun login(@Body request: LibreLoginRequest): LibreLoginResponse

    @GET("llu/connections")
    suspend fun getConnections(): LibreConnectionsResponse

    @GET("llu/connections/{patientId}/graph")
    suspend fun getGraph(@Path("patientId") patientId: String): LibreGraphResponse
}
