package net.bunny.bunnystreamplayer.model

/**
 * One point of the audience retention (watch-time heatmap) graph drawn above the seek bar.
 *
 * @property x position in the video as a percentage of its duration, 0 to 100.
 * @property y how many viewers were still watching at that position, relative to the peak.
 */
data class RetentionGraphEntry(
    val x: Int,
    val y: Int
)
