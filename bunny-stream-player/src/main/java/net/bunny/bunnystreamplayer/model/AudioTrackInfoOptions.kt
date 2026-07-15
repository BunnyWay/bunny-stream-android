package net.bunny.bunnystreamplayer.model

/**
 * Audio tracks available for the current video plus the one that is playing.
 *
 * @property options every selectable audio track.
 * @property selectedOption the active track, or null when the player has not selected one yet.
 */
data class AudioTrackInfoOptions(
    val options: List<AudioTrackInfo>,
    val selectedOption: AudioTrackInfo? = null
)
