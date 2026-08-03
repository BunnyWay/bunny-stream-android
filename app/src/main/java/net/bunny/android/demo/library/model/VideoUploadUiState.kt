package net.bunny.android.demo.library.model

import net.bunny.api.upload.model.PauseState

sealed class VideoUploadUiState {

    object NotUploading : VideoUploadUiState()

    object Preparing : VideoUploadUiState()

    data class Uploading(val progress: Int, val pauseState: PauseState) : VideoUploadUiState()

    /**
     * @param retryable the failure was transient *and* the upload can be picked up where it
     *   stopped, so the UI can offer "retry" meaning "continue" rather than "upload it all again".
     */
    data class UploadError(
        val message: String,
        val retryable: Boolean = false,
    ) : VideoUploadUiState()
}
