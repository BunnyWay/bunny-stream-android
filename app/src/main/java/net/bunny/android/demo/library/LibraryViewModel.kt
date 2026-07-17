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
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoListUiState
import net.bunny.android.demo.library.model.VideoStatus
import net.bunny.android.demo.library.model.VideoUploadUiState
import net.bunny.api.BunnyStreamApi
import net.bunny.api.upload.model.UploadError
import net.bunny.api.upload.service.PauseState
import net.bunny.api.upload.service.UploadListener
import org.openapitools.client.models.VideoModel
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

    private var uploadInProgressId: String? = null

    private var enrichJob: Job? = null

    private val uploadListener = object : UploadListener {
        override fun onUploadError(error: UploadError, videoId: String?) {
            Log.d(TAG, "onVideoUploadError: $error")
            mutableUploadUiState.value = VideoUploadUiState.UploadError(error.toString())
            uploadInProgressId = null
        }

        override fun onUploadDone(videoId: String) {
            Log.d(TAG, "onVideoUploadDone")
            loadLibrary()
            mutableUploadUiState.value = VideoUploadUiState.NotUploading
            uploadInProgressId = null
        }

        override fun onUploadStarted(uploadId: String, videoId: String) {
            Log.d(TAG, "onVideoUploadStarted: uploadId=$uploadId")
            uploadInProgressId = uploadId
        }

        override fun onProgressUpdated(percentage: Int, videoId: String, pauseState: PauseState) {
            Log.d(TAG, "onUploadProgress: percentage=$percentage")
            mutableUploadUiState.value = VideoUploadUiState.Uploading(percentage, pauseState)
        }

        override fun onUploadCancelled(videoId: String) {
            Log.d(TAG, "onVideoUploadCancelled")
            mutableUploadUiState.value = VideoUploadUiState.NotUploading
            uploadInProgressId = null
        }
    }

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    var useTusUpload by mutableStateOf(false)
        private set

    init {
        Log.d(TAG, "<init> $this")
        App.di.videoUploadService.uploadListener = uploadListener
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
                val response = App.di.streamSdk.videosApi.videoList(
                    libraryId = libraryId,
                    page = null,
                    itemsPerPage = null,
                    search = null,
                    collection = null,
                    orderBy = null
                )
                val loadedVideos = response.items?.map { it.toVideo() } ?: listOf()
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
                    Log.w(TAG, "Failed to fetch videos")
                    e.printStackTrace()
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

    fun uploadVideo(videoUri: Uri) {
        Log.d(TAG, "uploadVideo uri=$videoUri useTusUpload=$useTusUpload")
        mutableUploadUiState.value = VideoUploadUiState.Preparing

        if (useTusUpload) {
            App.di.tusVideoUploadService.uploadListener = uploadListener
            App.di.tusVideoUploadService.uploadVideo(libraryId, videoUri)
        } else {
            App.di.videoUploadService.uploadListener = uploadListener
            App.di.videoUploadService.uploadVideo(libraryId, videoUri)
        }
    }

    fun clearUploadError() {
        mutableUploadUiState.value = VideoUploadUiState.NotUploading
    }

    fun cancelUpload() {
        Log.d(TAG, "cancelUpload: uploadInProgressId=$uploadInProgressId")
        uploadInProgressId?.let {
            if (useTusUpload) {
                App.di.tusVideoUploadService.cancelUpload(it)
            } else {
                App.di.videoUploadService.cancelUpload(it)
            }
        }
    }

    fun pauseResumeUpload() {
        Log.d(TAG, "pauseResumeUpload: uploadInProgressId=$uploadInProgressId")
        uploadInProgressId?.let {
            if (useTusUpload) {
                val uploadState = mutableUploadUiState.value as? VideoUploadUiState.Uploading
                when (uploadState?.pauseState) {
                    PauseState.Paused -> App.di.tusVideoUploadService.resumeUpload(it)
                    PauseState.Uploading -> App.di.tusVideoUploadService.pauseUpload(it)
                    else -> { /* no-op */
                    }
                }
            }
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
                val result = App.di.streamSdk.videosApi.videoDeleteVideo(libraryId, video.id)

                if (result.success == true) {
                    Log.d(TAG, "Video deleted")
                    val loadedVideos =
                        (mutableUiState.value as? VideoListUiState.VideoListUiLoaded)?.videos
                            ?: emptyList()
                    notifyVideosUpdated(loadedVideos - video)
                } else {
                    Log.e(TAG, "Couldn't delete video: $result")
                    mutableErrorState.emit(Error("${result.statusCode} ${result.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting video: ${e.message}")
                e.printStackTrace()
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

    private fun VideoModel.toVideo(): Video {
        return Video(
            id = guid ?: UUID.randomUUID().toString(),
            name = title ?: "N/A",
            duration = length?.toDuration(DurationUnit.SECONDS).toString(),
            status = when (status?.value) {
                null -> VideoStatus.ERROR
                0 -> VideoStatus.CREATED
                1 -> VideoStatus.UPLOADED
                2 -> VideoStatus.PROCESSING
                3 -> VideoStatus.TRANSCODING
                4 -> VideoStatus.FINISHED
                5 -> VideoStatus.ERROR
                6 -> VideoStatus.UPLOAD_FAILED
                else -> VideoStatus.ERROR
            },
            size = storageSize?.inMb ?: 0.0,
            viewCount = views?.toString() ?: "N/A",
        )
    }

    private val Long?.inMb: Double?
        get() = this?.div(1024.0 * 1024.0)
}