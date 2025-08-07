package net.bunnystream.android.demo.player

import android.util.Log
import net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer

/**
 * Controller to handle player operations including speed control
 */
class PlayerController(private val player: BunnyStreamPlayer) {
    
    companion object {
        private const val TAG = "PlayerController"
        
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
            SPEED_2X
        )
    }
    
    private var currentSpeed: Float = 1.0f
    
    /**
     * Set playback speed using reflection to access the underlying BunnyPlayer
     */
    fun setSpeed(speed: Float) {
        try {
            currentSpeed = speed
            Log.d(TAG, "Setting speed to: ${speed}x")
            
            // Try to access the underlying BunnyPlayer using reflection
            val bunnyPlayerField = player::class.java.getDeclaredField("bunnyPlayer")
            bunnyPlayerField.isAccessible = true
            val bunnyPlayer = bunnyPlayerField.get(player)
            
            if (bunnyPlayer != null) {
                // Try to call setSpeed method on the BunnyPlayer
                val setSpeedMethod = bunnyPlayer::class.java.getMethod("setSpeed", Float::class.javaPrimitiveType)
                setSpeedMethod.invoke(bunnyPlayer, speed)
                Log.d(TAG, "Successfully set speed to: ${speed}x")
            } else {
                Log.w(TAG, "BunnyPlayer is null, cannot set speed")
            }
        } catch (e: NoSuchFieldException) {
            Log.e(TAG, "Field 'bunnyPlayer' not found", e)
            tryAlternativeSpeedMethod(speed)
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "Method 'setSpeed' not found", e)
            tryAlternativeSpeedMethod(speed)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speed", e)
            tryAlternativeSpeedMethod(speed)
        }
    }
    
    /**
     * Try alternative methods to set speed
     */
    private fun tryAlternativeSpeedMethod(speed: Float) {
        try {
            // Try to access DefaultBunnyPlayer directly
            val defaultBunnyPlayerClass = Class.forName("net.bunnystream.bunnystreamplayer.DefaultBunnyPlayer")
            val instanceField = defaultBunnyPlayerClass.getDeclaredField("instance")
            instanceField.isAccessible = true
            val defaultBunnyPlayer = instanceField.get(null)
            
            if (defaultBunnyPlayer != null) {
                val setSpeedMethod = defaultBunnyPlayerClass.getMethod("setSpeed", Float::class.javaPrimitiveType)
                setSpeedMethod.invoke(defaultBunnyPlayer, speed)
                Log.d(TAG, "Successfully set speed using DefaultBunnyPlayer: ${speed}x")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Alternative speed method failed", e)
        }
    }
    
    /**
     * Get current playback speed
     */
    fun getSpeed(): Float {
        try {
            val bunnyPlayerField = player::class.java.getDeclaredField("bunnyPlayer")
            bunnyPlayerField.isAccessible = true
            val bunnyPlayer = bunnyPlayerField.get(player)
            
            if (bunnyPlayer != null) {
                val getSpeedMethod = bunnyPlayer::class.java.getMethod("getSpeed")
                return getSpeedMethod.invoke(bunnyPlayer) as Float
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting speed", e)
        }
        return currentSpeed
    }
    
    /**
     * Pause the player
     */
    fun pause() {
        try {
            val pauseMethod = player::class.java.getMethod("pause")
            pauseMethod.invoke(player)
            Log.d(TAG, "Player paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing player", e)
        }
    }
    
    /**
     * Play the player
     */
    fun play() {
        try {
            val playMethod = player::class.java.getMethod("play")
            playMethod.invoke(player)
            Log.d(TAG, "Player resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player", e)
        }
    }
}
