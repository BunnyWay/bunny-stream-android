package net.bunny.android.demo.library

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bunny.api.error.fold
import net.bunny.android.demo.App
import net.bunny.android.demo.di.ActiveUpload
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoListUiState
import net.bunny.android.demo.library.model.VideoStatus
import net.bunny.android.demo.library.model.VideoUploadUiState
import net.bunny.api.BunnyStreamApi
import net.bunny.api.upload.VideoUploader
import net.bunny.api.upload.model.PauseState
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.getOrNull
import net.bunny.api.video.domain.model.Video as SdkVideo
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LibraryViewModel : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"

        // Poll interval for refreshing the list while any video is still being
        // ingested/encoded, so status pills update without leaving the screen.
        // The tick itself is driven by the screen (lifecycle-gated) — see LibraryRoute.
        const val STATUS_POLL_INTERVAL_MS = 5_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val mutableUiState: MutableStateFlow<VideoListUiState> = MutableStateFlow(
        VideoListUiState.VideoListUiEmpty
    )
    val uiState = mutableUiState.asStateFlow()

    private val mutableUploadUiState: MutableStateFlow<VideoUploadUiState> = MutableStateFlow(
        VideoUploadUiState.NotUploading
    )
    val uploadUiState = mutableUploadUiState.asStateFlow()

    private val mutableErrorState: MutableSharedFlow<Error?> = MutableSharedFlow()
    val errorState = mutableErrorState.asSharedFlow()

    private var uploadJob: Job? = null

    private var enrichJob: Job? = null

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    var useTusUpload by mutableStateOf(false)
        private set

    init {
        Log.d(TAG, "<init> $this")
        // The transfer runs inside the SDK, so one may still be going from an earlier instance of
        // this screen. Re-attach to it instead of showing an idle upload area over a live upload.
        App.di.activeUpload?.let(::attachTo)
    }

    fun loadLibrary() {
        Log.d(TAG, "loadVideoList")

        if (libraryId == -1L || !BunnyStreamApi.isInitialized()) {
            return
        }

        mutableUiState.value = VideoListUiState.VideoListUiLoading
        fetchLibrary(silent = false)
    }

    /**
     * Called by the screen every [STATUS_POLL_INTERVAL_MS] while it is at least STARTED.
     * Refreshes silently only when some video is still being ingested/encoded, so the
     * poll stops costing anything the moment everything settles.
     */
    fun onStatusPollTick() {
        val videos = (mutableUiState.value as? VideoListUiState.VideoListUiLoaded)?.videos
            ?: return
        if (videos.any { it.status in VideoStatus.TRANSITIONAL }) {
            refreshLibrarySilently()
        }
    }

    /**
     * Re-fetches the list without flipping the UI into the loading state, so the visible
     * list doesn't blink while we poll for processing/transcoding progress.
     */
    private fun refreshLibrarySilently() {
        Log.d(TAG, "refreshLibrarySilently")

        if (libraryId == -1L || !BunnyStreamApi.isInitialized()) {
            return
        }

        fetchLibrary(silent = true)
    }

    private fun fetchLibrary(silent: Boolean) {
        scope.launch {
            try {
                val page = App.di.streamSdk.videoRepository.listVideos(libraryId)
                    .getOrNull()
                    ?: return@launch handleFetchFailure(silent)
                val loadedVideos = page.items.map { it.toVideo() }
                if (silent && mutableUiState.value == VideoListUiState.VideoListUiLoading) {
                    // A full (user-triggered) reload is in flight — let its result win
                    // instead of clobbering the loading state with a possibly older list.
                    return@launch
                }
                // Keep thumbnails already resolved — a refresh only needs fresh
                // status/views/size, not another settings sweep.
                val previousThumbnails =
                    (mutableUiState.value as? VideoListUiState.VideoListUiLoaded)
                        ?.videos?.associate { it.id to it.thumbnailUrl }
                        ?: emptyMap()
                val merged = loadedVideos.map { video ->
                    val known = previousThumbnails[video.id]
                    if (known != null) video.copy(thumbnailUrl = known) else video
                }
                notifyVideosUpdated(merged)
                enrichMissingThumbnails(merged)
            } catch (e: Exception) {
                if (silent) {
                    // Transient poll failure — keep the current list; the screen's
                    // lifecycle-gated tick will try again while the screen is visible.
                    Log.w(TAG, "Silent refresh failed: $e")
                } else {
                    Log.w(TAG, "Failed to fetch videos", e)
                    mutableErrorState.emit(Error(e.message ?: e.toString()))
                }
            }
        }
    }

    /**
     * Resolves thumbnails only for videos that don't have one yet, then merges the
     * results into the *current* list by id. Merging (instead of replacing the whole
     * list with the snapshot this sweep started from) keeps a slow sweep from rolling
     * back statuses that a newer refresh already advanced.
     */
    private fun enrichMissingThumbnails(videos: List<Video>) {
        val missing = videos.filter { it.thumbnailUrl == null }
        if (missing.isEmpty()) {
            return
        }
        Log.d(TAG, "enrichMissingThumbnails count=${missing.size}")

        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                missing.mapNotNull { video ->
                    BunnyStreamApi.getInstance()
                        .fetchPlayerSettings(libraryId, video.id)
                        .fold(
                            onErr = {
                                Log.w(TAG, "Failed to fetch details for ${video.id}")
                                null
                            },
                            onOk = { video.id to it.thumbnailUrl }
                        )
                }.toMap()
            }
            if (resolved.isEmpty()) return@launch

            val current = (mutableUiState.value as? VideoListUiState.VideoListUiLoaded)
                ?.videos ?: return@launch
            val merged = current.map { video ->
                if (video.thumbnailUrl == null) {
                    resolved[video.id]?.let { video.copy(thumbnailUrl = it) } ?: video
                } else {
                    video
                }
            }
            mutableUiState.value = VideoListUiState.VideoListUiLoaded(merged)
        }
    }

    fun onErrorDismissed() = viewModelScope.launch {
        mutableErrorState.emit(null)
    }

    private fun uploaderFor(useTus: Boolean): VideoUploader =
        if (useTus) App.di.streamSdk.tusVideoUploader else App.di.streamSdk.videoUploader

    fun uploadVideo(videoUri: Uri) {
        Log.d(TAG, "uploadVideo uri=$videoUri useTusUpload=$useTusUpload")
        mutableUploadUiState.value = VideoUploadUiState.Preparing

        val uploadId = uploaderFor(useTusUpload).startUpload(libraryId, videoUri)
        val active = ActiveUpload(uploadId, libraryId, videoUri, useTusUpload)
        App.di.activeUpload = active
        attachTo(active)
    }

    /**
     * Picks the failed transfer up where it stopped rather than starting a second upload of the
     * same file. Only offered when the SDK can actually do it — see
     * [VideoUploadUiState.UploadError.retryable].
     */
    fun retryUpload() {
        val active = App.di.activeUpload ?: return
        val videoId = active.videoId
        Log.d(TAG, "retryUpload uploadId=${active.uploadId} videoId=$videoId")

        val uploader = uploaderFor(active.useTus)
        val retryId = if (videoId == null) {
            // The previous attempt never got as far as creating the video, so there is nothing to
            // continue — start over.
            uploader.startUpload(active.libraryId, active.videoUri)
        } else {
            uploader.continueUpload(active.libraryId, videoId, active.videoUri)
        }

        val next = active.copy(uploadId = retryId)
        App.di.activeUpload = next
        mutableUploadUiState.value = VideoUploadUiState.Preparing
        attachTo(next)
    }

    /** Observes [active] without starting or stopping anything; safe to call on every screen entry. */
    private fun attachTo(active: ActiveUpload) {
        uploadJob?.cancel()
        val events = uploaderFor(active.useTus).observeUpload(active.uploadId)
        if (events == null) {
            // Finished long enough ago that the SDK no longer remembers it.
            Log.d(TAG, "no upload to attach to for uploadId=${active.uploadId}")
            App.di.activeUpload = null
            mutableUploadUiState.value = VideoUploadUiState.NotUploading
            return
        }
        uploadJob = viewModelScope.launch { events.collect(::onUploadEvent) }
    }

    private fun onUploadEvent(event: UploadEvent) {
        when (event) {
            is UploadEvent.Started -> {
                Log.d(TAG, "upload started: uploadId=${event.uploadId} videoId=${event.videoId}")
                rememberVideoId(event.videoId)
            }

            is UploadEvent.Progress -> {
                // Also captured here, not only on Started: attaching to an upload already in
                // flight starts from the most recent event, so Started may never arrive.
                rememberVideoId(event.videoId)
                mutableUploadUiState.value =
                    VideoUploadUiState.Uploading(event.percentage, event.pauseState)
            }

            is UploadEvent.Completed -> {
                Log.d(TAG, "upload done: videoId=${event.videoId}")
                loadLibrary()
                finishUpload(VideoUploadUiState.NotUploading)
            }

            is UploadEvent.Cancelled -> {
                Log.d(TAG, "upload cancelled: videoId=${event.videoId}")
                finishUpload(VideoUploadUiState.NotUploading)
            }

            is UploadEvent.Failed -> {
                Log.w(TAG, "upload failed: ${event.error}")
                // A transient failure on the resumable path can be continued from the stored
                // offset — so keep the handle instead of dropping it.
                val active = App.di.activeUpload
                val retryable = !event.error.isTerminal && active?.useTus == true
                mutableUploadUiState.value =
                    VideoUploadUiState.UploadError(event.error.message, retryable)
                if (!retryable) App.di.activeUpload = null
            }
        }
    }

    /** The video id is what [retryUpload] needs to continue rather than start over. */
    private fun rememberVideoId(videoId: String) {
        val active = App.di.activeUpload ?: return
        if (active.videoId != videoId) {
            App.di.activeUpload = active.copy(videoId = videoId)
        }
    }

    private fun finishUpload(state: VideoUploadUiState) {
        mutableUploadUiState.value = state
        App.di.activeUpload = null
    }

    fun clearUploadError() {
        App.di.activeUpload = null
        mutableUploadUiState.value = VideoUploadUiState.NotUploading
    }

    fun cancelUpload() {
        val active = App.di.activeUpload ?: return
        Log.d(TAG, "cancelUpload: uploadId=${active.uploadId}")
        uploaderFor(active.useTus).cancelUpload(active.uploadId)
    }

    fun pauseResumeUpload() {
        val active = App.di.activeUpload ?: return
        val uploading = mutableUploadUiState.value as? VideoUploadUiState.Uploading ?: return
        Log.d(TAG, "pauseResumeUpload: uploadId=${active.uploadId} state=${uploading.pauseState}")

        val uploader = uploaderFor(active.useTus)
        when (uploading.pauseState) {
            PauseState.Paused -> uploader.resumeUpload(active.uploadId)
            PauseState.Uploading -> uploader.pauseUpload(active.uploadId)
            PauseState.Unsupported -> Unit
        }
    }

    fun onTusUploadOptionChanged(enabled: Boolean) {
        Log.d(TAG, "onTusUploadOptionChanged enabled=$enabled")
        useTusUpload = enabled
    }

    fun onDeleteVideo(video: Video) {
        Log.d(TAG, "onDeleteVideo video=$video")
        scope.launch {
            try {
                val result = App.di.streamSdk.videoRepository.deleteVideo(libraryId, video.id)

                if (result is BunnyResult.Ok) {
                    Log.d(TAG, "Video deleted")
                    val loadedVideos =
                        (mutableUiState.value as? VideoListUiState.VideoListUiLoaded)?.videos
                            ?: emptyList()
                    notifyVideosUpdated(loadedVideos - video)
                } else if (result is BunnyResult.Err) {
                    Log.e(TAG, "Couldn't delete video: ${result.message}")
                    mutableErrorState.emit(Error(result.message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting video", e)
                mutableErrorState.emit(Error("Error deleting video: ${e.message}"))
            }
        }
    }

    private fun notifyVideosUpdated(loadedVideos: List<Video>) {
        if (loadedVideos.isEmpty()) {
            mutableUiState.value = VideoListUiState.VideoListUiEmpty
        } else {
            mutableUiState.value = VideoListUiState.VideoListUiLoaded(loadedVideos)
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared $this")
        scope.cancel()
        super.onCleared()
    }

    private fun SdkVideo.toVideo(): Video {
        return Video(
            id = id.ifBlank { UUID.randomUUID().toString() },
            name = title.ifBlank { "N/A" },
            duration = lengthSeconds.toDuration(DurationUnit.SECONDS).toString(),
            status = when (status.value) {
                0 -> VideoStatus.CREATED
                1 -> VideoStatus.UPLOADED
                2 -> VideoStatus.PROCESSING
                3 -> VideoStatus.TRANSCODING
                4 -> VideoStatus.FINISHED
                6 -> VideoStatus.UPLOAD_FAILED
                else -> VideoStatus.ERROR
            },
            size = storageSizeBytes.inMb ?: 0.0,
            viewCount = views.toString(),
        )
    }

    /** A failed listing is silent while polling and surfaced when the user asked for it. */
    private suspend fun handleFetchFailure(silent: Boolean) {
        if (silent) {
            Log.w(TAG, "Silent refresh failed")
        } else {
            Log.w(TAG, "Failed to fetch videos")
            mutableErrorState.emit(Error("Could not load the video library"))
        }
    }

    private val Long?.inMb: Double?
        get() = this?.div(1024.0 * 1024.0)
}