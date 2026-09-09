package net.bunny.bunnystreamplayer

import androidx.media3.common.Player
import net.bunny.bunnystreamplayer.model.Chapter
import net.bunny.bunnystreamplayer.model.Moment
import net.bunny.bunnystreamplayer.model.RetentionGraphEntry

/**
 * Callbacks for playback state changes. Assign an implementation to
 * [net.bunny.bunnystreamplayer.common.BunnyPlayer.playerStateListener] to drive custom UI
 * (play/pause buttons, loading spinners, chapter lists) from the engine's state.
 *
 * All callbacks are invoked on the main thread.
 */
interface PlayerStateListener {

    /**
     * Playback moved between the local player and a connected Chromecast device. [player] is the
     * media3 player instance now in charge; [playerType] tells you which one it is.
     */
    fun onPlayerTypeChanged(player: Player, playerType: PlayerType)

    /** Playback started ([isPlaying] = true) or paused/stopped ([isPlaying] = false). */
    fun onPlayingChanged(isPlaying: Boolean)

    /** The player was muted or unmuted. */
    fun onMutedChanged(isMuted: Boolean)

    /** Playback speed changed, for example after the user picked a speed in the settings menu. */
    fun onPlaybackSpeedChanged(speed: Float)

    /**
     * The player started ([isLoading] = true) or stopped ([isLoading] = false) loading media data
     * over the network. This is not a rebuffering signal: it also fires routinely during smooth
     * playback, so do not drive a buffering spinner from it.
     */
    fun onLoadingChanged(isLoading: Boolean)

    /**
     * Chapter markers for the current video were loaded. Chapters are defined per video in the
     * Bunny dashboard; the list is empty when the video has none.
     */
    fun onChaptersUpdated(chapters: List<Chapter>)

    /**
     * Moment markers for the current video were loaded. Moments are labelled timestamps defined
     * per video in the Bunny dashboard; the list is empty when the video has none.
     */
    fun onMomentsUpdated(moments: List<Moment>)

    /**
     * Audience retention data for the current video was loaded. The player uses it to draw the
     * watch-time heatmap above the seek bar when the library has that feature enabled.
     */
    fun onRetentionGraphUpdated(points: List<RetentionGraphEntry>)

    /** Playback failed. [message] is a human-readable description suitable for logging. */
    fun onPlayerError(message: String)

    /**
     * The current video's pixel dimensions, reported when the first frame is decoded and whenever
     * they change (e.g. a quality switch or a different stream). Lets hosts size their container to
     * the real aspect ratio so both 16:9 and 9:16 (vertical) content display without distortion.
     * Default no-op so existing implementations keep compiling.
     */
    fun onVideoSizeChanged(width: Int, height: Int) {}
}
