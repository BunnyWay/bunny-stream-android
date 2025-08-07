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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunny.android.demo.App
import net.bunny.android.demo.R
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoStatus
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
    )

    LaunchedEffect(key1 = "load", block = { viewModel.loadVideo(videoId, libraryId) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    videoId: String,
    libraryId: Long?,
    uiState: VideoUiState,
    onBackClicked: () -> Unit,
) {
    var playerController by remember { mutableStateOf<PlayerController?>(null) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumePosition by remember { mutableStateOf<PlaybackPosition?>(null) }
    var resumeCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

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
                resumePosition = when (uiState) {
                    is VideoUiState.VideoUiLoaded -> uiState.resumePosition
                    else -> 0L
                },
                onPlayerReady = { player ->
                    playerController = PlayerController(player)
                },
                onResumePosition = { position, callback ->
                    resumePosition = position
                    resumeCallback = callback
                    showResumeDialog = true
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
                    val props = listOf(
                        VideoProperty("Title", uiState.video.name),
                        VideoProperty("Duration", uiState.video.duration),
                        VideoProperty("Views", uiState.video.viewCount),
                        VideoProperty("Size", String.format(Locale.US, "%.2f MB", uiState.video.size))
                    )
                    VideoPropertiesCard(properties = props)
                }
                VideoUiState.VideoUiLoading -> {}
            }
        }
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
                text = "Resume Playback",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Continue watching from ${formatTime(position.position)}?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Progress: ${(position.watchPercentage * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("Resume", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) {
                Text("Start Over")
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
                text = "Playback Speed",
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
                    text = "Current Speed:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${currentSpeed}x",
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
                        text = "${speed}x",
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
                        text = "${speed}x",
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
    resumePosition: Long = 0L,
    onPlayerReady: (BunnyStreamPlayer) -> Unit = {},
    onResumePosition: ((PlaybackPosition, (Boolean) -> Unit) -> Unit)? = null,
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
    } else {
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

                // Enable resume position only if enabled in settings
                if (resumeEnabled) {
                    player.enableResumePosition(
                        config = resumeConfig,
                        onResumePositionCallback = onResumePosition
                    )
                } else {
                    player.disableResumePosition()
                }

                onPlayerReady(player)
                player
            },
            update = {
                it.playVideo(videoId, libraryId)
                onPlayerReady(it)
            },
            modifier = modifier.background(Color.Gray)
        )
    }
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
                    Divider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
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