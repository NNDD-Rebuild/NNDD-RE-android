package com.nndd_re.data.model

import com.google.gson.annotations.SerializedName

data class NnddVideo(
    @SerializedName("id") val id: Int,
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("videoName") val videoName: String,
    @SerializedName("tags") val tags: List<String>,
    @SerializedName("duration") val duration: Double,
    @SerializedName("thumbUrl") val thumbUrl: String?,
    @SerializedName("playCount") val playCount: Double,
    @SerializedName("creationDate") val creationDate: String?,
    @SerializedName("lastPlayDate") val lastPlayDate: String?,
    @SerializedName("pubDate") val pubDate: String?,
    @SerializedName("uri") val uri: String,
    @SerializedName("folder") val folder: String,
    @SerializedName("description") val description: String?,
    @SerializedName("viewCount") val viewCount: Int?,
    @SerializedName("commentCount") val commentCount: Int?,
    @SerializedName("mylistCount") val mylistCount: Int?,
    @SerializedName("ownerNickname") val ownerNickname: String?,
    @SerializedName("ownerIconUrl") val ownerIconUrl: String?
)
