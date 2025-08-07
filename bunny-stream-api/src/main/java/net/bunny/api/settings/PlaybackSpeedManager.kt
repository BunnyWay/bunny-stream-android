package net.bunny.api.settings

import android.util.Log

// Extension for PlayerSettingsResponse
fun net.bunny.api.settings.data.model.PlayerSettingsResponse.parsePlaybackSpeedsEnhanced(): List<Float> {
    return PlaybackSpeedManager().parsePlaybackSpeeds(this.playbackSpeeds)
}

class PlaybackSpeedManager {
    companion object {
        private const val TAG = "PlaybackSpeedManager"
        val DEFAULT_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val ALLOWED_SPEED_RANGE = 0.25f..4.0f
    }

    fun parsePlaybackSpeeds(speedString: String?): List<Float> {
        if (speedString.isNullOrBlank()) {
            Log.d(TAG, "No speed string provided, using defaults")
            return DEFAULT_SPEEDS
        }

        return try {
            speedString.split(",")
                .mapNotNull {
                    val trimmed = it.trim()
                    val speed = trimmed.toFloatOrNull()
                    if (speed != null && speed in ALLOWED_SPEED_RANGE) {
                        speed
                    } else {
                        Log.w(TAG, "Invalid speed value: $trimmed")
                        null
                    }
                }
                .takeIf { it.isNotEmpty() }
                ?: run {
                    Log.w(TAG, "No valid speeds found in: $speedString, using defaults")
                    DEFAULT_SPEEDS
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing speeds: $speedString", e)
            DEFAULT_SPEEDS
        }
    }

    fun getSpeedDisplayText(speed: Float): String {
        return when (speed) {
            0.5f -> "0.5× (Slower)"
            0.75f -> "0.75×"
            1.0f -> "Normal"
            1.25f -> "1.25×"
            1.5f -> "1.5×"
            1.75f -> "1.75×"
            2.0f -> "2× (Faster)"
            else -> "${speed}×"
        }
    }
}