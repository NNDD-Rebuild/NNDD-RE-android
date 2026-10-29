package com.nndd_re.data.model

import com.google.gson.annotations.SerializedName

// Gson は Kotlin の non-null / デフォルト値を無視して null / 0 を注入する。欠損しうる項目は nullable で受けて getter で補う
// サーバーは sizeCommand / positionCommand を返す (旧名 size / pos も受ける)
data class NnddComment(
    @SerializedName("no") val no: Int,
    @SerializedName("vposMs") val vposMs: Long,
    @SerializedName("text") private val rawText: String?,
    @SerializedName(value = "sizeCommand", alternate = ["size"]) private val rawSize: Int?,  // 0=BIG 1=MEDIUM 2=SMALL
    @SerializedName(value = "positionCommand", alternate = ["pos"]) private val rawPos: String?,  // "naka"|"ue"|"shita"
    @SerializedName("color") val color: Long,    // RGB int
    @SerializedName("strokeColor") val strokeColor: Long?
) {
    val text: String get() = rawText ?: ""
    val size: Int get() = rawSize ?: 1
    val pos: String get() = rawPos ?: "naka"
}
