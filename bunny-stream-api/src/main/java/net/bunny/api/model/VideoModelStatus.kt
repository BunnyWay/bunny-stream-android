package net.bunny.api.model

import com.google.gson.annotations.SerializedName

/**
 * Processing state of a video, carried as an integer (0..8) by the Bunny API.
 *
 * After an upload a video typically moves [CREATED] -> [UPLOADED] -> [PROCESSING] ->
 * [TRANSCODING] -> [FINISHED]. A video is playable once it reaches [FINISHED].
 */
@Suppress("MagicNumber")
enum class VideoModelStatus(val value: Int) {
    /** The video object exists but no file has been uploaded yet. */
    @SerializedName("0")
    CREATED(0),

    /** The file arrived and is queued for processing. */
    @SerializedName("1")
    UPLOADED(1),

    /** The file is being analyzed and prepared for transcoding. */
    @SerializedName("2")
    PROCESSING(2),

    /** Renditions are being encoded. The video becomes playable when this finishes. */
    @SerializedName("3")
    TRANSCODING(3),

    /** All renditions are ready. The video is playable. */
    @SerializedName("4")
    FINISHED(4),

    /** Processing failed. Check the video in the dashboard for details. */
    @SerializedName("5")
    ERROR(5),

    /** The upload did not complete. Upload the file again. */
    @SerializedName("6")
    UPLOAD_FAILED(6),

    /** Just-in-time encoding: the source is being segmented. */
    @SerializedName("7")
    JIT_SEGMENTING(7),

    /** Just-in-time encoding: playlists have been created. */
    @SerializedName("8")
    JIT_PLAYLISTS_CREATED(8);

    companion object {
        /** Maps the API integer to a status. Throws [IllegalArgumentException] for values outside 0..8. */
        @JvmStatic
        fun fromValue(value: Int): VideoModelStatus = values().find { it.value == value }
            ?: throw IllegalArgumentException("Unknown VideoModelStatus value: $value")
    }
}
