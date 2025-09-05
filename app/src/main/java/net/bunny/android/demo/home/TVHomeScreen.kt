@file:OptIn(ExperimentalMaterial3Api::class)

package net.bunny.android.demo.home

//import android.graphics.Color
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Enhanced scale animation with press feedback
    val targetScale = when {
        isPressed -> 0.95f // Shrink when pressed for tactile feedback
        isFocused -> 1.08f // Bigger when focused
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (isPressed) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "scale"
    )

    // Enhanced container color with more dramatic press effect
    val targetContainerColor = when {
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 1.0f) // Full opacity when pressed
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 150),
        label = "containerColor"
    )

    // Enhanced border effects
    val targetBorderWidth = when {
        isPressed -> 4.dp
        isFocused -> 3.dp
        else -> 0.dp
    }
    val borderWidth by animateDpAsState(
        targetValue = targetBorderWidth,
        animationSpec = tween(durationMillis = 150),
        label = "borderWidth"
    )

    val targetBorderColor = when {
        isPressed -> MaterialTheme.colorScheme.onPrimary
        isFocused -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 150),
        label = "borderColor"
    )

    // Enhanced elevation with press feedback
    val targetElevation = when {
        isPressed -> 2.dp // Lower when pressed
        isFocused -> 12.dp // Higher when focused
        else -> 4.dp
    }
    val elevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = tween(durationMillis = 150),
        label = "elevation"
    )

    // Enhanced icon scale animation
    val targetIconScale = when {
        isPressed -> 1.15f // Bigger when pressed
        isFocused -> 1.1f // Slightly bigger when focused
        else -> 1.0f
    }
    val iconScale by animateFloatAsState(
        targetValue = targetIconScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )

    // Enhanced icon tint with press effect
    val targetIconTint = when {
        isPressed -> MaterialTheme.colorScheme.onPrimary
        isFocused -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    val iconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = tween(durationMillis = 150),
        label = "iconTint"
    )

    // Enhanced text colors
    val targetTitleColor = when {
        isPressed -> MaterialTheme.colorScheme.onPrimary
        isFocused -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val titleColor by animateColorAsState(
        targetValue = targetTitleColor,
        animationSpec = tween(durationMillis = 150),
        label = "titleColor"
    )

    val targetDescriptionColor = when {
        isPressed -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        isFocused -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val descriptionColor by animateColorAsState(
        targetValue = targetDescriptionColor,
        animationSpec = tween(durationMillis = 150),
        label = "descriptionColor"
    )

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .scale(scale)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null // Custom indication handled by our animations
            ) { item.onClick() },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Enhanced icon with background effect when pressed
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .then(
                        if (isPressed) {
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .padding(8.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced title with font weight animation
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPressed) FontWeight.ExtraBold else FontWeight.Bold,
                color = titleColor,
                fontSize = if (isPressed) 17.sp else 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Enhanced description
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = descriptionColor,
                textAlign = TextAlign.Center,
                fontWeight = if (isPressed) FontWeight.Medium else FontWeight.Normal
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