package net.bunny.bunnystreamplayer.model

/**
 * Preview thumbnails shown above the seek bar while scrubbing. Bunny generates them as sprite
 * sheets (a grid of frames per image); the player picks the right frame for the hovered position.
 *
 * @property seekThumbnailUrls URLs of the sprite-sheet images, in playback order.
 * @property frameDurationPerThumbnail how much video time one frame covers, in milliseconds.
 * @property totalThumbnailCount total number of preview frames across all sprite sheets.
 * @property thumbnailsPerImage number of frames in one sprite sheet.
 */
data class SeekThumbnail(
    val seekThumbnailUrls: List<String>,
    val frameDurationPerThumbnail: Int,
    val totalThumbnailCount: Int,
    val thumbnailsPerImage: Int,
)
