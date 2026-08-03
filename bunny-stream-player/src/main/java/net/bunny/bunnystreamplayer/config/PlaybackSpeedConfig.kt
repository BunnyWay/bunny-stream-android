package net.bunny.bunnystreamplayer.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Playback speed behaviour for [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer]. Pass an
 * instance to [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer.setPlaybackSpeedConfig] before
 * calling `playVideo`. Without it the player offers the speed options configured for the library
 * in the Bunny dashboard.
 *
 * @property enableSpeedControl show the speed option in the settings menu. Set to false to pin
 *   playback at 1x and hide the control.
 * @property defaultSpeed speed applied when playback starts, for example 1.5f.
 * @property allowedSpeeds speeds offered in the menu. Null keeps the list configured in the
 *   Bunny dashboard.
 * @property showSpeedBadge show a small "1.5x" badge over the player while the speed is not 1x.
 * @property rememberLastSpeed store the last chosen speed on the device and reapply it to the
 *   next playback.
 */
data class PlaybackSpeedConfig(
    val enableSpeedControl: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val allowedSpeeds: List<Float>? = null, // null = use backend config
    val showSpeedBadge: Boolean = true, // Show "2x" indicator during playback
    val rememberLastSpeed: Boolean = true
)

internal class PlaybackSpeedPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bunny_speed_prefs", Context.MODE_PRIVATE)

    fun saveLastSpeed(speed: Float) {
        Log.d("PlaybackSpeedPrefs", "Saving speed: $speed")
        prefs.edit().putFloat("last_speed", speed).apply()
    }

    fun getLastSpeed(defaultSpeed: Float = 1.0f): Float {
        val saved = prefs.getFloat("last_speed", defaultSpeed)
        Log.d("PlaybackSpeedPrefs", "Retrieved speed: $saved")
        return saved
    }

    fun clearLastSpeed() {
        prefs.edit().remove("last_speed").apply()
    }
}
