package net.bunnystream.android.demo.resume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bunnystream.android.demo.App
import net.bunnystream.api.playback.DefaultPlaybackPositionManager
import net.bunnystream.api.playback.PlaybackPosition
import net.bunnystream.api.playback.ResumeConfig
import net.bunnystream.bunnystreamplayer.DefaultBunnyPlayer

class ResumePositionViewModel : ViewModel() {

    private val _positions = MutableStateFlow<List<PlaybackPosition>>(emptyList())
    val positions = _positions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _exportData = MutableStateFlow("")
    val exportData = _exportData.asStateFlow()

    private val context = App.di.context
    private val bunnyPlayer = DefaultBunnyPlayer.getInstance(context)

    // Create a position manager for direct access
    private val positionManager = DefaultPlaybackPositionManager(
        context = context,
        config = ResumeConfig()
    )

    fun loadPositions() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Try to get from bunnyPlayer first, fallback to direct manager
                if (bunnyPlayer.positionManager != null) {
                    bunnyPlayer.getAllSavedPositions { positions ->
                        _positions.value = positions
                        _isLoading.value = false
                    }
                } else {
                    // Use direct position manager
                    val positions = positionManager.getAllPositions()
                    _positions.value = positions
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _positions.value = emptyList()
            }
        }
    }

    fun deletePosition(videoId: String) {
        viewModelScope.launch {
            try {
                // Try bunnyPlayer first, fallback to direct manager
                if (bunnyPlayer.positionManager != null) {
                    bunnyPlayer.clearSavedPosition(videoId)
                } else {
                    positionManager.clearPosition(videoId)
                }
                loadPositions() // Refresh the list
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteAllPositions() {
        viewModelScope.launch {
            try {
                // Try bunnyPlayer first, fallback to direct manager
                if (bunnyPlayer.positionManager != null) {
                    bunnyPlayer.clearAllSavedPositions()
                } else {
                    positionManager.clearAllPositions()
                }
                _positions.value = emptyList()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun exportPositions() {
        viewModelScope.launch {
            try {
                // Try bunnyPlayer first, fallback to direct manager
                if (bunnyPlayer.positionManager != null) {
                    bunnyPlayer.exportPositions { jsonData ->
                        _exportData.value = jsonData
                    }
                } else {
                    val jsonData = positionManager.exportPositions()
                    _exportData.value = jsonData
                }
            } catch (e: Exception) {
                _exportData.value = "[]"
            }
        }
    }

    fun importPositions(jsonData: String) {
        viewModelScope.launch {
            try {
                // Try bunnyPlayer first, fallback to direct manager
                if (bunnyPlayer.positionManager != null) {
                    bunnyPlayer.importPositions(jsonData) { success ->
                        if (success) {
                            loadPositions() // Refresh the list
                        }
                    }
                } else {
                    val success = positionManager.importPositions(jsonData)
                    if (success) {
                        loadPositions() // Refresh the list
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun cleanupExpiredPositions() {
        viewModelScope.launch {
            try {
                // Try bunnyPlayer first, fallback to direct manager
                if (bunnyPlayer.positionManager != null) {
                    bunnyPlayer.cleanupExpiredPositions()
                } else {
                    positionManager.cleanupExpiredPositions()
                }
                loadPositions() // Refresh the list
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}