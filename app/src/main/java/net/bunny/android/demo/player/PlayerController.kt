package net.bunny.android.demo.player

import net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer

/**
 * Thin wrapper over the player's public API, so the screen has one place to call.
 *
 * Every method here used to go through reflection: speed was set by reading the view's private
 * `bunnyPlayer` field, with the engine's private static `instance` as a fallback, and even
 * `play`/`pause` were invoked by name. That was the demo working around gaps in the SDK's public
 * surface - gaps that are now filled, so this is plain calls.
 */
class PlayerController(private val player: BunnyStreamPlayer) {

    companion object {
        const val SPEED_0_25X = 0.25f
        const val SPEED_0_5X = 0.5f
        const val SPEED_0_75X = 0.75f
        const val SPEED_1X = 1.0f
        const val SPEED_1_25X = 1.25f
        const val SPEED_1_5X = 1.5f
        const val SPEED_2X = 2.0f

        val AVAILABLE_SPEEDS = listOf(
            SPEED_0_25X,
            SPEED_0_5X,
            SPEED_0_75X,
            SPEED_1X,
            SPEED_1_25X,
            SPEED_1_5X,
            SPEED_2X,
        )
    }

    fun setSpeed(speed: Float) {
        player.playbackSpeed = speed
    }

    fun getSpeed(): Float = player.playbackSpeed

    fun play() = player.play()

    fun pause() = player.pause()

    fun isMuted(): Boolean = player.isMuted()

    fun setMuted(muted: Boolean) {
        if (muted) player.mute() else player.unmute()
    }
}
