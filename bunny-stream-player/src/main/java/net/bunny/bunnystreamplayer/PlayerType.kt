package net.bunny.bunnystreamplayer

/**
 * Which player is currently in charge of playback. Reported through
 * [PlayerStateListener.onPlayerTypeChanged] when playback moves between the device and a
 * Chromecast receiver.
 */
enum class PlayerType {
    /** Playback runs locally on the device. */
    DEFAULT_PLAYER,

    /** Playback was handed off to a connected Chromecast device. */
    CAST_PLAYER
}
