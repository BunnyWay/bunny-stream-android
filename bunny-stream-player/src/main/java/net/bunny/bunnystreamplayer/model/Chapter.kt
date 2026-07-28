package net.bunny.bunnystreamplayer.model

/**
 * A chapter marker of the current video, defined per video in the Bunny dashboard. The player
 * draws chapters as segments on the seek bar; the list arrives through
 * [net.bunny.bunnystreamplayer.PlayerStateListener.onChaptersUpdated].
 *
 * @property startTimeMs chapter start, in milliseconds from the beginning of the video.
 * @property endTimeMs chapter end, in milliseconds from the beginning of the video.
 * @property title chapter name shown while scrubbing.
 */
data class Chapter(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String
)
