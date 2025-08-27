@file:OptIn(ExperimentalMaterial3Api::class)

package net.bunny.android.demo.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.bunny.android.demo.App
import net.bunny.android.demo.settings.LocalPrefs
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme

data class TVMenuItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

// Navigation extension for TV home screen
fun NavGraphBuilder.tvHomeScreen(
    appState: AppState,
    navigateToSettings: () -> Unit,
    navigateToVideoList: () -> Unit,
    navigateToUpload: () -> Unit,
    navigateToStreaming: () -> Unit,
    navigateToTVPlayer: (String, Long) -> Unit,
    navigateToResumeSettings: () -> Unit,
    navigateToResumeManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    composable(
        route = HOME_ROUTE,
    ) {
        TVHomeScreenRoute(
            appState = appState,
            localPrefs = App.di.localPrefs,
            navigateToSettings = navigateToSettings,
            navigateToVideoList = navigateToVideoList,
            navigateToUpload = navigateToUpload,
            navigateToStreaming = navigateToStreaming,
            navigateToTVPlayer = navigateToTVPlayer,
            navigateToResumeSettings = navigateToResumeSettings,
            navigateToResumeManagement = navigateToResumeManagement,
            modifier = modifier
        )
    }
}

@Composable
fun TVHomeScreenRoute(
    appState: AppState,
    localPrefs: LocalPrefs,
    navigateToSettings: () -> Unit,
    navigateToVideoList: () -> Unit,
    navigateToUpload: () -> Unit,
    navigateToStreaming: () -> Unit,
    navigateToTVPlayer: (String, Long) -> Unit,
    navigateToResumeSettings: () -> Unit,
    navigateToResumeManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDirectPlayDialog by remember { mutableStateOf(false) }

    val menuItems = remember {
        listOf(
            TVMenuItem(
                title = "Video Library",
                description = "Browse and play your videos",
                icon = Icons.Default.Menu // Corresponds to user's "VideoLibrary"
            ) { navigateToVideoList() },
            TVMenuItem(
                title = "Upload Video",
                description = "Upload new videos to your library",
                icon = Icons.Default.KeyboardArrowUp // Corresponds to user's "CloudUpload"
            ) { navigateToUpload() },
            TVMenuItem(
                title = "Live Recording",
                description = "Record and stream live content",
                icon = Icons.Default.Info // Corresponds to user's "Videocam"
            ) { navigateToStreaming() },
            TVMenuItem(
                title = "Direct Play",
                description = "Play a video by entering its ID",
                icon = Icons.Default.PlayArrow
            ) { showDirectPlayDialog = true },
            TVMenuItem(
                title = "Resume Settings",
                description = "Configure video resume options",
                icon = Icons.Default.Settings
            ) { navigateToResumeSettings() },
            TVMenuItem(
                title = "Manage Positions",
                description = "View and manage saved positions",
                icon = Icons.Default.Edit // Corresponds to user's "Bookmarks"
            ) { navigateToResumeManagement() },
            TVMenuItem(
                title = "Configuration",
                description = "Configure Bunny Stream settings",
                icon = Icons.Default.Build
            ) { navigateToSettings() }
        )
    }

    TVHomeScreenContent(
        modifier = modifier,
        showDirectPlayDialog = showDirectPlayDialog,
        menuItems = menuItems,
        onDirectPlayDismiss = { showDirectPlayDialog = false },
        onDirectPlay = { videoId, libraryId ->
            showDirectPlayDialog = false
            navigateToTVPlayer(videoId, libraryId.toLong())
        }
    )
}

@Composable
private fun TVHomeScreenContent(
    modifier: Modifier,
    showDirectPlayDialog: Boolean,
    menuItems: List<TVMenuItem>,
    onDirectPlayDismiss: () -> Unit,
    onDirectPlay: (String, String) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp) // Larger padding for TV
    ) {
        Column {
            // App Title
            Text(
                text = "Bunny Stream",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Subtitle
            Text(
                text = "Select an option using the remote control",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // TV Menu Grid
            TVMenuGrid(menuItems = menuItems)
        }
    }

    if (showDirectPlayDialog) {
        TVDirectPlayDialog(
            onPlay = onDirectPlay,
            onDismiss = onDirectPlayDismiss
        )
    }
}

@Composable
private fun TVMenuGrid(menuItems: List<TVMenuItem>) {
    // Split items into rows of 3 for TV layout
    val rows = menuItems.chunked(3)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(rows) { rowItems ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(rowItems) { item ->
                    TVMenuItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun TVMenuItemCard(item: TVMenuItem) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .focusable()
            .clickable { item.onClick() }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .scale(if (isFocused) 1.05f else 1.0f),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isFocused)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isFocused)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TVDirectPlayDialog(
    onPlay: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var videoId by remember { mutableStateOf("") }
    var libraryId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Direct Play",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter video details to play directly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = videoId,
                    onValueChange = { videoId = it },
                    label = { Text("Video ID") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = libraryId,
                    onValueChange = { libraryId = it },
                    label = { Text("Library ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPlay(videoId, libraryId) },
                enabled = videoId.isNotEmpty() && libraryId.isNotEmpty()
            ) {
                Text("Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TVHomeScreenPreview() {
    BunnyStreamTheme {
        TVHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            showDirectPlayDialog = false,
            menuItems = listOf(
                TVMenuItem("Video Library", "Browse videos", Icons.Default.PlayArrow) {},
                TVMenuItem("Upload", "Upload new videos", Icons.Default.KeyboardArrowUp) {},
                TVMenuItem("Settings", "Configure app", Icons.Default.Settings) {}
            ),
            onDirectPlayDismiss = {},
            onDirectPlay = { _, _ -> }
        )
    }
}