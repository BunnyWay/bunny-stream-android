package net.bunny.api.livestream.domain.model

/**
 * Playback data for a live stream. Mirrors [net.bunny.api.settings.domain.model.PlayerSettings]
 * but is sourced from the Manage Live Streams `/play` endpoint and carries live-only fields.
 *
 * Colors are decoded to Android `Color` ints in the data layer (consistent with PlayerSettings);
 * playback speeds are parsed to a `List<Float>` via the same `PlaybackSpeedManager`.
 */
data class LiveStreamPlayData(
    val liveStream: LiveStream?,
    val libraryName: String?,
    val captionsPath: String?,
    val seekPath: String?,
    val thumbnailUrl: String?,
    val fallbackUrl: String?,
    val videoPlaylistUrl: String?,
    val originalUrl: String?,
    val previewUrl: String?,
    val controls: String,
    val enableDRM: Boolean,
    val drmVersion: Int,
    val keyColor: Int,
    val vastTagUrl: String?,
    val captionsFontSize: Int,
    val captionsFontColor: Int?,
    val captionsBackgroundColor: Int?,
    val uiLanguage: String?,
    val allowEarlyPlay: Boolean,
    val tokenAuthEnabled: Boolean,
    val enableMP4Fallback: Boolean,
    val showHeatmap: Boolean,
    val fontFamily: String?,
    val playbackSpeeds: List<Float>,
    val widevineMinClientSecurityLevel: Int?,
    val zoneTier: Int?,
    val rememberPlayerPosition: Boolean,
    val enableCompactControls: Boolean,
) {
    // Derived from the dashboard `controls` string, named as in PlayerSettings and VideoPlayData
    // so consumers can reuse the same UI logic
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
