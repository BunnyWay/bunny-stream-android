package net.bunnystream.bunnystreamplayer.model

data class VideoQualityOptions(
    val options: List<VideoQuality>,
    val selectedOption: VideoQuality? = null
)
