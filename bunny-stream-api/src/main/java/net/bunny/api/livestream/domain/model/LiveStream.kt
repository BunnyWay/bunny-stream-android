package net.bunny.api.livestream.domain.model

import net.bunny.api.model.LiveStreamStatus

/**
 * Domain representation of a Bunny Stream live stream.
 *
 * Mirrors the fields exposed by the Manage Live Streams API but is decoupled
 * from the generated DTOs so SDK consumers depend only on this type.
 */
data class LiveStream(
    val id: String,
    val videoLibraryId: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val collectionId: String?,
    val isPublic: Boolean,
    val status: LiveStreamStatus,
    val dateCreated: String,
    val scheduledStartTime: String?,
    val scheduledEndTime: String?,
    val startedAt: String?,
    val endedAt: String?,
    val durationSeconds: Int?,
    val streamKey: String?,
    val playbackUrlHls: String?,
    val dvrEnabled: Boolean,
    val dvrWindowSeconds: Int?,
    val recordVod: Boolean,
    val availableResolutions: String?,
    val width: Int?,
    val height: Int?,
    val framerate: Double?,
    val ingestRegion: String?,
    val peakConcurrentViewers: Int?,
    val totalViewerSeconds: Long?,
    val thumbnailFileName: String?,
    val thumbnailUpdatedAt: String?,
    val enableCountdown: Boolean?,
    val rtmpOutputs: List<RtmpOutput>,
    val preStreamTrailerVideoId: String?,
)

/**
 * Optional RTMP forwarding endpoint that the incoming live stream is relayed to.
 */
data class RtmpOutput(
    val endpoint: String?,
    val streamKey: String?,
)

/**
 * A generated thumbnail for a live stream, returned by the Get Thumbnails endpoint
 * (most recent first). [url] is directly loadable; [timestamp] is when it was captured.
 */
data class LiveStreamThumbnail(
    val url: String?,
    val timestamp: String?,
)

/**
 * Library-level watermark placement, all values in percent. Read from / written to the Core
 * Platform "Get/Update Video Library" endpoint. [hasWatermark] is read-only (true once an image
 * has been uploaded).
 */
data class LibraryWatermarkSettings(
    val hasWatermark: Boolean,
    val positionLeft: Int,
    val positionTop: Int,
    val width: Int,
    val height: Int,
)
