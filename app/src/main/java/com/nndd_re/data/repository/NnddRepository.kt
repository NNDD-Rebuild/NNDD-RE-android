package com.nndd_re.data.repository

import com.nndd_re.data.model.NnddComment
import com.nndd_re.data.model.NnddVideo
import com.nndd_re.data.network.NnddApi
import com.nndd_re.data.network.NnddApiClient

class NnddRepository(private val host: String, private val port: Int) {

    private val api: NnddApi = NnddApiClient.build(host, port)

    val baseUrl get() = NnddApiClient.baseUrl(host, port)

    suspend fun ping(): Boolean = runCatching {
        api.health().isSuccessful
    }.getOrDefault(false)

    suspend fun getLibrary(): Result<List<NnddVideo>> = runCatching {
        api.library()
    }

    suspend fun getVideo(id: String): Result<NnddVideo> = runCatching {
        api.video(id)
    }

    suspend fun getComments(id: String): Result<List<NnddComment>> = runCatching {
        api.comments(id)
    }

    fun streamUrl(id: String) = NnddApiClient.streamUrl(host, port, id)

    fun thumbUrl(id: String) = NnddApiClient.thumbUrl(host, port, id)
}
