package com.nndd_re.data.network

import com.nndd_re.data.model.NnddComment
import com.nndd_re.data.model.NnddVideo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface NnddApi {

    @GET("health")
    suspend fun health(): Response<Map<String, Any>>

    @GET("api/library")
    suspend fun library(): List<NnddVideo>

    @GET("api/video/{id}")
    suspend fun video(@Path("id") id: String): NnddVideo

    @GET("api/video/{id}/comments")
    suspend fun comments(@Path("id") id: String): List<NnddComment>
}
