package net.bunny.android.demo.livestream

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bunny.android.demo.App
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.livestream.model.LiveStreamListUiState
import net.bunny.android.demo.livestream.model.LiveStreamUiModel
import net.bunny.api.BunnyStreamApi
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest

/**
 * Drives the live streams management screen. Provides load + CRUD intents that call through to
 * the SDK's [net.bunny.api.livestream.domain.LiveStreamRepository], exposing UI state as
 * [LiveStreamListUiState] and one-shot errors via a [SharedFlow].
 */
class LiveStreamsViewModel : ViewModel() {

    companion object {
        private const val TAG = "BunnyLive/ListVM"
    }

    private val mutableUiState: MutableStateFlow<LiveStreamListUiState> =
        MutableStateFlow(LiveStreamListUiState.Empty)
    val uiState = mutableUiState.asStateFlow()

    private val mutableErrorState: MutableSharedFlow<Error?> = MutableSharedFlow()
    val errorState = mutableErrorState.asSharedFlow()

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    private val repository
        get() = App.di.streamSdk.liveStreamRepository

    fun load() {
        Log.d(TAG, "load() called — libraryId=$libraryId, apiInitialized=${BunnyStreamApi.isInitialized()}")
        if (libraryId == -1L || !BunnyStreamApi.isInitialized()) {
            Log.w(TAG, "load() aborted — library ID not set or API not initialized")
            return
        }

        mutableUiState.value = LiveStreamListUiState.Loading

        viewModelScope.launch {
            repository.listLiveStreams(libraryId).fold(
                ifLeft = { message ->
                    Log.w(TAG, "listLiveStreams failed: $message")
                    mutableErrorState.emit(Error(message))
                    notifyStreamsUpdated(emptyList())
                },
                ifRight = { list ->
                    Log.d(
                        TAG,
                        "listLiveStreams ok — items=${list.items.size}, " +
                            "total=${list.totalItems}, page=${list.currentPage}",
                    )
                    list.items.forEachIndexed { idx, stream ->
                        Log.d(
                            TAG,
                            "  [$idx] id=${stream.id} title='${stream.title}' " +
                                "status=${stream.status} public=${stream.isPublic} " +
                                "dvr=${stream.dvrEnabled} vod=${stream.recordVod} " +
                                "playbackUrlHls=${stream.playbackUrlHls}",
                        )
                    }
                    notifyStreamsUpdated(list.items.map { it.toUiModel() })
                }
            )
        }
    }

    fun createStream(request: LiveStreamCreateRequest) {
        Log.d(TAG, "createStream title=${request.title}")
        viewModelScope.launch {
            repository.createLiveStream(libraryId, request).fold(
                ifLeft = { message ->
                    Log.w(TAG, "Failed to create live stream: $message")
                    mutableErrorState.emit(Error(message))
                },
                ifRight = { created ->
                    Log.d(
                        TAG,
                        "createLiveStream ok — id=${created.id} title='${created.title}' " +
                            "status=${created.status} streamKey=${created.streamKey?.take(20)}…",
                    )
                    val existing = (mutableUiState.value as? LiveStreamListUiState.Loaded)?.streams
                        ?: emptyList()
                    notifyStreamsUpdated(listOf(created.toUiModel()) + existing)
                }
            )
        }
    }

    fun updateStream(streamId: String, request: LiveStreamCreateRequest) {
        Log.d(TAG, "updateStream id=$streamId title=${request.title}")
        viewModelScope.launch {
            repository.updateLiveStream(libraryId, streamId, request).fold(
                ifLeft = { message ->
                    Log.w(TAG, "Failed to update live stream: $message")
                    mutableErrorState.emit(Error(message))
                },
                ifRight = {
                    // The API doesn't echo the updated entity back, so re-fetch the row to refresh
                    // anything the server may have changed (e.g. derived status).
                    refreshSingle(streamId)
                }
            )
        }
    }

    fun deleteStream(stream: LiveStreamUiModel) {
        Log.d(TAG, "deleteStream id=${stream.id}")
        viewModelScope.launch {
            repository.deleteLiveStream(libraryId, stream.id).fold(
                ifLeft = { message ->
                    Log.w(TAG, "Failed to delete live stream: $message")
                    mutableErrorState.emit(Error(message))
                },
                ifRight = {
                    val remaining = (mutableUiState.value as? LiveStreamListUiState.Loaded)
                        ?.streams
                        ?.filterNot { it.id == stream.id }
                        ?: emptyList()
                    notifyStreamsUpdated(remaining)
                }
            )
        }
    }

    fun onErrorDismissed() = viewModelScope.launch {
        mutableErrorState.emit(null)
    }

    private fun refreshSingle(streamId: String) {
        viewModelScope.launch {
            repository.getLiveStream(libraryId, streamId).fold(
                ifLeft = {
                    // Fall back to a full reload — the row may have moved due to ordering changes.
                    load()
                },
                ifRight = { updated ->
                    val existing = (mutableUiState.value as? LiveStreamListUiState.Loaded)?.streams
                        ?: emptyList()
                    val replaced = existing.map {
                        if (it.id == streamId) updated.toUiModel() else it
                    }
                    notifyStreamsUpdated(replaced)
                }
            )
        }
    }

    private fun notifyStreamsUpdated(streams: List<LiveStreamUiModel>) {
        mutableUiState.value = if (streams.isEmpty()) {
            LiveStreamListUiState.Empty
        } else {
            LiveStreamListUiState.Loaded(streams)
        }
    }

    private fun LiveStream.toUiModel(): LiveStreamUiModel = LiveStreamUiModel(
        id = id,
        title = title.ifEmpty { "(untitled)" },
        description = description,
        status = status.name,
        isPublic = isPublic,
        dvrEnabled = dvrEnabled,
        recordVod = recordVod,
        scheduledStartTime = scheduledStartTime,
        streamKey = streamKey,
        playbackUrlHls = playbackUrlHls,
    )
}
