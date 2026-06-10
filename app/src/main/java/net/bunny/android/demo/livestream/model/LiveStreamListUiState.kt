package net.bunny.android.demo.livestream.model

sealed class LiveStreamListUiState {
    data object Empty : LiveStreamListUiState()
    data object Loading : LiveStreamListUiState()
    data class Loaded(val streams: List<LiveStreamUiModel>) : LiveStreamListUiState()
}
