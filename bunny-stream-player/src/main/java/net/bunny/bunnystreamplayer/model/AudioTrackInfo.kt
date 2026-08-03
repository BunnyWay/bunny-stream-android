package net.bunny.bunnystreamplayer.model

/**
 * One selectable audio track of the current video, as listed in the player's settings menu.
 *
 * @property index position of the track in the player's track list.
 * @property trackId stable track identifier from the stream manifest, when the manifest provides one.
 * @property label human-readable name to show in a track picker, when the manifest provides one.
 * @property languageCode ISO 639 language code of the track, for example "en", when known.
 */
data class AudioTrackInfo(
    val index: Int,
    val trackId: String?,
    val label: String?,
    val languageCode: String?
)
