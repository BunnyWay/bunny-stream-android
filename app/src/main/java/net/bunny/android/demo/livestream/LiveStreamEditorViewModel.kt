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
import net.bunny.api.livestream.domain.model.LiveStreamThumbnail
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

        /**
         * A trailer video is attached and can be previewed.
         *
         * @param deletable true only when this video was uploaded from the editor this session, so
         *        "Delete" may remove it from the library. For an existing stream's trailer or a
         *        video picked from the library, this is false — those are only *deselected*, never
         *        deleted from the library.
         */
        data class Ready(val videoId: String, val deletable: Boolean) : TrailerState

        /** The most recent upload failed. */
        data class Failed(val message: String) : TrailerState
    }

    /** State of the stream's generated-thumbnails list (edit mode only). */
    sealed interface ThumbnailListState {
        /** Not requested yet (e.g. create mode). */
        data object Idle : ThumbnailListState

        /** A list/delete request is running. */
        data object Loading : ThumbnailListState

        /** Loaded thumbnails (may be empty). */
        data class Loaded(val items: List<LiveStreamThumbnail>) : ThumbnailListState

        /** The last list request failed. */
        data class Failed(val message: String) : ThumbnailListState
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
        /** Generated thumbnails for the loaded stream (list/preview/delete). */
        val thumbnails: ThumbnailListState = ThumbnailListState.Idle,
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
                        ?.let { TrailerState.Ready(it, deletable = false) }
                        ?: TrailerState.None
                    mutableUiState.update {
                        it.copy(loading = false, stream = stream, trailer = trailer)
                    }
                    loadThumbnails(streamId)
                },
            )
        }
    }

    /**
     * Re-fetches the created stream so the summary screen reflects its current status. A live stream
     * is single-use, so after the user goes live and finishes (returning to this screen) its status
     * has moved on from `CREATED` — refreshing here lets the UI hide "Go live now" accordingly.
     */
    fun refreshCreatedStream() {
        val streamId = mutableUiState.value.createdStream?.id ?: return
        Log.d(TAG, "refreshCreatedStream id=$streamId")
        viewModelScope.launch {
            repository.getLiveStream(libraryId, streamId).fold(
                ifLeft = { message -> Log.w(TAG, "refreshCreatedStream failed: $message") },
                ifRight = { stream ->
                    Log.d(TAG, "refreshCreatedStream ok — status=${stream.status}")
                    mutableUiState.update { it.copy(createdStream = stream) }
                },
            )
        }
    }

    /** Fetches the stream's generated thumbnails into [UiState.thumbnails]. */
    fun loadThumbnails(streamId: String) {
        Log.d(TAG, "loadThumbnails id=$streamId")
        mutableUiState.update { it.copy(thumbnails = ThumbnailListState.Loading) }
        viewModelScope.launch {
            repository.listLiveStreamThumbnails(libraryId, streamId).fold(
                ifLeft = { message ->
                    Log.w(TAG, "listThumbnails failed: $message")
                    mutableUiState.update {
                        it.copy(thumbnails = ThumbnailListState.Failed(message))
                    }
                },
                ifRight = { items ->
                    Log.d(TAG, "listThumbnails ok — ${items.size} item(s)")
                    mutableUiState.update {
                        it.copy(thumbnails = ThumbnailListState.Loaded(items))
                    }
                },
            )
        }
    }

    /** Deletes the stream's custom thumbnail, then refreshes the list. */
    fun deleteThumbnail(streamId: String) {
        Log.d(TAG, "deleteThumbnail id=$streamId")
        mutableUiState.update { it.copy(thumbnails = ThumbnailListState.Loading) }
        viewModelScope.launch {
            repository.deleteLiveStreamThumbnail(libraryId, streamId).fold(
                ifLeft = { message ->
                    Log.w(TAG, "deleteThumbnail failed: $message")
                    mutableUiState.update { it.copy(error = message) }
                    loadThumbnails(streamId)
                },
                ifRight = {
                    Log.d(TAG, "deleteThumbnail ok")
                    loadThumbnails(streamId)
                },
            )
        }
    }

    fun save(
        streamId: String?,
        request: LiveStreamCreateRequest,
        thumbnail: ThumbnailSource? = null,
        // Client-side broadcast option (not a Bunny stream property), remembered locally per stream.
        dualPublish: Boolean = false,
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
                        App.di.dualPublishPreferences.setDualPublish(created.id, dualPublish)
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
                        App.di.dualPublishPreferences.setDualPublish(streamId, dualPublish)
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
            mutableUiState.update { it.copy(trailer = TrailerState.Ready(videoId, deletable = true)) }
        }

        override fun onUploadError(error: UploadError, videoId: String?) {
            Log.w(TAG, "trailer upload failed: $error")
            mutableUiState.update { it.copy(trailer = TrailerState.Failed(error.toString())) }
        }

        override fun onUploadCancelled(videoId: String) {
            mutableUiState.update { it.copy(trailer = TrailerState.None) }
        }
    }

    /**
     * Selects an existing library video (e.g. chosen from the trailer picker) as the pre-stream
     * trailer. Marked non-deletable: it's an existing library asset, so removing it from the stream
     * must not delete the video.
     */
    fun selectTrailer(videoId: String) {
        Log.d(TAG, "selectTrailer videoId=$videoId")
        mutableUiState.update { it.copy(trailer = TrailerState.Ready(videoId, deletable = false)) }
    }

    /** Deselects the trailer without deleting the underlying library video. */
    fun clearTrailer() {
        Log.d(TAG, "clearTrailer")
        mutableUiState.update { it.copy(trailer = TrailerState.None) }
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
