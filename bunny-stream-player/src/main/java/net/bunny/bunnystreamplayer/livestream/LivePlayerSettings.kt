package net.bunny.bunnystreamplayer.livestream

import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.settings.domain.model.PlayerSettings

/**
 * Server-driven live player customization, mirroring the iOS SDK's `resolvedTheme` /
 * `resolvedConfig`: every knob (accent colour, font, UI language, control set, compact mode,
 * heatmap) comes from the live `/play` endpoint — i.e. the Bunny dashboard's player settings —
 * with SDK defaults filling any field the endpoint doesn't provide. There is deliberately no
 * client-side configuration object.
 */

/**
 * Control tokens used when play-data hasn't arrived yet (or reports no controls): the full live
 * control set. Matches the pre-server-driven default so a stream with default dashboard settings
 * renders the same chrome as before.
 */
internal const val DEFAULT_LIVE_CONTROLS: String =
    "play-large,play,progress,current-time,duration,mute,volume,settings,pip,fullscreen,chromecast"

/**
 * Builds the synthetic [PlayerSettings] the shared player engine consumes for live playback.
 *
 * @param playData        live `/play` response; `null` (not yet fetched) falls back to defaults.
 * @param hlsUrl          pre-resolved playable URL the engine builds its MediaItem from.
 * @param dvrEnabled      whether the stream has DVR — gates the timeline via [liveControlsFor].
 * @param enableSubtitles keeps/adds the `captions` token. Live playback surfaces no caption tracks
 *                        through this path (the synthetic VideoModel carries none), so a dashboard
 *                        `captions` token would render a dead button — it is stripped unless a
 *                        caller opts in here. Callers default it to `false`.
 * @param isVodRecording  the URL is an ended stream's recording, not the live edge. A recording is
 *                        a fully seekable VOD, so the timeline is NOT stripped regardless of
 *                        [dvrEnabled] (which describes the *live* time-shift capability only).
 */
internal fun livePlayerSettings(
    playData: LiveStreamPlayData?,
    hlsUrl: String,
    dvrEnabled: Boolean,
    enableSubtitles: Boolean = false,
    isVodRecording: Boolean = false,
): PlayerSettings {
    val serverControls = playData?.controls?.takeIf { it.isNotBlank() } ?: DEFAULT_LIVE_CONTROLS
    // Strip the dashboard's `captions` token by default: live has no caption tracks here, so the
    // button would be dead. Re-add it only when the caller explicitly opts in.
    val captioned = if (enableSubtitles) {
        if (serverControls.split(",").any { it.trim() == "captions" }) {
            serverControls
        } else {
            listOf(serverControls, "captions").filter { it.isNotEmpty() }.joinToString(",")
        }
    } else {
        stripControlTokens(serverControls, setOf("captions"))
    }
    val controls =
        if (isVodRecording) captioned else liveControlsFor(captioned, dvrEnabled = dvrEnabled)
    return PlayerSettings(
        thumbnailUrl = playData?.thumbnailUrl.orEmpty(),
        controls = controls,
        keyColor = playData?.keyColor ?: 0,
        captionsFontSize = playData?.captionsFontSize ?: 0,
        captionsFontColor = playData?.captionsFontColor,
        captionsBackgroundColor = playData?.captionsBackgroundColor,
        uiLanguage = playData?.uiLanguage.orEmpty(),
        showHeatmap = playData?.showHeatmap ?: false,
        fontFamily = playData?.fontFamily.orEmpty(),
        // Live playback is pinned to 1.0×; the recording (VOD branch) still goes through this
        // entry point today, so speeds stay pinned there too.
        playbackSpeeds = listOf(1.0f),
        drmEnabled = false,
        vastTagUrl = null,
        videoUrl = hlsUrl,
        seekPath = playData?.seekPath.orEmpty(),
        captionsPath = playData?.captionsPath.orEmpty(),
    )
}

/**
 * Adjusts a live control set for the stream's DVR capability. A live stream WITHOUT DVR has no
 * meaningful timeline — its position/duration are measured against the sliding HLS window (they
 * jump and rewind) — so the VOD-style scrub bar (`progress`), the time counter (`current-time`,
 * `duration`) and the seek buttons (`rewind`, `fast-forward`) are dropped, leaving just the LIVE
 * indicator + essential controls. A DVR live stream keeps them (seekable window + jump-to-live).
 * Mirrors the iOS/web live player.
 */
internal fun liveControlsFor(controls: String, dvrEnabled: Boolean): String {
    if (dvrEnabled) return controls
    return stripControlTokens(controls, setOf("progress", "current-time", "duration", "rewind", "fast-forward"))
}

/** Removes [drop] tokens from a comma-separated control string, trimming and dropping blanks. */
internal fun stripControlTokens(controls: String, drop: Set<String>): String =
    controls.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() && it !in drop }
        .joinToString(",")
