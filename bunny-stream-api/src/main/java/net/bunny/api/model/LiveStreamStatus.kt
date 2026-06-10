package net.bunny.api.model

import com.google.gson.annotations.SerializedName

@Suppress("MagicNumber")
enum class LiveStreamStatus(val value: Int) {
    @SerializedName("0")
    UNKNOWN(0),

    @SerializedName("1")
    CREATED(1),

    @SerializedName("2")
    SCHEDULED(2),

    @SerializedName("3")
    PREVIEW(3),

    @SerializedName("4")
    RUNNING(4),

    @SerializedName("5")
    ENDED(5),

    @SerializedName("6")
    VOD_PROCESSING(6),

    @SerializedName("7")
    ERROR(7);

    companion object {
        @JvmStatic
        fun fromValue(value: Int): LiveStreamStatus = values().find { it.value == value }
            ?: throw IllegalArgumentException("Unknown LiveStreamStatus value: $value")
    }
}
