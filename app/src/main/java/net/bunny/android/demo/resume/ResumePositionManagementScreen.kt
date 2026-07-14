
package net.bunny.android.demo.resume

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunny.android.demo.R
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme
import net.bunny.api.playback.PlaybackPosition
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun ResumePositionManagementRoute(
    appState: AppState,
    onPlayVideo: (String, Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResumePositionViewModel = viewModel(),
) {
    val positions by viewModel.positions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val exportData by viewModel.exportData.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadPositions()
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { success ->
            Toast.makeText(
                context,
                if (success) context.getString(R.string.toast_positions_imported)
                else context.getString(R.string.toast_import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ResumePositionManagementScreen(
        modifier = modifier,
        onBackClicked = { appState.navController.popBackStack() },
        positions = positions,
        isLoading = isLoading,
        exportData = exportData,
        onPlayVideo = onPlayVideo,
        onDeletePosition = viewModel::deletePosition,
        onDeleteAllPositions = viewModel::deleteAllPositions,
        onExportPositions = viewModel::exportPositions,
        onImportPositions = viewModel::importPositions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumePositionManagementScreen(
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit,
    positions: List<PlaybackPosition>,
    isLoading: Boolean,
    exportData: String,
    onPlayVideo: (String, Long?) -> Unit,
    onDeletePosition: (String) -> Unit,
    onDeleteAllPositions: () -> Unit,
    onExportPositions: () -> Unit,
    onImportPositions: (String) -> Unit,
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = {
                        Text(stringResource(R.string.screen_resume_positions))
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = stringResource(R.string.cd_delete_all)
                            )
                        }
                    }
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Action buttons — Import stays available even when the list is empty,
                // so a backed-up set of positions can be restored on a fresh install.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        enabled = positions.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.button_export))
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.button_import))
                    }
                }

                if (positions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.label_no_saved_positions),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.label_watch_videos_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Positions list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(positions, key = { it.videoId }) { position ->
                            ResumePositionItem(
                                position = position,
                                onPlayVideo = { onPlayVideo(position.videoId, position.libraryId) },
                                onDeletePosition = { onDeletePosition(position.videoId) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete All Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_all_title)) },
            text = { Text(stringResource(R.string.dialog_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllPositions()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.button_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.dialog_export_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_export_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportData,
                        onValueChange = { },
                        readOnly = true,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exportData.isNotEmpty(),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(exportData))
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Text(stringResource(R.string.button_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )

        LaunchedEffect(Unit) {
            onExportPositions()
        }
    }

    // Import Dialog
    if (showImportDialog) {
        var importData by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.dialog_import_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_import_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importData,
                        onValueChange = { importData = it },
                        placeholder = { Text(stringResource(R.string.placeholder_exported_json)) },
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importData.isNotBlank(),
                    onClick = {
                        onImportPositions(importData)
                        showImportDialog = false
                    }
                ) {
                    Text(stringResource(R.string.button_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumePositionItem(
    position: PlaybackPosition,
    onPlayVideo: () -> Unit,
    onDeletePosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = position.videoTitle.takeIf { it.isNotEmpty() } ?: position.videoId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = stringResource(
                            R.string.label_resume_at,
                            formatTime(position.position),
                            (position.watchPercentage * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = formatDate(position.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column {
                    IconButton(onClick = onPlayVideo) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play)
                        )
                    }
                    IconButton(onClick = onDeletePosition) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete)
                        )
                    }
                }
            }
            
            // Progress bar
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = position.watchPercentage,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview
@Composable
private fun ResumePositionManagementScreenPreview() {
    val samplePositions = listOf(
        PlaybackPosition(
            videoId = "1",
            position = 180000, // 3 minutes
            duration = 600000, // 10 minutes  
            timestamp = System.currentTimeMillis(),
            watchPercentage = 0.3f,
            videoTitle = "Sample Video Title",
            libraryId = 12345
        )
    )
    
    BunnyStreamTheme {
        ResumePositionManagementScreen(
            onBackClicked = {},
            positions = samplePositions,
            isLoading = false,
            exportData = "",
            onPlayVideo = { _, _ -> },
            onDeletePosition = {},
            onDeleteAllPositions = {},
            onExportPositions = {},
            onImportPositions = {}
        )
    }
}
