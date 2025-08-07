package net.bunny.android.demo.player

import net.bunny.android.demo.library.model.Video

sealed class VideoUiState {

    data object VideoUiEmpty : VideoUiState()

    data object VideoUiLoading : VideoUiState()

    data class VideoUiLoaded(
        val video: Video,
        val resumePosition: Long = 0L
    ) : VideoUiState()
}