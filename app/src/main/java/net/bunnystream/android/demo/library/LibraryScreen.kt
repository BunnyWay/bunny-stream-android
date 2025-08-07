package net.bunnystream.android.demo.library

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import net.bunny.android.demo.R
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoListUiState
import net.bunny.android.demo.library.model.VideoStatus
import net.bunny.android.demo.library.model.VideoUploadUiState
import net.bunny.android.demo.settings.LocalPrefs
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme
import net.bunny.api.upload.service.PauseState
import java.util.Locale

@Composable
fun LibraryRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
    showUpload: Boolean = false,
    navigateToPlayer: (String) -> Unit,
    navigateToSettings: () -> Unit,
    localPrefs: LocalPrefs,
    viewModel: LibraryViewModel = viewModel(),
) {

    Log.d("LibraryRoute", "viewModel: $viewModel")
    val showAccessKeyNeeded = localPrefs.accessKey.isEmpty()

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            Log.d("LibraryRoute", "uri=$uri")
            if (uri != null) {
                viewModel.uploadVideo(uri)
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uploadingUiState by viewModel.uploadUiState.collectAsStateWithLifecycle()

    val errorState by viewModel.errorState.collectAsStateWithLifecycle(
        null,
        LocalLifecycleOwner.current
    )

    var deleteVideo by remember { mutableStateOf<Video?>(null) }

    errorState?.also {
        ErrorDialog(it, viewModel::onErrorDismissed)
    }

    deleteVideo?.also {
        DeleteVideoDialog(
            video = it,
            onDeleteVideo = { video ->
                viewModel.onDeleteVideo(video)
            },
            onDismissRequest = {
                deleteVideo = null
            }
        )
    }

    LibraryScreen(
        modifier = modifier,
        showUpload = showUpload,
        onBackClicked = { appState.navController.popBackStack() },
        showAccessKeyNeeded = showAccessKeyNeeded,
        navigateToSettings = navigateToSettings,
        onLoadLibrary = viewModel::loadLibrary,
        uiState = uiState,
        onUploadVideoClicked = {
            pickVideoLauncher.launch(
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly
                )
            )
        },
        uploadingUiState = uploadingUiState,
        onDismissUploadErrorClicked = viewModel::clearUploadError,
        onCancelUploadClicked = viewModel::cancelUpload,
        onPauseResumeUploadClicked = viewModel::pauseResumeUpload,
        onTusUploadOptionChanged = {
            viewModel.onTusUploadOptionChanged(it)
        },
        useTusUpload = viewModel.useTusUpload,
        onDeleteVideoClicked = {
            deleteVideo = it
        },
        onVideoClicked = {
            navigateToPlayer(it.id)
        }
    )

    LaunchedEffect(key1 = "loadLibrary", block = { viewModel.loadLibrary() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    modifier: Modifier = Modifier,
    showUpload: Boolean,
    onBackClicked: () -> Unit,
    showAccessKeyNeeded: Boolean,
    navigateToSettings: () -> Unit,
    onLoadLibrary: () -> Unit,
    uiState: VideoListUiState,
    onUploadVideoClicked: () -> Unit,
    uploadingUiState: VideoUploadUiState,
    onDismissUploadErrorClicked: () -> Unit,
    onCancelUploadClicked: () -> Unit,
    onPauseResumeUploadClicked: () -> Unit,
    onTusUploadOptionChanged: (Boolean) -> Unit,
    useTusUpload: Boolean,
    onDeleteVideoClicked: (Video) -> Unit,
    onVideoClicked: (Video) -> Unit
) {

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = {
                        Text("Video upload")
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClicked
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = null
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
        ) {
            if (showAccessKeyNeeded) {
                AccessKey(
                    navigateToSettings = navigateToSettings,
                    modifier = modifier.align(Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    SwipeRefresh(
                        modifier = modifier,
                        state = rememberSwipeRefreshState(
                            isRefreshing = uiState == VideoListUiState.VideoListUiLoading
                        ),
                        onRefresh = { onLoadLibrary() }
                    ) {
                        Column(modifier = modifier) {
                            Box(
                                modifier = modifier
                                    .fillMaxWidth()
                                    .weight(1F)
                            ) {
                                when (uiState) {
                                    VideoListUiState.VideoListUiEmpty -> {
                                        Text(
                                            modifier = modifier.align(Center),
                                            text = stringResource(id = R.string.label_no_videos)
                                        )
                                    }

                                    is VideoListUiState.VideoListUiLoaded -> {
                                        LazyColumn(
                                            modifier = modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.background)
                                                .padding(vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(uiState.videos, key = { it.id }) { video ->
                                                VideoItem(
                                                    video = video,
                                                    onVideoClicked = { onVideoClicked(video) },
                                                    onDeleteVideoClicked = {
                                                        onDeleteVideoClicked(video)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    VideoListUiState.VideoListUiLoading -> {
                                        // no-op
                                    }
                                }
                            }
                            if (showUpload) {
                                VideoUploadControls(
                                    uploadingUiState = uploadingUiState,
                                    onUploadVideoClicked = onUploadVideoClicked,
                                    onDismissUploadErrorClicked = onDismissUploadErrorClicked,
                                    onCancelUploadClicked = onCancelUploadClicked,
                                    onPauseResumeUploadClicked = onPauseResumeUploadClicked,
                                    onTusUploadOptionChanged = onTusUploadOptionChanged,
                                    useTusUpload = useTusUpload,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDialog(
    errorState: Error,
    onErrorDismissed: () -> Unit
) {
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.dialog_title_error))
        },
        text = {
            Text(text = errorState?.message ?: "")
        },
        onDismissRequest = { onErrorDismissed() },
        confirmButton = {
            TextButton(
                onClick = { onErrorDismissed() },
            ) {
                Text(text = stringResource(id = R.string.dialog_button_ok))
            }
        },
    )
}

@Composable
private fun DeleteVideoDialog(
    video: Video,
    onDeleteVideo: (Video) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.dialog_delete_video))
        },
        text = {
            Text(text = video.name)
        },
        onDismissRequest = { onDismissRequest() },
        dismissButton = {
            TextButton(
                onClick = { onDismissRequest() }
            ) {
                Text(text = stringResource(id = R.string.dialog_button_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteVideo(video)
                    onDismissRequest()
                }
            ) {
                Text(text = stringResource(id = R.string.dialog_button_delete))
            }
        },
    )
}

@Composable
private fun VideoItem(
    video: Video,
    onVideoClicked: () -> Unit,
    onDeleteVideoClicked: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)        // Full‑HD ratio
            .clickable(onClick = onVideoClicked),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            // Make the card itself transparent if we have a thumbnail,
            // so the image can show through; otherwise use surface.
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            video.thumbnailUrl?.let { url ->
                // Debug log
                Log.d("VideoItem", "Loading thumbnail for video ${video.id}: $url")

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            // Fade from semi‑opaque surface at the very top…
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            // startY = 0 (top), endY = height
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            // 3) Your existing content, inset for padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Top‑left pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Pill("${video.viewCount} views")
                    Pill(video.status.name)
                }

                // Overflow menu (top‑right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .wrapContentSize()           // size to content
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onDeleteVideoClicked()
                            }
                        )
                    }
                }

                // Bottom‑aligned title + metadata
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = CenterVertically
                    ) {
                        Text(
                            text = video.duration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f MB", video.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AccessKey(
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = R.string.label_set_access_key))
        Button(onClick = navigateToSettings) {
            Text(text = stringResource(id = R.string.button_settings))
        }
    }
}

@Composable
private fun VideoUploadControls(
    uploadingUiState: VideoUploadUiState,
    onUploadVideoClicked: () -> Unit,
    onDismissUploadErrorClicked: () -> Unit,
    onCancelUploadClicked: () -> Unit,
    onPauseResumeUploadClicked: () -> Unit,
    onTusUploadOptionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    useTusUpload: Boolean
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when (uploadingUiState) {
            VideoUploadUiState.NotUploading -> {
                Column(modifier = modifier.fillMaxWidth()) {
                    Row(
                        modifier = modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = useTusUpload,
                                onValueChange = {
                                    onTusUploadOptionChanged(it)
                                },
                                role = Role.Checkbox
                            )
                            .padding(16.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        Checkbox(
                            checked = useTusUpload,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = stringResource(id = R.string.checkbox_enable_tus),
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        modifier = modifier.fillMaxWidth(),
                        onClick = onUploadVideoClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.button_upload_video),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            is VideoUploadUiState.UploadError -> {
                Row(modifier = modifier) {
                    Text(
                        modifier = modifier
                            .weight(1F)
                            .align(CenterVertically),
                        text = "Error: ${uploadingUiState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    IconButton(onClick = onDismissUploadErrorClicked) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null
                        )
                    }
                }
            }

            is VideoUploadUiState.Uploading -> {
                Row(modifier = modifier.fillMaxWidth()) {
                    Column(
                        modifier = modifier
                            .weight(1F)
                            .align(CenterVertically)
                    ) {
                        Text(
                            modifier = modifier,
                            text = "Progress: ${uploadingUiState.progress}%",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            modifier = modifier.fillMaxWidth(),
                            progress = uploadingUiState.progress.toFloat() / 100
                        )
                    }

                    if (uploadingUiState.pauseState != PauseState.Unsupported) {
                        val icon = if (uploadingUiState.pauseState == PauseState.Uploading) {
                            R.drawable.pause_circle_icon
                        } else {
                            R.drawable.play_circle_icon
                        }
                        IconButton(
                            modifier = modifier.align(CenterVertically),
                            onClick = onPauseResumeUploadClicked
                        ) {
                            Icon(
                                painter = painterResource(id = icon),
                                tint = MaterialTheme.colorScheme.onSurface,
                                contentDescription = "Pause upload"
                            )
                        }
                    }

                    IconButton(
                        modifier = modifier.align(CenterVertically),
                        onClick = onCancelUploadClicked
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.stop_circle_icon),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "Cancel upload",
                        )
                    }
                }
            }

            VideoUploadUiState.Preparing -> {
                Text(
                    modifier = modifier,
                    text = "Preparing upload...",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview
@Composable
private fun LibraryScreenPreview() {

    val videos = listOf(
        Video("1", "Video 1", "1m", VideoStatus.FINISHED, 10.0, "123"),
        Video("2", "Video 2", "1m", VideoStatus.FINISHED, 10.0, "123"),
        Video("3", "Video 3", "1m", VideoStatus.FINISHED, 10.0, "123")
    )

    BunnyStreamTheme {
        LibraryScreen(
            navigateToSettings = {},
            showUpload = true,
            onBackClicked = {},
            showAccessKeyNeeded = false,
            onLoadLibrary = {},
            uiState = VideoListUiState.VideoListUiEmpty,
            onUploadVideoClicked = {},
            uploadingUiState = VideoUploadUiState.Uploading(50, PauseState.Paused),
            onDismissUploadErrorClicked = {},
            onCancelUploadClicked = {},
            onTusUploadOptionChanged = {},
            useTusUpload = true,
            onDeleteVideoClicked = {},
            onVideoClicked = {},
            onPauseResumeUploadClicked = {}
        )
    }
}
