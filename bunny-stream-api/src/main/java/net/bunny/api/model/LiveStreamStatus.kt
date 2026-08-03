package net.bunny.api.model

import com.google.gson.annotations.SerializedName

/**
 * Lifecycle state of a live stream, carried as an integer (0..7) by the Bunny API.
 *
 * The usual life of a stream: [CREATED] (or [SCHEDULED]) -> [PREVIEW] when an encoder connects ->
 * [RUNNING] after the stream is started -> [ENDED], with [VOD_PROCESSING] in between when the
 * stream records a VOD. An ended stream cannot be restarted; create a new one instead.
 */
@Suppress("MagicNumber")
enum class LiveStreamStatus(val value: Int) {
    /**
     * The server could not determine the stream's state (API value 0), the status field was
     * absent, or the API returned a value this SDK version does not recognize.
     */
    @SerializedName("0")
    UNKNOWN(0),

    /** The stream exists but has no schedule and no encoder has connected yet. */
    @SerializedName("1")
    CREATED(1),

    /**
     * The stream has a scheduled start time. The player shows a countdown when the stream also
     * has countdowns enabled and the start time is still ahead.
     */
    @SerializedName("2")
    SCHEDULED(2),

    /**
     * An encoder is connected but the stream has not been started yet. Viewers cannot watch;
     * the publisher can check the picture. Starting the stream moves it to [RUNNING].
     */
    @SerializedName("3")
    PREVIEW(3),

    /** The stream is live and playable. */
    @SerializedName("4")
    RUNNING(4),

    /** The stream has ended. It cannot be restarted. */
    @SerializedName("5")
    ENDED(5),

    /** The stream has ended and its recording is being turned into a video. */
    @SerializedName("6")
    VOD_PROCESSING(6),

    /** Something went wrong on the streaming backend. */
    @SerializedName("7")
    ERROR(7);

    companion object {
        /** Maps the API integer to a status. Throws [IllegalArgumentException] for values outside 0..7. */
        @JvmStatic
        fun fromValue(value: Int): LiveStreamStatus = values().find { it.value == value }
            ?: throw IllegalArgumentException("Unknown LiveStreamStatus value: $value")
    }
}
