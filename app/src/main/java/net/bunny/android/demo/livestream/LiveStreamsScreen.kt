package net.bunny.android.demo.livestream

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import net.bunny.api.model.LiveStreamStatus
import net.bunny.android.demo.library.model.Error
import net.bunny.android.demo.recording.GoLiveActivity
import net.bunny.android.demo.livestream.model.LiveStreamListUiState
import net.bunny.android.demo.livestream.model.LiveStreamUiModel
import net.bunny.android.demo.ui.AppState

@Composable
fun LiveStreamsRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
    viewModel: LiveStreamsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    var streamToDelete by remember { mutableStateOf<LiveStreamUiModel?>(null) }

    errorState?.also {
        ErrorDialog(it, viewModel::onErrorDismissed)
    }

    streamToDelete?.also { stream ->
        DeleteStreamDialog(
            stream = stream,
            onConfirm = {
                viewModel.deleteStream(stream)
                streamToDelete = null
            },
            onDismiss = { streamToDelete = null },
        )
    }

    LiveStreamsScreen(
        modifier = modifier,
        uiState = uiState,
        onBack = { appState.navController.popBackStack() },
        onRefresh = viewModel::load,
        onCreateClicked = { appState.navController.navigateToLiveStreamEditor() },
        onEditClicked = { appState.navController.navigateToLiveStreamEditor(it.id) },
        onDeleteClicked = { streamToDelete = it },
        onWatchClicked = { stream ->
            Log.d(
                "BunnyLive/ListUI",
                "onWatchClicked — id=${stream.id} title='${stream.title}' " +
                    "status=${stream.status} hasHlsUrl=${!stream.playbackUrlHls.isNullOrBlank()}",
            )
            appState.navController.navigateToLiveStreamPlayer(
                streamId = stream.id,
                fallbackHlsUrl = stream.playbackUrlHls,
                title = stream.title,
            )
        },
        onGoLiveClicked = { stream ->
            Log.d("BunnyLive/ListUI", "onGoLiveClicked — id=${stream.id} title='${stream.title}'")
            context.startActivity(GoLiveActivity.createIntent(context, stream.id))
        },
    )

    LaunchedEffect("loadLiveStreams") { viewModel.load() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveStreamsScreen(
    modifier: Modifier,
    uiState: LiveStreamListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateClicked: () -> Unit,
    onEditClicked: (LiveStreamUiModel) -> Unit,
    onDeleteClicked: (LiveStreamUiModel) -> Unit,
    onWatchClicked: (LiveStreamUiModel) -> Unit,
    onGoLiveClicked: (LiveStreamUiModel) -> Unit,
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text("Live streams") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClicked) {
                Icon(Icons.Filled.Add, contentDescription = "Create live stream")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(
                    isRefreshing = uiState == LiveStreamListUiState.Loading,
                ),
                onRefresh = onRefresh,
            ) {
                when (uiState) {
                    LiveStreamListUiState.Empty -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No live streams")
                        }
                    }

                    LiveStreamListUiState.Loading -> {
                        // SwipeRefresh shows its own indicator
                        Spacer(modifier = Modifier.fillMaxSize())
                    }

                    is LiveStreamListUiState.Loaded -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.streams, key = { it.id }) { stream ->
                                LiveStreamItem(
                                    stream = stream,
                                    onClick = { onWatchClicked(stream) },
                                    onEdit = { onEditClicked(stream) },
                                    onDelete = { onDeleteClicked(stream) },
                                    onWatch = { onWatchClicked(stream) },
                                    onGoLive = { onGoLiveClicked(stream) },
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
private fun LiveStreamItem(
    stream: LiveStreamUiModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onWatch: () -> Unit,
    onGoLive: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val canWatch = !stream.playbackUrlHls.isNullOrBlank()
    // A terminal stream (ended / processing to VOD) can't be re-published — the SDK rejects it —
    // so disable "Go live" instead of letting the user walk into that dead end.
    val canGoLive = stream.status != LiveStreamStatus.ENDED.name &&
        stream.status != LiveStreamStatus.VOD_PROCESSING.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stream.status)
                    if (stream.isPublic) Pill("Public")
                    if (stream.dvrEnabled) Pill("DVR")
                    if (stream.recordVod) Pill("VOD")
                }
                stream.scheduledStartTime?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scheduled: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = onWatch,
                enabled = canWatch,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Watch",
                    tint = if (canWatch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Watch") },
                        enabled = canWatch,
                        onClick = {
                            menuExpanded = false
                            onWatch()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Go live") },
                        enabled = canGoLive,
                        onClick = {
                            menuExpanded = false
                            onGoLive()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DeleteStreamDialog(
    stream: LiveStreamUiModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text("Delete live stream?") },
        text = { Text(stream.title) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ErrorDialog(error: Error, onDismiss: () -> Unit) {
    AlertDialog(
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text("Error") },
        text = { Text(error.message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
