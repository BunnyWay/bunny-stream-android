package net.bunny.bunnystreamplayer.model

/**
 * Caption tracks available for the current video plus the one that is showing.
 *
 * @property subtitles every selectable caption track.
 * @property selectedSubtitle the active track, or null when captions are off.
 */
data class Subtitles(
    val subtitles: List<SubtitleInfo>,
    val selectedSubtitle: SubtitleInfo? = null
)
