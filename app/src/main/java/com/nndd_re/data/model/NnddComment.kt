package com.nndd_re.data.model

import com.google.gson.annotations.SerializedName

data class NnddComment(
    @SerializedName("no") val no: Int,
    @SerializedName("vposMs") val vposMs: Long,
    @SerializedName("text") val text: String,
    @SerializedName("size") val size: Int,       // 0=BIG 1=MEDIUM 2=SMALL
    @SerializedName("pos") val pos: String,      // "naka"|"ue"|"shita"
    @SerializedName("color") val color: Long,    // RGB int
    @SerializedName("strokeColor") val strokeColor: Long?
)
