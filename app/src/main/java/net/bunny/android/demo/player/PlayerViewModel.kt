package net.bunny.android.demo.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoStatus
import net.bunny.api.BunnyStreamApi
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.getOrNull
import net.bunny.api.video.domain.model.Video as SdkVideo
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PlayerViewModel : ViewModel() {

    companion object {
        private const val TAG = "v"

        // Tick interval for re-fetching metadata while the video is still being
        // processed; driven lifecycle-gated by PlayerRoute.
        const val STATUS_POLL_INTERVAL_MS = 5_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val mutableUiState: MutableStateFlow<VideoUiState> =
        MutableStateFlow(VideoUiState.VideoUiEmpty)
    val uiState = mutableUiState.asStateFlow()

    private val mutableErrorState: MutableSharedFlow<Error?> = MutableSharedFlow()
    val errorState = mutableErrorState.asSharedFlow()

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    private var lastVideoId: String? = null
    private var lastLibraryId: Long? = null

    init {
        Log.d(TAG, "<init> $this")
    }

    fun loadVideo(videoId: String, libraryId: Long?) {
        Log.d(TAG, "loadVideo videoId=$videoId")

        lastVideoId = videoId
        lastLibraryId = libraryId

        val providedLibraryId = libraryId ?: BunnyStreamApi.libraryId

        if (libraryId == -1L || !BunnyStreamApi.isInitialized()) {
            return
        }

        mutableUiState.value = VideoUiState.VideoUiLoading
        fetchVideo(videoId, providedLibraryId, silent = false)
    }

    /**
     * Called by the screen every [STATUS_POLL_INTERVAL_MS] while visible. Re-fetches
     * metadata only when the loaded video is still in a transitional state, so a video
     * that finishes encoding while the user waits starts playing without re-entering
     * the screen.
     */
    fun onStatusPollTick() {
        val videoId = lastVideoId ?: return
        val status = (mutableUiState.value as? VideoUiState.VideoUiLoaded)?.video?.status
            ?: return
        if (status in VideoStatus.TRANSITIONAL) {
            fetchVideo(videoId, lastLibraryId ?: BunnyStreamApi.libraryId, silent = true)
        }
    }

    private fun fetchVideo(videoId: String, providedLibraryId: Long, silent: Boolean) {
        scope.launch {
            val result = BunnyStreamApi.getInstance().videoRepository
                .fetchVideoPlayData(providedLibraryId, videoId)

            when (result) {
                is BunnyResult.Err -> handleFetchFailure(result.error.message, silent)
                is BunnyResult.Ok -> {
                    val sdkVideo = result.value.video
                        ?: return@launch handleFetchFailure(
                            "The response carried no video metadata",
                            silent,
                        )
                    mutableUiState.value = VideoUiState.VideoUiLoaded(sdkVideo.toVideo())
                }
            }
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared $this")
        scope.cancel()
        super.onCleared()
    }

    fun onErrorDismissed() = viewModelScope.launch {
        mutableErrorState.emit(null)
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

    /**
     * A failed fetch is silent while polling — the last known state stays on screen — and put in
     * front of the user when they were the ones waiting for it. Leaving the non-silent case
     * unhandled parks the screen on its loading spinner with nothing to retry.
     */
    private suspend fun handleFetchFailure(reason: String, silent: Boolean) {
        if (silent) {
            Log.w(TAG, "Silent metadata refresh failed: $reason")
            return
        }
        Log.e(TAG, "Error loading video: $reason")
        mutableErrorState.emit(Error("Error loading video: $reason"))
        mutableUiState.value = VideoUiState.VideoUiLoadFailed(reason)
    }

    private val Long?.inMb: Double?
        get() = this?.div(1024.0 * 1024.0)
}