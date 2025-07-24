package net.bunnystream.android.demo.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bunnystream.api.playback.PlaybackPosition

class SimpleResumePositionStorage(context: Context) {
    
    companion object {
        private const val PREF_NAME = "resume_positions"
        private const val KEY_POSITIONS = "positions"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    suspend fun savePosition(position: PlaybackPosition) = withContext(Dispatchers.IO) {
        try {
            val positions = getAllPositions().toMutableList()
            
            // Remove existing position for this video
            positions.removeAll { it.videoId == position.videoId }
            
            // Add new position
            positions.add(position)
            
            // Keep only last 100 positions to avoid memory issues
            if (positions.size > 100) {
                positions.sortByDescending { it.timestamp }
                positions.subList(100, positions.size).clear()
            }
            
            // Save back to preferences
            val json = gson.toJson(positions)
            prefs.edit().putString(KEY_POSITIONS, json).apply()
            
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    suspend fun getPosition(videoId: String): PlaybackPosition? = withContext(Dispatchers.IO) {
        try {
            return@withContext getAllPositions().find { it.videoId == videoId }
        } catch (e: Exception) {
            return@withContext null
        }
    }
    
    suspend fun getAllPositions(): List<PlaybackPosition> = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(KEY_POSITIONS, null) ?: return@withContext emptyList()
            val type = object : TypeToken<List<PlaybackPosition>>() {}.type
            return@withContext gson.fromJson<List<PlaybackPosition>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }
    
    suspend fun clearPosition(videoId: String) = withContext(Dispatchers.IO) {
        try {
            val positions = getAllPositions().toMutableList()
            positions.removeAll { it.videoId == videoId }
            
            val json = gson.toJson(positions)
            prefs.edit().putString(KEY_POSITIONS, json).apply()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    suspend fun clearAllPositions() = withContext(Dispatchers.IO) {
        try {
            prefs.edit().remove(KEY_POSITIONS).apply()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    suspend fun cleanupExpiredPositions(retentionDays: Int) = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val expiredTime = currentTime - (retentionDays * 24 * 60 * 60 * 1000L)
            
            val positions = getAllPositions().filter { it.timestamp > expiredTime }
            
            val json = gson.toJson(positions)
            prefs.edit().putString(KEY_POSITIONS, json).apply()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    suspend fun getPositionCount(): Int = withContext(Dispatchers.IO) {
        try {
            return@withContext getAllPositions().size
        } catch (e: Exception) {
            return@withContext 0
        }
    }
    
    suspend fun exportPositions(): String = withContext(Dispatchers.IO) {
        try {
            val positions = getAllPositions()
            return@withContext gson.toJson(positions)
        } catch (e: Exception) {
            return@withContext "[]"
        }
    }
    
    suspend fun importPositions(jsonData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val type = object : TypeToken<List<PlaybackPosition>>() {}.type
            val importedPositions = gson.fromJson<List<PlaybackPosition>>(jsonData, type)
            
            val existingPositions = getAllPositions().toMutableList()
            
            // Merge positions, replacing duplicates
            importedPositions.forEach { imported ->
                existingPositions.removeAll { it.videoId == imported.videoId }
                existingPositions.add(imported)
            }
            
            val json = gson.toJson(existingPositions)
            prefs.edit().putString(KEY_POSITIONS, json).apply()
            
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
