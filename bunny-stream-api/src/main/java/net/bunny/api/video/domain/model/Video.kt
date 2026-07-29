package net.bunny.api.video.domain.model

import net.bunny.api.model.SmartGenerateStatus
import net.bunny.api.model.VideoModelStatus

/**
 * Domain representation of a video in a Bunny Stream library.
 *
 * Mirrors what the Manage Videos API exposes but is decoupled from the generated DTOs, so SDK
 * consumers depend only on this type and a change to the OpenAPI spec cannot break their build.
 *
 * Nullability follows what the API actually guarantees rather than what the generator emits: the
 * generated DTO marks every field optional, but a video always has an id, a library, a title and
 * a status. Fields that are genuinely absent — a description nobody wrote, dimensions before
 * transcoding finished — stay nullable.
 *
 * Everything but the identity ([id], [videoLibraryId], [title]) has a default, so a caller that
 * only needs to name a video — a synthetic entry for a live stream, a fixture in a test — does not
 * have to spell out thirty-three fields.
 *
 * @property id the video's GUID, used everywhere else in the SDK as `videoId`.
 * @property status where the video is in the upload → transcode → playable pipeline;
 *   [VideoModelStatus.FINISHED] means it can be played.
 * @property lengthSeconds runtime in seconds. `0` until transcoding has measured it.
 * @property availableResolutions renditions ready to play, e.g. `["240p", "360p", "720p"]`. The
 *   API returns these comma-separated; the domain model splits them, so a quality picker does not
 *   have to. Empty until transcoding produces renditions.
 * @property outputCodecs codecs the renditions were encoded with, e.g. `["x264"]`. Split from the
 *   API's comma-separated string for the same reason.
 * @property storageSizeBytes bytes the video occupies in the library.
 * @property encodeProgress transcoding progress, `0..100`.
 * @property thumbnailBlurhash compact blur placeholder for the thumbnail, when the library has
 *   them enabled — render it while the real thumbnail loads.
 */
public data class Video(
    val id: String,
    val videoLibraryId: Long,
    val title: String,
    val description: String? = null,
    val collectionId: String? = null,
    val category: String? = null,
    val dateUploaded: String? = null,
    val isPublic: Boolean = false,
    val status: VideoModelStatus = VideoModelStatus.CREATED,

    // — playback and media properties
    val lengthSeconds: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val framerate: Double? = null,
    val rotation: Int? = null,
    val availableResolutions: List<String> = emptyList(),
    val outputCodecs: List<String> = emptyList(),
    val hasMp4Fallback: Boolean = false,
    val jitEncodingEnabled: Boolean = false,

    // — storage and processing
    val storageSizeBytes: Long = 0L,
    val encodeProgress: Int = 0,
    val hasOriginal: Boolean = false,
    val originalHash: String? = null,
    val hasHighQualityPreview: Boolean = false,

    // — thumbnails
    val thumbnailCount: Int = 0,
    val thumbnailFileName: String? = null,
    val thumbnailBlurhash: String? = null,

    // — analytics
    val views: Long = 0L,
    val averageWatchTimeSeconds: Long = 0L,
    val totalWatchTimeSeconds: Long = 0L,

    // — content added on top of the video
    val captions: List<Caption> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val moments: List<Moment> = emptyList(),
    val metaTags: List<MetaTag> = emptyList(),

    // — diagnostics and AI features
    val transcodingMessages: List<TranscodingMessage> = emptyList(),
    val smartGenerateStatus: SmartGenerateStatus? = null,
    val smartGenerateFeatures: SmartGenerateFeatures? = null,
)

/**
 * A subtitle track attached to a video.
 *
 * @property languageCode BCP-47-ish source language code the API calls `srclang`, e.g. `"en"`.
 * @property label human-readable name shown in the player's caption menu.
 */
public data class Caption(
    val languageCode: String?,
    val label: String?,
    val version: Int?,
)

/**
 * A named section of a video, shown on the player's timeline.
 *
 * @property startSeconds offset where the chapter begins.
 * @property endSeconds offset where it ends.
 */
public data class Chapter(
    val title: String,
    val startSeconds: Int?,
    val endSeconds: Int?,
)

/** A labelled point in time on the player's timeline. */
public data class Moment(
    val label: String,
    val timestampSeconds: Int?,
)

/** An arbitrary key/value pair stored alongside the video. */
public data class MetaTag(
    val property: String?,
    val value: String?,
)

/**
 * A diagnostic the transcoder produced while processing the video — the SDK's window into why a
 * video looks or sounds different from the source.
 *
 * @property timestamp when the transcoder reported it, ISO 8601.
 * @property value the measurement the message refers to, when it has one (a duration difference,
 *   a framerate); free-form, as the API returns it.
 */
public data class TranscodingMessage(
    val timestamp: String?,
    val severity: TranscodingSeverity,
    val issue: TranscodingIssue,
    val message: String?,
    val value: String?,
)

/**
 * How serious a [TranscodingMessage] is.
 *
 * Named, unlike the generated enum, whose entries are `_0`.. `_3` with the meaning buried in a
 * comment.
 */
@Suppress("MagicNumber") // the numbers are the API's severity codes
public enum class TranscodingSeverity(public val value: Int) {
    UNDEFINED(0),
    INFORMATION(1),
    WARNING(2),
    ERROR(3);

    public companion object {
        /** Maps the API's numeric severity; unknown values fall back to [UNDEFINED]. */
        public fun from(value: Int?): TranscodingSeverity =
            entries.firstOrNull { it.value == value } ?: UNDEFINED
    }
}

/** What a [TranscodingMessage] is about. */
@Suppress("MagicNumber") // the numbers are the API's issue codes
public enum class TranscodingIssue(public val value: Int) {
    UNDEFINED(0),
    STREAM_LENGTHS_DIFFERENCE(1),
    TRANSCODING_WARNINGS(2),
    INCOMPATIBLE_RESOLUTION(3),
    INVALID_FRAMERATE(4),
    VIDEO_EXCEEDED_MAX_DURATION(5),
    AUDIO_EXCEEDED_MAX_DURATION(6),
    ORIGINAL_CORRUPTED(7),
    TRANSCRIPTION_FAILED(8),
    JIT_INCOMPATIBLE(9),
    JIT_FAILED(10),
    CAPTION_GENERATION_FAILED(11);

    public companion object {
        /**
         * Maps the API's numeric issue code; unknown values fall back to [UNDEFINED] so a new
         * code added server-side cannot crash an app built against this release.
         */
        public fun from(value: Int?): TranscodingIssue =
            entries.firstOrNull { it.value == value } ?: UNDEFINED
    }
}

/** Per-feature state of Bunny's AI "smart generate" pass over the video. */
public data class SmartGenerateFeatures(
    val title: SmartGenerateStatus?,
    val description: SmartGenerateStatus?,
    val chapters: SmartGenerateStatus?,
    val moments: SmartGenerateStatus?,
)
