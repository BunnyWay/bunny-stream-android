package net.bunny.api.video.domain.model

/**
 * Breakdown of the storage one video occupies, in bytes.
 *
 * Useful for showing where a library's quota is going: a video with MP4 fallback enabled and many
 * renditions costs several times its original.
 *
 * @property encoded per-rendition sizes, keyed as the API returns them.
 * @property calculatedAt when Bunny last computed this, ISO 8601. Sizes are not recomputed on
 *   every request, so this can lag behind a recent re-encode.
 */
public data class VideoStorageSize(
    val encoded: Map<String, CodecRenditionSize>,
    val thumbnailsBytes: Long,
    val previewsBytes: Long,
    val originalsBytes: Long,
    val mp4FallbackBytes: Long,
    val miscellaneousBytes: Long,
    val calculatedAt: String?,
)

/** Size of one encoded rendition. */
public data class CodecRenditionSize(
    val codec: String?,
    val resolution: String?,
    val sizeBytes: Long,
)
