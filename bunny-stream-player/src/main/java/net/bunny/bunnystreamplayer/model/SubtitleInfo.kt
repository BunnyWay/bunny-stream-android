package net.bunny.bunnystreamplayer.model

/**
 * One caption track of the current video, as listed in the player's captions menu.
 *
 * @property title display name of the track, for example "English".
 * @property language ISO 639 language code the track was uploaded under, for example "en".
 */
data class SubtitleInfo(
    val title: String,
    val language: String
)
