package net.bunny.bunnystreamplayer.livestream

import androidx.compose.runtime.Immutable

/**
 * Programmatic UI customization for the live player ([BunnyLiveStreamPlayer]). Every field defaults
 * to the player's current behaviour, so passing `LivePlayerConfig()` (or nothing) is a no-op.
 *
 * Theming ([primaryColor], [fontFamily], [uiLanguage]) applies to both the transport bar and the
 * non-playing overlays (countdown / offline / live badge). Control visibility is driven by
 * [controls].
 *
 * @property primaryColor accent colour (ARGB int) for the scrub bar / live badge. `null` keeps the
 *   SDK default. Maps to `PlayerSettings.keyColor`.
 * @property fontFamily Google Fonts family name (e.g. "Rubik") applied to player + overlay text.
 *   `null`/blank uses the system default.
 * @property uiLanguage ISO-639 code (e.g. "en", "de") for player + overlay copy. `null` uses the
 *   device locale.
 * @property showWatchtimeHeatmap show the retention heatmap on the scrub bar. A *running* live
 *   stream has no retention data yet, so this only has a visible effect once the stream is played
 *   back as a recording (the VOD branch).
 * @property compactControls condense the control bar, hiding secondary controls (settings,
 *   captions, duration) to reduce clutter on small surfaces.
 * @property controls per-control visibility toggles. See [LiveControls].
 */
@Immutable
public data class LivePlayerConfig(
    val primaryColor: Int? = null,
    val fontFamily: String? = null,
    val uiLanguage: String? = null,
    val showWatchtimeHeatmap: Boolean = false,
    val compactControls: Boolean = false,
    val controls: LiveControls = LiveControls(),
)

/**
 * Per-control visibility for the live player. All default to visible (matching today's chrome),
 * except [airplay] which has no Android equivalent.
 *
 * @property bigPlayButton centre "play" overlay button (`play-large`).
 * @property livePlayPause play/pause toggle.
 * @property progress the scrub/seek bar. Required for DVR time-shifting — see [dvr].
 * @property duration the position / duration readout.
 * @property mute mute toggle.
 * @property volume volume control. On the mobile layout the mute toggle doubles as the volume
 *   affordance, so this currently shares the mute button; a dedicated slider may be added later.
 * @property fullScreen fullscreen toggle.
 * @property settings settings (quality/speed) menu.
 * @property pip picture-in-picture button. Hidden automatically on devices without PiP support.
 * @property chromecast Chromecast (Cast) button.
 * @property airplay iOS AirPlay — **no-op on Android** (the platform has no AirPlay; Chromecast is
 *   the equivalent). Accepted for parity with the iOS SDK config.
 * @property dvr allow time-shifting (pausing/rewinding) into the DVR window. When `false`, the
 *   scrub bar is hidden so playback stays pinned to the live edge.
 */
@Immutable
public data class LiveControls(
    val bigPlayButton: Boolean = true,
    val livePlayPause: Boolean = true,
    val progress: Boolean = true,
    val duration: Boolean = true,
    val mute: Boolean = true,
    val volume: Boolean = true,
    val fullScreen: Boolean = true,
    val settings: Boolean = true,
    val pip: Boolean = true,
    val chromecast: Boolean = true,
    val airplay: Boolean = false,
    val dvr: Boolean = true,
)

/**
 * Renders [controls] to the comma-separated control-token string that [net.bunny.api.settings.domain.model.PlayerSettings]
 * parses (`controls.contains("…")`). Kept here so the live → PlayerSettings mapping lives in one
 * place and is unit-testable without a player.
 *
 * Notes:
 *  * DVR scrubbing requires the progress bar, so `progress` is only emitted when both
 *    [LiveControls.progress] and [LiveControls.dvr] are on.
 *  * `volume` implies the mute control (the shared mobile affordance), so `mute` is emitted when
 *    either [LiveControls.mute] or [LiveControls.volume] is on.
 *  * `airplay` is emitted for config fidelity but is ignored by the Android view layer.
 */
internal fun LivePlayerConfig.toControlsString(): String {
    val c = controls
    return buildList {
        if (c.bigPlayButton) add("play-large")
        if (c.livePlayPause) add("play")
        if (c.progress && c.dvr) add("progress")
        if (c.duration) {
            add("current-time")
            add("duration")
        }
        if (c.mute || c.volume) add("mute")
        if (c.volume) add("volume")
        if (c.settings) add("settings")
        if (c.pip) add("pip")
        if (c.fullScreen) add("fullscreen")
        if (c.chromecast) add("chromecast")
        if (c.airplay) add("airplay")
    }.joinToString(",")
}
