package net.bunny.bunnystreamplayer.common

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.FloatRange
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.PlaybackPositionManager
import net.bunny.api.playback.ResumeConfig
import net.bunny.api.playback.ResumePositionListener
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.bunnystreamplayer.PlayerStateListener
import net.bunny.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunny.bunnystreamplayer.model.AudioTrackInfo
import net.bunny.bunnystreamplayer.model.AudioTrackInfoOptions
import net.bunny.bunnystreamplayer.model.SeekThumbnail
import net.bunny.bunnystreamplayer.model.SubtitleInfo
import net.bunny.bunnystreamplayer.model.Subtitles
import net.bunny.bunnystreamplayer.model.VideoQuality
import net.bunny.bunnystreamplayer.model.VideoQualityOptions
import org.openapitools.client.models.VideoModel

/**
 * The playback engine behind [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer]. One engine
 * instance is shared by every player view in the process; the views are meant to be used one at
 * a time.
 *
 * Most apps never touch this interface directly: the
 * [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer] view drives it and re-exposes the pieces an
 * app usually needs (play, progress, resume positions). Reach for the engine when you build your
 * own player chrome and need direct control over tracks, quality, volume or speed.
 *
 * Call engine methods from the main thread.
 */
interface BunnyPlayer {

    /** Receives playback state callbacks. See [PlayerStateListener]. */
    var playerStateListener: PlayerStateListener?

    /** The underlying media3 [Player] - the local ExoPlayer or, while casting, the cast player. */
    var currentPlayer: Player?

    /** Seek-bar preview thumbnails of the current video, when the video has them. */
    var seekThumbnail: SeekThumbnail?

    /**
     * True while playback is paused by the SDK itself (for example when the host activity went to
     * the background) rather than by the user. Such playback resumes automatically.
     */
    var autoPaused: Boolean

    /** Player settings of the current video, fetched from the Bunny API when playback starts. */
    var playerSettings: PlayerSettings?

    /** Storage for resume positions. Set automatically when resume positions are enabled. */
    var positionManager: PlaybackPositionManager?

    /** Context the engine was created with; used for device-type checks below. */
    val context: Context

    /** Releases codecs and every other resource held by the engine. Playback cannot be resumed. */
    fun release()

    /** Starts or resumes playback. */
    fun play()

    /**
     * Pauses playback. [autoPaused] is used by the SDK's own lifecycle handling; leave it false
     * when pausing on behalf of the user.
     */
    fun pause(autoPaused: Boolean = false)

    /** Stops playback and resets the engine to its initial state. */
    fun stop()

    /** Seeks to [positionMs], in milliseconds from the start of the video. */
    fun seekTo(positionMs: Long)

    /** Sets the player volume, from 0 (silent) to 1 (full volume). */
    fun setVolume(
        @FloatRange(from = 0.0, to = 1.0)
        volume: Float
    )

    /** Returns the player volume, from 0 (silent) to 1 (full volume). */
    @FloatRange(from = 0.0, to = 1.0)
    fun getVolume(): Float

    /** Returns true while the player is muted. */
    fun isMuted(): Boolean

    /** Mutes the player by setting the volume to 0. */
    fun mute()

    /** Unmutes the player by setting the volume back to full (1.0). */
    fun unmute()

    /** Returns true while playback is running. */
    fun isPlaying(): Boolean

    /** Returns the duration of the current video in milliseconds. */
    fun getDuration(): Long

    /** Returns the current playback position in milliseconds. */
    fun getCurrentPosition(): Long

    /**
     * Starts playback of [video] inside [playerView]. Used internally by the
     * [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer] view, which fetches the video and its
     * [playerSettings] first; prefer that view's `playVideo(videoId)` unless you are building a
     * fully custom player.
     */
    fun playVideo(playerView: PlayerView, video: VideoModel, retentionData: Map<Int, Int>, playerSettings: PlayerSettings)

    /** Skips 10 seconds forward. */
    fun skipForward()

    /** Skips 10 seconds back. */
    fun replay()

