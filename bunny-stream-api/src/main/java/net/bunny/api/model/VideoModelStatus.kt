package net.bunny.api.model

import com.google.gson.annotations.SerializedName

@Suppress("MagicNumber")
enum class VideoModelStatus(val value: Int) {
    @SerializedName("0")
    CREATED(0),

    @SerializedName("1")
    UPLOADED(1),

    @SerializedName("2")
    PROCESSING(2),

    @SerializedName("3")
    TRANSCODING(3),

    @SerializedName("4")
    FINISHED(4),

    @SerializedName("5")
    ERROR(5),

    @SerializedName("6")
    UPLOAD_FAILED(6),

    @SerializedName("7")
    JIT_SEGMENTING(7),

    @SerializedName("8")
    JIT_PLAYLISTS_CREATED(8);

    companion object {
        @JvmStatic
        fun fromValue(value: Int): VideoModelStatus = values().find { it.value == value }
            ?: throw IllegalArgumentException("Unknown VideoModelStatus value: $value")
    }
}