package net.bunny.android.demo.player

import net.bunny.android.demo.library.model.Video

sealed class VideoUiState {

    data object VideoUiEmpty : VideoUiState()

    data object VideoUiLoading : VideoUiState()

    data class VideoUiLoaded(
        val video: Video,
    ) : VideoUiState()

    /**
     * Metadata could not be fetched (e.g. the video belongs to a library the configured
     * access key can't read). Playback is NOT attempted — the player would issue the
     * same play-data call with the same credentials and fail identically, so the UI
     * explains the failure instead.
     */
    data class VideoUiLoadFailed(
        val message: String?,
    ) : VideoUiState()
}