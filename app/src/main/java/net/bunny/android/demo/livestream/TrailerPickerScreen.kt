package net.bunny.android.demo.livestream

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bunny.api.error.fold
import net.bunny.api.error.getOrNull
import net.bunny.android.demo.App
import net.bunny.android.demo.R
import net.bunny.android.demo.ui.AppState
import net.bunny.api.BunnyStreamApi

/**
 * Lists the library's videos so the user can pick one as a stream's pre-stream trailer. Selection
 * is returned to the caller (the editor) via [onPicked]; this screen never mutates the stream
 * itself.
 */
@Composable
fun TrailerPickerRoute(
    appState: AppState,
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrailerPickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrailerPickerScreen(
        modifier = modifier,
        state = state,
        onBack = { appState.navController.popBackStack() },
        onSelect = onPicked,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrailerPickerScreen(
    modifier: Modifier,
    state: TrailerPickerViewModel.State,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text(stringResource(R.string.live_screen_choose_trailer)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (state) {
                is TrailerPickerViewModel.State.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is TrailerPickerViewModel.State.Failed -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.live_error_load_videos, state.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text(stringResource(R.string.button_retry)) }
                    }
                }

                is TrailerPickerViewModel.State.Loaded -> {
                    if (state.videos.isEmpty()) {
                        Text(
                            text = stringResource(R.string.live_label_no_library_videos),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.videos) { video ->
                                TrailerVideoRow(video = video, onClick = { onSelect(video.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerVideoRow(
    video: TrailerPickerViewModel.TrailerVideo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Backs [TrailerPickerRoute]: loads the library's videos (with thumbnails) for trailer selection.
 * Read-only — it lists videos via the SDK's `videosApi` and never modifies them.
 */
class TrailerPickerViewModel : ViewModel() {

    data class TrailerVideo(val id: String, val title: String, val thumbnailUrl: String?)

    sealed interface State {
        data object Loading : State
        data class Loaded(val videos: List<TrailerVideo>) : State
        data class Failed(val message: String) : State
    }

    private val libraryId: Long
        get() = BunnyStreamApi.libraryId

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        Log.d(TAG, "load library videos")
        mutableState.value = State.Loading
        viewModelScope.launch {
            try {
                val page = App.di.streamSdk.videoRepository.listVideos(libraryId).getOrNull()
                if (page == null) {
                    mutableState.value = State.Failed("Could not load the video library")
                    return@launch
                }
                val videos = page.items.map { video ->
                    TrailerVideo(video.id, video.title.ifBlank { "Untitled" }, null)
                }
                mutableState.value = State.Loaded(videos)
                enrichThumbnails(videos)
            } catch (e: Exception) {
                Log.w(TAG, "videoList failed", e)
                mutableState.value = State.Failed(e.message ?: e.toString())
            }
        }
    }

    /** Fills in each video's thumbnail URL via the play-settings endpoint, like the library list. */
    private fun enrichThumbnails(videos: List<TrailerVideo>) {
        if (videos.isEmpty()) return
        viewModelScope.launch {
            val enriched = videos.map { video ->
                BunnyStreamApi.getInstance()
                    .fetchPlayerSettings(libraryId, video.id)
                    .fold(
                        onOk = { video.copy(thumbnailUrl = it.thumbnailUrl) },
                        onErr = { video },
                    )
            }
            if (mutableState.value is State.Loaded) {
                mutableState.value = State.Loaded(enriched)
            }
        }
    }

    companion object {
        private const val TAG = "BunnyLive/TrailerPicker"
    }
}
