package net.bunny.bunnystreamplayer.model

/**
 * Renditions available for the current video plus the one that is playing.
 *
 * @property options every selectable rendition.
 * @property selectedOption the active rendition, or null while the player picks automatically.
 */
data class VideoQualityOptions(
    val options: List<VideoQuality>,
    val selectedOption: VideoQuality? = null
)
