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
}