
package net.bunnystream.android.demo.resume

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunnystream.android.demo.ui.AppState
import net.bunnystream.android.demo.ui.theme.BunnyStreamTheme
import net.bunnystream.api.playback.PlaybackPosition
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

    LaunchedEffect(Unit) {
        viewModel.loadPositions()
    }

    ResumePositionManagementScreen(
        modifier = modifier,
        onBackClicked = { appState.navController.popBackStack() },
        positions = positions,
        isLoading = isLoading,
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
    onPlayVideo: (String, Long?) -> Unit,
    onDeletePosition: (String) -> Unit,
    onDeleteAllPositions: () -> Unit,
    onExportPositions: () -> Unit,
    onImportPositions: (String) -> Unit,
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportData by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = {
                        Text("Resume Positions")
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
                                contentDescription = "Delete All"
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
            } else if (positions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No saved positions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Watch some videos to see resume positions here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { /* TODO: Import dialog */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import")
                    }
                }

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

    // Delete All Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Positions") },
            text = { Text("Are you sure you want to delete all saved resume positions? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllPositions()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Positions") },
            text = {
                Column {
                    Text("Copy this data to backup your resume positions:")
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
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )

        LaunchedEffect(showExportDialog) {
            if (showExportDialog) {
                onExportPositions()
                // In a real implementation, get the export data from the ViewModel
                exportData = "Exported data would appear here"
            }
        }
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
                        text = "Resume at ${formatTime(position.position)} • ${(position.watchPercentage * 100).toInt()}% watched",
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
                            contentDescription = "Play"
                        )
                    }
                    IconButton(onClick = onDeletePosition) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete"
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
            onPlayVideo = { _, _ -> },
            onDeletePosition = {},
            onDeleteAllPositions = {},
            onExportPositions = {},
            onImportPositions = {}
        )
    }
}
