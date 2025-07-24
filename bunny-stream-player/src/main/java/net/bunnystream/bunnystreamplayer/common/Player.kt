package net.bunnystream.bunnystreamplayer.common

import androidx.annotation.FloatRange
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import net.bunnystream.api.playback.PlaybackPosition
import net.bunnystream.api.playback.PlaybackPositionManager
import net.bunnystream.api.playback.ResumeConfig
import net.bunnystream.api.playback.ResumePositionListener
import net.bunnystream.api.settings.domain.model.PlayerSettings
import net.bunnystream.bunnystreamplayer.PlayerStateListener
import net.bunnystream.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunnystream.bunnystreamplayer.model.AudioTrackInfo
import net.bunnystream.bunnystreamplayer.model.AudioTrackInfoOptions
import net.bunnystream.bunnystreamplayer.model.SeekThumbnail
import net.bunnystream.bunnystreamplayer.model.SubtitleInfo
import net.bunnystream.bunnystreamplayer.model.Subtitles
import net.bunnystream.bunnystreamplayer.model.VideoQuality
import net.bunnystream.bunnystreamplayer.model.VideoQualityOptions
import org.openapitools.client.models.VideoModel

interface BunnyPlayer {

    var playerStateListener: PlayerStateListener?

    var currentPlayer: Player?

    var seekThumbnail: SeekThumbnail?

    var autoPaused: Boolean

    var playerSettings: PlayerSettings?

    var positionManager: PlaybackPositionManager?

    /* Releases the resources held by the player, such as codecs. */
    fun release()

    /* Starts or resumes playback. */
    fun play()

    /* Pauses playback. */
    fun pause(autoPaused: Boolean = false)

    /* Stops playback and resets the player to its initial state. */
    fun stop()

    /* Seeks to a specified position in the video. */
    fun seekTo(positionMs: Long)

    /*  Sets the volume. Volume should be a float value between 0 (mute) and 1 (maximum volume). */
    fun setVolume(
        @FloatRange(from = 0.0, to = 1.0)
        volume: Float
    )

    /* Returns the current volume. */
    @FloatRange(from = 0.0, to = 1.0)
    fun getVolume(): Float

    fun isMuted(): Boolean

    /* Mutes the player. */
    fun mute()

    /* Unmutes the player. */
    fun unmute()

    /* Returns whether the player is currently playing. */
    fun isPlaying(): Boolean

    /* Returns the duration of the video. */
    fun getDuration(): Long

    /* Returns the current playback position. */
    fun getCurrentPosition(): Long

    fun playVideo(playerView: PlayerView, video: VideoModel, retentionData: Map<Int, Int>, playerSettings: PlayerSettings)

    fun skipForward()

    fun replay()

    fun setPlaybackSpeedConfig(config: PlaybackSpeedConfig)
    fun loadSavedSpeed()
    fun setSpeed(speed: Float)

    fun getSpeed(): Float

    fun getSubtitles(): Subtitles

    fun selectSubtitle(subtitleInfo: SubtitleInfo)

    fun setSubtitlesEnabled(enabled: Boolean)

    fun areSubtitlesEnabled(): Boolean

    fun getVideoQualityOptions(): VideoQualityOptions?

    fun getAudioTrackOptions(): AudioTrackInfoOptions?

    fun selectQuality(quality: VideoQuality)

    fun selectAudioTrack(audioTrackInfo: AudioTrackInfo)

    fun getPlaybackSpeeds(): List<Float>

    // New resume position methods
    fun enableResumePosition(config: ResumeConfig = ResumeConfig())
    fun disableResumePosition()
    fun clearSavedPosition(videoId: String)
    fun setResumePositionListener(listener: ResumePositionListener)

    fun clearAllSavedPositions()
    fun getAllSavedPositions(callback: (List<PlaybackPosition>)-> Unit)
    fun exportPositions(callback: (String) -> Unit)
    fun importPositions(jsonData: String, callback: (Boolean) -> Unit)
    fun cleanupExpiredPositions()
    fun setResumePosition(position: Long)
    fun saveCurrentProgress()
    fun clearProgress()
}