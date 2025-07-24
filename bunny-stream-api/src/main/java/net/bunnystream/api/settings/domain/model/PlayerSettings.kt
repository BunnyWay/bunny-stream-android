package net.bunnystream.api.settings.domain.model

data class PlayerSettings(
    val thumbnailUrl: String,
    val controls: String,
    val keyColor: Int,
    val captionsFontSize: Int,
    val captionsFontColor: Int?,
    val captionsBackgroundColor: Int?,
    val uiLanguage: String,
    val showHeatmap: Boolean,
    val fontFamily: String,
    val playbackSpeeds: List<Float>,
    val drmEnabled: Boolean,
    val vastTagUrl: String?,
    val videoUrl: String,
    val seekPath: String,
    val captionsPath: String,
    val resumePosition: Long = 0L, // Position in milliseconds
    val saveProgressInterval: Long = 30000L, // Save every 30 seconds
) {
    // "rewind,fast-forward,play-large,captions,current-time,duration,fullscreen,mute,pip,play,progress,settings,volume"
    val subtitlesEnabled = controls.contains("captions")
    val rewindEnabled = controls.contains("rewind")
    val fastForwardEnabled = controls.contains("fast-forward")
    val currentTimeEnabled = controls.contains("current-time")
    val fullScreenEnabled = controls.contains("fullscreen")
    val muteEnabled = controls.contains("mute")
    val settingsEnabled = controls.contains("settings")
    val progressEnabled = controls.contains("progress")
    val durationEnabled = controls.contains("duration")
    val playButtonEnabled = controls.contains("play-large") || controls.contains("play")
    val castButtonEnabled = controls.contains("chromecast")
}
