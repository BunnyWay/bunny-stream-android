package net.bunny.bunnystreamplayer

import androidx.media3.common.Player
import net.bunny.bunnystreamplayer.model.Chapter
import net.bunny.bunnystreamplayer.model.Moment
import net.bunny.bunnystreamplayer.model.RetentionGraphEntry

interface PlayerStateListener {
    fun onPlayerTypeChanged(player: Player, playerType: PlayerType)
    fun onPlayingChanged(isPlaying: Boolean)
    fun onMutedChanged(isMuted: Boolean)
    fun onPlaybackSpeedChanged(speed: Float)
    fun onLoadingChanged(isLoading: Boolean)
    fun onChaptersUpdated(chapters: List<Chapter>)
    fun onMomentsUpdated(moments: List<Moment>)
    fun onRetentionGraphUpdated(points: List<RetentionGraphEntry>)
    fun onPlayerError(message: String)

    /**
     * The current video's pixel dimensions, reported when the first frame is decoded and whenever
     * they change (e.g. a quality switch or a different stream). Lets hosts size their container to
     * the real aspect ratio so both 16:9 and 9:16 (vertical) content display without distortion.
     * Default no-op so existing implementations keep compiling.
     */
    fun onVideoSizeChanged(width: Int, height: Int) {}
}