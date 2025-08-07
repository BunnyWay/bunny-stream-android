package net.bunny.android.demo.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoStatus
import net.bunny.api.BunnyStreamApi
import org.openapitools.client.models.VideoModel
import org.openapitools.client.models.VideoPlayDataModelVideo
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PlayerViewModel : ViewModel() {

    companion object {
        private const val TAG = "v"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val mutableUiState: MutableStateFlow<VideoUiState> =
        MutableStateFlow(VideoUiState.VideoUiEmpty)
    val uiState = mutableUiState.asStateFlow()

    private val mutableErrorState: MutableSharedFlow<Error?> = MutableSharedFlow()
    val errorState = mutableErrorState.asSharedFlow()

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    init {
        Log.d(TAG, "<init> $this")
    }

    fun loadVideo(videoId: String, libraryId: Long?) {
        Log.d(TAG, "loadVideo videoId=$videoId")

        val providedLibraryId = libraryId ?: BunnyStreamApi.libraryId

        if (libraryId == -1L || !BunnyStreamApi.isInitialized()) {
            return
        }

        mutableUiState.value = VideoUiState.VideoUiLoading

        scope.launch {
            try {
                val response =
                    BunnyStreamApi.getInstance().videosApi.videoGetVideoPlayData(
                        providedLibraryId,
                        videoId
                    ).video?.toVideoModel()!!
                // Load saved progress
                val progressResult = BunnyStreamApi.getInstance().progressRepository
                    .getProgress(providedLibraryId, videoId)

                val resumePosition = progressResult.fold(
                    ifLeft = { 0L },
                    ifRight = { it }
                )

                val video = response.toVideo()
                mutableUiState.value = VideoUiState.VideoUiLoaded(video, resumePosition)
            } catch (e: Exception) {
                 Log.e(TAG, "Error loading video: ${e.message}")
                e.printStackTrace()
                mutableErrorState.emit(Error("Error loading video: ${e.message}"))
                mutableUiState.value = VideoUiState.VideoUiEmpty
            }
        }

    }

    fun onErrorDismissed() = viewModelScope.launch {
        mutableErrorState.emit(null)
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

    fun VideoPlayDataModelVideo.toVideoModel(): VideoModel = VideoModel(
        videoLibraryId        = this.videoLibraryId,
        guid                  = this.guid,
        title                 = this.title,
        dateUploaded          = this.dateUploaded,
        views                 = this.views,
        isPublic              = this.isPublic,
        length                = this.length,
        status                = this.status,
        framerate             = this.framerate,
        rotation              = this.rotation,
        width                 = this.width,
        height                = this.height,
        availableResolutions  = this.availableResolutions,
        outputCodecs          = this.outputCodecs,
        thumbnailCount        = this.thumbnailCount,
        encodeProgress        = this.encodeProgress,
        storageSize           = this.storageSize,
        captions               = this.captions,
        hasMP4Fallback        = this.hasMP4Fallback,
        collectionId          = this.collectionId,
        thumbnailFileName     = this.thumbnailFileName,
        averageWatchTime      = this.averageWatchTime,
        totalWatchTime        = this.totalWatchTime,
        category              = this.category,
        chapters              = this.chapters,
        moments               = this.moments,
        metaTags              = this.metaTags,
        transcodingMessages   = this.transcodingMessages
    )

    private val Long?.inMb: Double?
        get() = this?.div(1024.0 * 1024.0)
}