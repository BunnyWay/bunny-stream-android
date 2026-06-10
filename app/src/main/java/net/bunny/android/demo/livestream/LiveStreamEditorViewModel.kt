package net.bunny.android.demo.livestream

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.bunny.android.demo.App
import net.bunny.api.BunnyStreamApi
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.upload.model.UploadError
import net.bunny.api.upload.service.PauseState
import net.bunny.api.upload.service.UploadListener

/**
 * Backs [LiveStreamEditorRoute]. Loads the stream being edited (edit mode) and submits
 * create/update requests through the SDK's
 * [net.bunny.api.livestream.domain.LiveStreamRepository].
 */
class LiveStreamEditorViewModel : ViewModel() {

    companion object {
        private const val TAG = "BunnyLive/EditorVM"
    }

    /** A thumbnail chosen in the editor, applied once the stream exists (post create/update). */
    sealed interface ThumbnailSource {
        /** A local image picked from the device. */
        data class Local(val bytes: ByteArray, val contentType: String) : ThumbnailSource

        /** A remote image URL the server fetches. */
        data class Url(val url: String) : ThumbnailSource
    }

    /** State of the pre-stream trailer video shown in the editor. */
    sealed interface TrailerState {
        /** No trailer attached. */
        data object None : TrailerState

        /** A trailer video is being uploaded to the library. */
        data class Uploading(val percentage: Int) : TrailerState

        /** A trailer video is uploaded/available and can be previewed. */
        data class Ready(val videoId: String) : TrailerState

        /** The most recent upload failed. */
        data class Failed(val message: String) : TrailerState
    }

    data class UiState(
        val stream: LiveStream? = null,
        val loading: Boolean = false,
        val saving: Boolean = false,
        /** Set after a successful *update* — the editor pops back. */
        val saved: Boolean = false,
        /** Set after a successful *create* — the editor shows the links summary. */
        val createdStream: LiveStream? = null,
        /** Pre-stream trailer video state (upload/preview/delete). */
        val trailer: TrailerState = TrailerState.None,
        val error: String? = null,
    )

    private val mutableUiState = MutableStateFlow(UiState())
    val uiState = mutableUiState.asStateFlow()

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    private val repository
        get() = App.di.streamSdk.liveStreamRepository

