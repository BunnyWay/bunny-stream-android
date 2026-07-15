package net.bunny.bunnystreamplayer.model

/**
 * One playable rendition of the current video, offered in the player's quality menu.
 *
 * @property width frame width in pixels.
 * @property height frame height in pixels, for example 1080 for the "1080p" rendition.
 */
data class VideoQuality(
    val width: Int,
    val height: Int
)
