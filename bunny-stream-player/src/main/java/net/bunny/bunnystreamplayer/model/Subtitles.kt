package net.bunny.bunnystreamplayer.model

data class Subtitles(
    val subtitles: List<SubtitleInfo>,
    val selectedSubtitle: SubtitleInfo? = null
)
