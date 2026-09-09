package net.bunny.api.model

import com.google.gson.annotations.SerializedName

/**
 * Status of AI smart-generation for a video (title/description/chapters/moments).
 * Hand-written replacement for the generator's broken `VideoModelSmartGenerateStatus`
 * oneOf wrapper — wired in via `typeMappings` in bunny-stream-api/build.gradle.kts.
 */
@Suppress("MagicNumber")
enum class SmartGenerateStatus(val value: Int) {
    @SerializedName("0")
    NONE(0),

    @SerializedName("1")
    QUEUED(1),

    @SerializedName("2")
    IN_PROGRESS(2),

    @SerializedName("3")
    FINISHED(3),

    @SerializedName("4")
    FAILED(4);

    companion object {
        @JvmStatic
        fun fromValue(value: Int): SmartGenerateStatus = values().find { it.value == value }
            ?: throw IllegalArgumentException("Unknown SmartGenerateStatus value: $value")
    }
}
