package net.bunny.api.video.domain.model

import net.bunny.api.model.VideoPlaybackSource

/**
 * Everything needed to play one video: the URLs, the library's player configuration, and the
 * video itself.
 *
 * Deliberately shaped like [net.bunny.api.livestream.domain.model.LiveStreamPlayData] — the VOD
 * and live `/play` endpoints should read the same way, so a player can share its configuration
 * logic between them.
 *
 * Colors arrive from the API as hex strings and are decoded to Android `Color` ints in the data
 * layer (consistent with [net.bunny.api.settings.domain.model.PlayerSettings]); playback speeds
 * are parsed to a `List<Float>` by the same `PlaybackSpeedManager`.
 *
 * @property video the video's own metadata, absent when the API returns play data without it.
 * @property videoPlaylistUrl HLS playlist — the normal playback source.
 * @property fallbackUrl MP4 base URL used when [enableMP4Fallback] is on and HLS is unavailable.
 * @property seekPath base path for seek thumbnails shown while scrubbing.
 * @property isPlayable whether the video has finished processing enough to play at all.
 * @property preferredPlaybackSource which source the library configuration prefers.
 * @property tokenAuthEnabled the library requires signed playback URLs; without a token, playback
 *   will be refused.
 */
public data class VideoPlayData(
    val video: Video?,
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
    /** vi.ai publisher id, used together with [vastTagUrl] when the library monetises playback. */
    val viAiPublisherId: String?,
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
    val isPlayable: Boolean,
    val isPlaylistPlayable: Boolean,
    val preferredPlaybackSource: VideoPlaybackSource?,
    val rememberPlayerPosition: Boolean,
    val customCss: String?,
    val exposeVideoMetadata: Boolean,
    val enableCompactControls: Boolean,
) {
    // Derived from the same dashboard `controls` string as PlayerSettings and LiveStreamPlayData,
    // with the same names, so a player can reuse one piece of UI logic across VOD and live. Each
    // surface only derives the controls it can actually show: live has no PiP flag here because
    // the live player reads that from PlayerSettings.
    // "rewind,fast-forward,play-large,captions,current-time,duration,fullscreen,mute,pip,play,progress,settings,volume"
    val subtitlesEnabled: Boolean = controls.contains("captions")
    val rewindEnabled: Boolean = controls.contains("rewind")
    val fastForwardEnabled: Boolean = controls.contains("fast-forward")
    val currentTimeEnabled: Boolean = controls.contains("current-time")
    val fullScreenEnabled: Boolean = controls.contains("fullscreen")
    val muteEnabled: Boolean = controls.contains("mute")
    val settingsEnabled: Boolean = controls.contains("settings")
    val progressEnabled: Boolean = controls.contains("progress")
    val durationEnabled: Boolean = controls.contains("duration")
    val playButtonEnabled: Boolean = controls.contains("play-large") || controls.contains("play")
    val castButtonEnabled: Boolean = controls.contains("chromecast")
    val pipEnabled: Boolean = controls.contains("pip")
}