    fun load(streamId: String) {
        // Only fetch once per editor instance — recompositions/back-navigation shouldn't refetch.
        if (mutableUiState.value.stream != null || mutableUiState.value.loading) return

        Log.d(TAG, "load id=$streamId")
        mutableUiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            repository.getLiveStream(libraryId, streamId).fold(
                ifLeft = { message ->
                    Log.w(TAG, "getLiveStream failed: $message")
                    mutableUiState.update { it.copy(loading = false, error = message) }
                },
                ifRight = { stream ->
                    Log.d(TAG, "getLiveStream ok — title='${stream.title}'")
                    val trailer = stream.preStreamTrailerVideoId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { TrailerState.Ready(it) }
                        ?: TrailerState.None
                    mutableUiState.update {
                        it.copy(loading = false, stream = stream, trailer = trailer)
                    }
                },
            )
        }
    }

    fun save(
        streamId: String?,
        request: LiveStreamCreateRequest,
        thumbnail: ThumbnailSource? = null,
    ) {
        Log.d(TAG, "save id=$streamId request=$request thumbnail=${thumbnail?.let { it::class.simpleName }}")
        mutableUiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (streamId == null) {
                repository.createLiveStream(libraryId, request).fold(
                    ifLeft = { message ->
                        Log.w(TAG, "create failed: $message")
                        mutableUiState.update { it.copy(saving = false, error = message) }
                    },
                    ifRight = { created ->
                        Log.d(
                            TAG,
                            "create ok — id=${created.id} streamKey=${created.streamKey} " +
                                "hls=${created.playbackUrlHls}",
                        )
                        // Thumbnail is best-effort: a failure here shouldn't hide the created
                        // stream, but it's surfaced as a non-fatal error over the summary screen.
                        val thumbError = thumbnail?.let { applyThumbnail(created.id, it) }
                        mutableUiState.update {
                            it.copy(saving = false, createdStream = created, error = thumbError)
                        }
                    },
                )
            } else {
                repository.updateLiveStream(libraryId, streamId, request).fold(
                    ifLeft = { message ->
                        Log.w(TAG, "update failed: $message")
                        mutableUiState.update { it.copy(saving = false, error = message) }
                    },
                    ifRight = {
                        Log.d(TAG, "update ok")
                        val thumbError = thumbnail?.let { applyThumbnail(streamId, it) }
                        mutableUiState.update {
                            it.copy(saving = false, saved = thumbError == null, error = thumbError)
                        }
                    },
                )
            }
        }
    }

    /** Applies [thumbnail] to [streamId]; returns a user-facing error message, or null on success. */
    private suspend fun applyThumbnail(streamId: String, thumbnail: ThumbnailSource): String? {
        val result = when (thumbnail) {
            is ThumbnailSource.Local -> repository.uploadLiveStreamThumbnail(
                libraryId = libraryId,
                streamId = streamId,
                imageBytes = thumbnail.bytes,
                contentType = thumbnail.contentType,
            )

            is ThumbnailSource.Url -> repository.setLiveStreamThumbnail(
                libraryId = libraryId,
                streamId = streamId,
                thumbnailUrl = thumbnail.url,
            )
        }
        return result.fold(
            ifLeft = { message ->
                Log.w(TAG, "thumbnail failed: $message")
                "The stream was saved, but the thumbnail could not be set: $message"
            },
            ifRight = {
                Log.d(TAG, "thumbnail set")
                null
            },
        )
    }

    // region — Pre-stream trailer

    /** Forwards upload callbacks for the trailer video into [UiState.trailer]. */
    private val trailerUploadListener = object : UploadListener {
        override fun onUploadStarted(uploadId: String, videoId: String) {
            Log.d(TAG, "trailer upload started — videoId=$videoId")
        }

        override fun onProgressUpdated(percentage: Int, videoId: String, pauseState: PauseState) {
            mutableUiState.update { it.copy(trailer = TrailerState.Uploading(percentage)) }
        }

        override fun onUploadDone(videoId: String) {
            Log.d(TAG, "trailer upload done — videoId=$videoId")
            mutableUiState.update { it.copy(trailer = TrailerState.Ready(videoId)) }
        }

        override fun onUploadError(error: UploadError, videoId: String?) {
            Log.w(TAG, "trailer upload failed: $error")
            mutableUiState.update { it.copy(trailer = TrailerState.Failed(error.toString())) }
        }

        override fun onUploadCancelled(videoId: String) {
            mutableUiState.update { it.copy(trailer = TrailerState.None) }
        }
    }

    /** Uploads [videoUri] to the library as the pre-stream trailer; result surfaces via [UiState.trailer]. */
    fun uploadTrailer(videoUri: Uri) {
        Log.d(TAG, "uploadTrailer uri=$videoUri")
        mutableUiState.update { it.copy(trailer = TrailerState.Uploading(0)) }
        App.di.videoUploadService.uploadListener = trailerUploadListener
        App.di.videoUploadService.uploadVideo(libraryId, videoUri)
    }

    /** Deletes the uploaded trailer video from the library and clears the trailer. */
    fun deleteTrailer() {
        val current = mutableUiState.value.trailer as? TrailerState.Ready
        if (current == null) {
            mutableUiState.update { it.copy(trailer = TrailerState.None) }
            return
        }
        Log.d(TAG, "deleteTrailer videoId=${current.videoId}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = App.di.streamSdk.videosApi.videoDeleteVideo(libraryId, current.videoId)
                if (result.success == true) {
                    mutableUiState.update { it.copy(trailer = TrailerState.None) }
                } else {
                    mutableUiState.update {
                        it.copy(error = "Couldn't delete trailer: ${result.statusCode} ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteTrailer error", e)
                mutableUiState.update { it.copy(error = "Error deleting trailer: ${e.message}") }
            }
        }
    }

    // endregion

    fun onErrorDismissed() {
        mutableUiState.update { it.copy(error = null) }
    }
}
