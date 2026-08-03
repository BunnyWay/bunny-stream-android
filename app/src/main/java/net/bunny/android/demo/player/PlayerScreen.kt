package net.bunny.android.demo.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import net.bunny.android.demo.App
import net.bunny.android.demo.R
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoStatus
import net.bunny.api.BunnyStreamApi
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.ResumeConfig
import net.bunny.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun PlayerRoute(
    appState: AppState,
    videoId: String,
    libraryId: Long?,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlayerScreen(
        modifier = modifier,
        videoId = videoId,
        libraryId = libraryId,
        uiState,
        onBackClicked = { appState.navController.popBackStack() },
        onReloadVideo = { viewModel.loadVideo(videoId, libraryId) },
    )

    LaunchedEffect(key1 = "load", block = { viewModel.loadVideo(videoId, libraryId) })

    // While the video is still processing, re-check its status so playback starts by
    // itself once encoding finishes. Gated on the screen being visible; the ViewModel
    // skips the fetch entirely for settled videos.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(PlayerViewModel.STATUS_POLL_INTERVAL_MS)
                viewModel.onStatusPollTick()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    videoId: String,
    libraryId: Long?,
    uiState: VideoUiState,
    onBackClicked: () -> Unit,
    onReloadVideo: () -> Unit = {},
) {
    var playerController by remember { mutableStateOf<PlayerController?>(null) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumePosition by remember { mutableStateOf<PlaybackPosition?>(null) }
    var resumeCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackAttempt by remember { mutableStateOf(0) }

    // Get resume position preferences
    val resumePrefs = App.di.resumePositionPrefs

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text(stringResource(id = R.string.screen_player)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BunnyPlayerComposable(
                videoId = videoId,
                libraryId = libraryId,
                uiState = uiState,
                playbackAttempt = playbackAttempt,
                onPlayerReady = { player ->
                    playerController = PlayerController(player)
                },
                onResumePosition = { position, callback ->
                    resumePosition = position
                    resumeCallback = callback
                    showResumeDialog = true
                },
                onPlaybackError = { message ->
                    playbackError = message
                },
                onRetry = {
                    playbackAttempt++
                    onReloadVideo()
                },
                resumeConfig = resumePrefs.getResumeConfig(),
                resumeEnabled = resumePrefs.isResumeEnabled(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Speed Control Section
            SpeedControlSection(
                currentSpeed = currentSpeed,
                onSpeedChanged = { speed ->
                    currentSpeed = speed
                    playerController?.setSpeed(speed)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState) {
                VideoUiState.VideoUiEmpty -> {}
                is VideoUiState.VideoUiLoaded -> {
                    val props = buildList {
                        add(VideoProperty(stringResource(R.string.label_title), uiState.video.name))
                        add(
                            VideoProperty(
                                stringResource(R.string.label_duration),
                                uiState.video.duration
                            )
                        )
                        add(
                            VideoProperty(
                                stringResource(R.string.label_views),
                                uiState.video.viewCount
                            )
                        )
                        add(
                            VideoProperty(
                                stringResource(R.string.label_size),
                                stringResource(
                                    R.string.value_size_mb,
                                    String.format(Locale.US, "%.2f", uiState.video.size)
                                )
                            )
                        )
                        if (uiState.video.status != VideoStatus.FINISHED) {
                            add(
                                VideoProperty(
                                    stringResource(R.string.label_status),
                                    uiState.video.status.name
                                )
                            )
                        }
                    }
                    VideoPropertiesCard(properties = props)
                }

                is VideoUiState.VideoUiLoadFailed -> {
                    VideoPropertiesCard(
                        properties = listOf(
                            VideoProperty(
                                stringResource(R.string.label_metadata),
                                stringResource(R.string.value_metadata_unavailable)
                            )
                        )
                    )
                }

                VideoUiState.VideoUiLoading -> {}
            }
        }
    }

    // Playback error dialog — translates raw ExoPlayer error codes into an actionable
    // explanation (processing state, cross-library access, token auth).
    playbackError?.let { rawError ->
        AlertDialog(
            onDismissRequest = { playbackError = null },
            title = { Text(stringResource(R.string.dialog_playback_failed_title)) },
            text = {
                Text(
                    text = describePlaybackError(
                        rawError = rawError,
                        uiState = uiState,
                        requestedLibraryId = libraryId,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playbackError = null
                        playbackAttempt++
                        onReloadVideo()
                    }
                ) {
                    Text(stringResource(R.string.button_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { playbackError = null }) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )
    }

    // Resume Dialog
    if (showResumeDialog && resumePosition != null && resumePrefs.isResumeEnabled()) {
        ResumeDialog(
            position = resumePosition!!,
            onResume = {
                resumeCallback?.invoke(true)
                showResumeDialog = false
            },
            onStartOver = {
                resumeCallback?.invoke(false)
                showResumeDialog = false
            },
            onDismiss = {
                resumeCallback?.invoke(false)
                showResumeDialog = false
            }
        )
    }
}

@Composable
fun ResumeDialog(
    position: PlaybackPosition,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.resume_playback),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.continue_watching_from,
                        formatTime(position.position)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.progress_percentage,
                        (position.watchPercentage * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.resume), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) {
                Text(stringResource(R.string.start_over))
            }
        }
    )
}

@Composable
fun SpeedControlSection(
    currentSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.label_playback_speed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Current speed display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_current_speed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.value_speed, currentSpeed.toString()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Speed buttons grid
            SpeedButtonGrid(
                speedOptions = speedOptions,
                currentSpeed = currentSpeed,
                onSpeedSelected = onSpeedChanged
            )
        }
    }
}

