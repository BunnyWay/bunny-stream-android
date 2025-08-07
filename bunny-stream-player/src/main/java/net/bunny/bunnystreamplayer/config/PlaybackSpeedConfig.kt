package net.bunny.bunnystreamplayer.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

data class PlaybackSpeedConfig(
    val enableSpeedControl: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val allowedSpeeds: List<Float>? = null, // null = use backend config
    val showSpeedBadge: Boolean = true, // Show "2x" indicator during playback
    val rememberLastSpeed: Boolean = true
)

class PlaybackSpeedPreferences(context: Context) {
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