    /** Applies a [PlaybackSpeedConfig]. See that class for the available options. */
    fun setPlaybackSpeedConfig(config: PlaybackSpeedConfig)

    /** Reapplies the speed remembered from the previous playback, when that option is enabled. */
    fun loadSavedSpeed()

    /** Sets the playback speed, for example 1.5f. */
    fun setSpeed(speed: Float)

    /** Returns the current playback speed. */
    fun getSpeed(): Float

    /** Returns the caption tracks of the current video and which one is showing. */
    fun getSubtitles(): Subtitles

    /** Shows the given caption track. Pick one from [getSubtitles]. */
    fun selectSubtitle(subtitleInfo: SubtitleInfo)

    /** Turns captions on or off without changing the selected track. */
    fun setSubtitlesEnabled(enabled: Boolean)

    /** Returns true while captions are turned on. */
    fun areSubtitlesEnabled(): Boolean

    /** Returns the selectable renditions of the current video, or null before playback starts. */
    fun getVideoQualityOptions(): VideoQualityOptions?

    /** Returns the selectable audio tracks of the current video, or null before playback starts. */
    fun getAudioTrackOptions(): AudioTrackInfoOptions?

    /** Locks playback to the given rendition. Pick one from [getVideoQualityOptions]. */
    fun selectQuality(quality: VideoQuality)

    /** Switches to the given audio track. Pick one from [getAudioTrackOptions]. */
    fun selectAudioTrack(audioTrackInfo: AudioTrackInfo)

    /** Returns the speeds offered in the settings menu. */
    fun getPlaybackSpeeds(): List<Float>

    /**
     * Turns on local resume positions: playback positions are saved on the device automatically.
     * Saving alone does not seek - to resume, set a [ResumePositionListener] (or pass the resume
     * callback to the player view's enableResumePosition) and confirm the jump from there. See
     * [ResumeConfig] for thresholds and retention.
     */
    fun enableResumePosition(config: ResumeConfig = ResumeConfig())

    /** Turns resume positions off. Already saved positions are kept. */
    fun disableResumePosition()

    /** Deletes the saved position of one video. */
    fun clearSavedPosition(videoId: String)

    /**
     * The resume mechanism: notified when a saved position exists for the starting video. The
     * listener decides whether to seek (ask the user, then call seekTo); without a listener
     * playback starts from the beginning.
     */
    fun setResumePositionListener(listener: ResumePositionListener)

    /** Deletes every saved position. */
    fun clearAllSavedPositions()

    /** Returns every saved position. The callback runs on the main thread. */
    fun getAllSavedPositions(callback: (List<PlaybackPosition>) -> Unit)

    /** Serializes all saved positions to JSON, for backup or transfer between devices. */
    fun exportPositions(callback: (String) -> Unit)

    /** Restores positions exported with [exportPositions]. The callback reports success. */
    fun importPositions(jsonData: String, callback: (Boolean) -> Unit)

    /** Removes saved positions older than the retention configured in [ResumeConfig]. */
    fun cleanupExpiredPositions()

    /** Seeks to a resume position. Used by the SDK after the resume prompt. */
    fun setResumePosition(position: Long)

    /** Returns true on Android TV devices. */
    fun isRunningOnTV(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /** Returns true when the device requires a touch screen. */
    fun isTouchScreenRequired(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /** Classifies the device this code is running on. See [DeviceType]. */
    fun getDeviceType(): DeviceType {
        return when {
            isRunningOnTV() -> DeviceType.TV
            isTouchScreenRequired() -> DeviceType.MOBILE
            else -> DeviceType.UNKNOWN
        }
    }
}

/**
 * Rough device classification used to route playback to the right UI, for example the Android TV
 * player. Returned by [BunnyPlayer.getDeviceType].
 */
enum class DeviceType {
    /** A phone or tablet (a device with a touch screen). */
    MOBILE,

    /** An Android TV device. */
    TV,

    /** Neither a TV nor a touch device; treat like mobile unless you know better. */
    UNKNOWN
}
