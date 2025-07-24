package net.bunnystream.android.demo.player

import net.bunnystream.android.demo.library.model.Video

sealed class VideoUiState {

    data object VideoUiEmpty : VideoUiState()

    data object VideoUiLoading : VideoUiState()

    data class VideoUiLoaded(
        val video: Video,
        val resumePosition: Long = 0L
    ) : VideoUiState()
}