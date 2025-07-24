// Update bunny-stream-api/src/main/java/net/bunnystream/api/playback/PlaybackPositionManager.kt
// Use this simplified version instead of the complex database version

package net.bunnystream.api.playback

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Keep the existing interfaces and data classes
interface PlaybackPositionManager {
    suspend fun savePosition(videoId: String, position: Long, duration: Long)
    suspend fun getPosition(videoId: String): PlaybackPosition?
    suspend fun clearPosition(videoId: String)
    suspend fun clearAllPositions()
    suspend fun cleanupExpiredPositions()
    suspend fun getAllPositions(): List<PlaybackPosition>
    suspend fun exportPositions(): String
    suspend fun importPositions(jsonData: String): Boolean
}

data class PlaybackPosition(
    val videoId: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long,
    val watchPercentage: Float,
    val videoTitle: String = "",
    val libraryId: Long? = null
)

data class ResumeConfig(
    val retentionDays: Int = 7,
    val minimumWatchTime: Long = 30_000L, // 30 seconds
    val resumeThreshold: Float = 0.05f, // Don't resume if < 5% watched
    val nearEndThreshold: Float = 0.95f, // Don't resume if > 95% watched
    val enableAutoSave: Boolean = true,
    val saveInterval: Long = 10_000L // Save every 10 seconds during playback
)

// Simplified implementation that works immediately
class DefaultPlaybackPositionManager(
    private val context: Context,
    private val config: ResumeConfig
) : PlaybackPositionManager {

    companion object {
        private const val PREF_NAME = "bunny_resume_positions"
        private const val KEY_POSITIONS = "all_positions"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    override suspend fun savePosition(videoId: String, position: Long, duration: Long) = withContext(Dispatchers.IO) {
        try {
            if (duration <= 0) return@withContext
            
            val watchPercentage = position.toFloat() / duration.toFloat()
            
            // Only save if meaningful progress
            if (position > config.minimumWatchTime &&
                watchPercentage >= config.resumeThreshold &&
                watchPercentage <= config.nearEndThreshold) {
                
                val playbackPosition = PlaybackPosition(
                    videoId = videoId,
                    position = position,
                    duration = duration,
                    timestamp = System.currentTimeMillis(),
                    watchPercentage = watchPercentage
                )
                
                val positions = getAllPositions().toMutableList()
                
                // Remove existing position for this video
                positions.removeAll { it.videoId == videoId }
                
                // Add new position
                positions.add(playbackPosition)
                
                // Keep only last 100 positions
                if (positions.size > 100) {
                    positions.sortByDescending { it.timestamp }
                    positions.subList(100, positions.size).clear()
                }
                
                // Save to preferences
                saveAllPositions(positions)
            }
        } catch (e: Exception) {
            // Handle silently
        }
    }

    override suspend fun getPosition(videoId: String): PlaybackPosition? = withContext(Dispatchers.IO) {
        try {
            val position = getAllPositions().find { it.videoId == videoId }
            
            if (position != null && isPositionValid(position)) {
                return@withContext position
            } else if (position != null) {
                // Remove expired position
                clearPosition(videoId)
            }
            
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
    }

    override suspend fun clearPosition(videoId: String) = withContext(Dispatchers.IO) {
        try {
            val positions = getAllPositions().toMutableList()
            positions.removeAll { it.videoId == videoId }
            saveAllPositions(positions)
        } catch (e: Exception) {
            // Handle silently
        }
    }

    override suspend fun clearAllPositions() = withContext(Dispatchers.IO) {
        try {
            prefs.edit().remove(KEY_POSITIONS).apply()
        } catch (e: Exception) {
            // Handle silently
        }
    }

    override suspend fun cleanupExpiredPositions() = withContext(Dispatchers.IO) {
        try {
            val positions = getAllPositions().filter { isPositionValid(it) }
            saveAllPositions(positions)
        } catch (e: Exception) {
            // Handle silently
        }
    }

    override suspend fun getAllPositions(): List<PlaybackPosition> = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(KEY_POSITIONS, null) ?: return@withContext emptyList()
            val type = object : TypeToken<List<PlaybackPosition>>() {}.type
            return@withContext gson.fromJson<List<PlaybackPosition>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    override suspend fun exportPositions(): String = withContext(Dispatchers.IO) {
        try {
            val positions = getAllPositions()
            return@withContext gson.toJson(positions)
        } catch (e: Exception) {
            return@withContext "[]"
        }
    }

    override suspend fun importPositions(jsonData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val type = object : TypeToken<List<PlaybackPosition>>() {}.type
            val importedPositions = gson.fromJson<List<PlaybackPosition>>(jsonData, type)
            
            val existingPositions = getAllPositions().toMutableList()
            
            // Merge positions, replacing duplicates
            importedPositions.forEach { imported ->
                if (isPositionValid(imported)) {
                    existingPositions.removeAll { it.videoId == imported.videoId }
                    existingPositions.add(imported)
                }
            }
            
            saveAllPositions(existingPositions)
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    private fun isPositionValid(position: PlaybackPosition): Boolean {
        val daysSinceWatched = (System.currentTimeMillis() - position.timestamp) / (24 * 60 * 60 * 1000)
        return daysSinceWatched <= config.retentionDays
    }

    private fun saveAllPositions(positions: List<PlaybackPosition>) {
        try {
            val json = gson.toJson(positions)
            prefs.edit().putString(KEY_POSITIONS, json).apply()
        } catch (e: Exception) {
            // Handle silently
        }
    }
}
