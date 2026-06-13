package com.nndd_re.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NnddApiClient {

    val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun build(host: String, port: Int): NnddApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl(host, port))
            .client(okhttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NnddApi::class.java)
    }

    fun baseUrl(host: String, port: Int) = "http://$host:$port/"

    fun streamUrl(host: String, port: Int, id: String) =
        "http://$host:$port/api/video/$id/stream"

    fun thumbUrl(host: String, port: Int, id: String) =
        "http://$host:$port/api/video/$id/thumb"
}
