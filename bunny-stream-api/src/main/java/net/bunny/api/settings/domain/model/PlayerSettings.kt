package net.bunny.api.settings.domain.model

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
    /**
     * Lowest Widevine security level the library accepts (1 = L1, hardware-backed, the strictest;
     * 3 = L3, software only). Null when the library does not restrict it.
     */
    val widevineMinClientSecurityLevel: Int? = null,
    /**
     * The library only licenses SD renditions to Widevine L3 devices: on such a device the player
     * has to stay at or below [maxSoftwareWidevineResolution].
     */
    val widevineSdOnlyForL3: Boolean = false,
    /** Highest rendition (in "p", e.g. 480) a Widevine L3 device may play when [widevineSdOnlyForL3] is on. */
    val maxSoftwareWidevineResolution: Int? = null,
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

    // Additive flags used by the live player's expanded control set. These are derived the same way
    // as the controls above and don't affect VOD playback (a VOD settings payload simply won't
    // contain these tokens unless the dashboard enables them).
    val bigPlayButtonEnabled = controls.contains("play-large")
    val volumeEnabled = controls.contains("volume")
    val pipEnabled = controls.contains("pip")
    val airPlayEnabled = controls.contains("airplay")
}