@Composable
private fun SpeedButtonGrid(
    speedOptions: List<Float>,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    // Split speeds into two rows
    val firstRow = speedOptions.take(4)
    val secondRow = speedOptions.drop(4)

    Column {
        SpeedButtonRow(
            speeds = firstRow,
            currentSpeed = currentSpeed,
            onSpeedSelected = onSpeedSelected
        )

        Spacer(modifier = Modifier.height(8.dp))

        SpeedButtonRow(
            speeds = secondRow,
            currentSpeed = currentSpeed,
            onSpeedSelected = onSpeedSelected
        )
    }
}

@Composable
private fun SpeedButtonRow(
    speeds: List<Float>,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        speeds.forEach { speed ->
            val isSelected = speed == currentSpeed

            if (isSelected) {
                Button(
                    onClick = { onSpeedSelected(speed) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.value_speed, speed.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onSpeedSelected(speed) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.value_speed, speed.toString()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun BunnyPlayerComposable(
    videoId: String,
    libraryId: Long?,
    // Null means the caller supplies no metadata (e.g. the live editor's trailer
    // preview) — playback starts immediately, exactly like before the gating existed.
    uiState: VideoUiState? = null,
    playbackAttempt: Int = 0,
    onPlayerReady: (BunnyStreamPlayer) -> Unit = {},
    onResumePosition: ((PlaybackPosition, (Boolean) -> Unit) -> Unit)? = null,
    onPlaybackError: ((String) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    resumeConfig: ResumeConfig = ResumeConfig(),
    resumeEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        Box(
            modifier
                .background(Color.DarkGray)
                .then(modifier)
        ) {
            Text(
                "Player preview",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        }
        return
    }

    // Gate playback on the video's processing state: a video that hasn't finished
    // encoding has no playable HLS manifest yet, and starting the player would only
    // surface a raw ERROR_CODE_IO_BAD_HTTP_STATUS overlay.
    val loadedStatus = ((uiState as? VideoUiState.VideoUiLoaded)?.video?.status)
    val playbackAllowed = when (uiState) {
        null -> true
        is VideoUiState.VideoUiLoaded -> loadedStatus == VideoStatus.FINISHED
        // Metadata fetch failed — the player's own play-data call (same endpoint, same
        // credentials) would fail identically, so explain instead of dying silently.
        is VideoUiState.VideoUiLoadFailed -> false
        VideoUiState.VideoUiEmpty, VideoUiState.VideoUiLoading -> false
    }

    when {
        playbackAllowed -> {
            var playerView by remember { mutableStateOf<BunnyStreamPlayer?>(null) }
            // Guards against double kickoff: the first LaunchedEffect run already sees
            // the player written by the factory, and the playerView key change then
            // restarts the effect once more for the same (player, request) pair.
            var lastStarted by remember {
                mutableStateOf<Pair<BunnyStreamPlayer, Triple<String, Long?, Int>>?>(null)
            }

            AndroidView(
                factory = { context ->
                    val player = BunnyStreamPlayer(context)

                    val speedConfig = PlaybackSpeedConfig(
                        enableSpeedControl = true,
                        defaultSpeed = 1.0f,
                        allowedSpeeds = null,
                        showSpeedBadge = true,
                        rememberLastSpeed = true
                    )

                    player.setPlaybackSpeedConfig(speedConfig)

                    // Opt the demo app in to auto-contrast for the position/duration readout so the
                    // text stays legible regardless of scene brightness. Off by default in the SDK.
                    player.autoProgressTextColor = true
//                player.progressTextColor = android.graphics.Color.RED

                    // Enable resume position only if enabled in settings
                    if (resumeEnabled) {
                        player.enableResumePosition(
                            config = resumeConfig,
                            onResumePositionCallback = onResumePosition
                        )
                    } else {
                        player.disableResumePosition()
                    }

                    player.onPlaybackError = { message -> onPlaybackError?.invoke(message) }

                    onPlayerReady(player)
                    playerView = player
                    player
                },
                update = {
                    onPlayerReady(it)
                },
                modifier = modifier.background(Color.Gray)
            )

            LaunchedEffect(playerView, videoId, libraryId, playbackAttempt) {
                val player = playerView ?: return@LaunchedEffect
                val request = player to Triple(videoId, libraryId, playbackAttempt)
                if (lastStarted != request) {
                    lastStarted = request
                    player.playVideo(videoId, libraryId, videoTitle = "")
                }
            }
        }

        uiState is VideoUiState.VideoUiLoadFailed -> {
            val configuredLibraryId = App.di.libraryId
            val requestedLibraryId = libraryId
            PlayerStatusPlaceholder(
                title = stringResource(R.string.player_status_cant_load_title),
                message = if (requestedLibraryId != null && requestedLibraryId != configuredLibraryId) {
                    stringResource(
                        R.string.player_load_failed_cross_library,
                        requestedLibraryId,
                        configuredLibraryId
                    )
                } else {
                    stringResource(R.string.player_load_failed_generic) +
                        (uiState.message?.let {
                            "\n\n" + stringResource(R.string.playback_error_details, it)
                        } ?: "")
                },
                showProgress = false,
                onRetry = onRetry,
                modifier = modifier
            )
        }

        loadedStatus != null -> {
            // Loaded but not FINISHED — show the processing state instead of a dead
            // player. Transitional states auto-refresh via the screen's status poll,
            // so playback starts by itself once encoding finishes.
            PlayerStatusPlaceholder(
                title = when (loadedStatus) {
                    VideoStatus.ERROR, VideoStatus.UPLOAD_FAILED ->
                        stringResource(R.string.player_status_cant_play_title)
                    else -> stringResource(R.string.player_status_not_ready_title)
                },
                message = when (loadedStatus) {
                    VideoStatus.CREATED -> stringResource(R.string.player_status_created)
                    VideoStatus.UPLOADED, VideoStatus.PROCESSING, VideoStatus.TRANSCODING ->
                        stringResource(R.string.player_status_processing, loadedStatus.name)
                    VideoStatus.ERROR -> stringResource(R.string.player_status_encoding_error)
                    VideoStatus.UPLOAD_FAILED ->
                        stringResource(R.string.player_status_upload_failed)
                    VideoStatus.FINISHED -> "" // unreachable — FINISHED is playbackAllowed
                },
                showProgress = loadedStatus in VideoStatus.TRANSITIONAL &&
                    loadedStatus != VideoStatus.CREATED,
                onRetry = if (loadedStatus == VideoStatus.CREATED) onRetry else null,
                modifier = modifier
            )
        }

        else -> {
            // Metadata still loading — brief spinner before the player appears.
            Box(modifier = modifier.background(Color.Black)) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun PlayerStatusPlaceholder(
    title: String,
    message: String,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier = modifier.background(Color.Black)) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showProgress) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.button_retry), color = Color.White)
                }
            }
        }
    }
}

/**
 * Maps a raw engine error (e.g. "ERROR_CODE_IO_BAD_HTTP_STATUS: Source error") plus what we
 * know about the video to a message that tells the user what to actually do about it.
 */
@Composable
private fun describePlaybackError(
    rawError: String,
    uiState: VideoUiState,
    requestedLibraryId: Long?,
): String {
    val video = (uiState as? VideoUiState.VideoUiLoaded)?.video

    val configuredLibraryId = App.di.libraryId

    val explanation = when {
        video != null && video.status in VideoStatus.TRANSITIONAL ->
            stringResource(R.string.playback_error_not_ready, video.status.name)

        requestedLibraryId != null && requestedLibraryId != configuredLibraryId ->
            stringResource(
                R.string.playback_error_cross_library,
                requestedLibraryId,
                configuredLibraryId
            )

        else -> stringResource(R.string.playback_error_generic)
    }

    return explanation + "\n\n" + stringResource(R.string.playback_error_details, rawError)
}

private fun formatTime(positionMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(positionMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(positionMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(positionMs) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

data class VideoProperty(val label: String, val value: String)

@Composable
fun VideoPropertiesCard(properties: List<VideoProperty>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            properties.forEachIndexed { index, prop ->
                VideoPropertyItem(prop)
                if (index < properties.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPropertyItem(prop: VideoProperty) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = prop.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = prop.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun PlayerScreenPreview() {
    BunnyStreamTheme {
        PlayerScreen(
            videoId = "12345",
            libraryId = null,
            uiState = VideoUiState.VideoUiLoaded(
                Video(
                    id = "12345",
                    name = "Sample Video",
                    duration = "00:10:00",
                    status = VideoStatus.FINISHED,
                    viewCount = "1000",
                    size = 50.0
                )
            ),
            onBackClicked = {},
        )
    }
}