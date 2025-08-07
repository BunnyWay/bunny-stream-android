package net.bunny.android.demo.library.model

sealed class VideoListUiState {

    data object VideoListUiEmpty : VideoListUiState()

    data object VideoListUiLoading : VideoListUiState()

    data class VideoListUiLoaded(
        val videos: List<Video>
    ) : VideoListUiState()
}
