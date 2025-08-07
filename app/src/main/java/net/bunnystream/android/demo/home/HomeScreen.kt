package net.bunnystream.android.demo.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.bunny.android.demo.R
import net.bunny.android.demo.settings.LocalPrefs
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme

/**
 * The sealed class that describes each button, with an optional override for its text color.
 */
/**
 * Now each option carries a small @Composable lambda for its text color,
 * defaulting to MaterialTheme.colorScheme.onSurface
 */
sealed class HomeOption(
    val title: String,
    val textColor: @Composable () -> Color = { MaterialTheme.colorScheme.onSurface }
) {
    object VideoPlayer : HomeOption("Video player")

    object VideoUpload : HomeOption("Video Upload")

    object CameraUpload : HomeOption("Camera upload")

    object DirectVideoPlay : HomeOption(
        "Direct video play",
        textColor = { MaterialTheme.colorScheme.primary })

    object BunnyStreamConfiguration : HomeOption(
        "Bunny Stream Configuration",
        textColor = { MaterialTheme.colorScheme.primary }
    )

    object ResumePositionSettings : HomeOption(
        "Resume Position Settings",
        textColor = { MaterialTheme.colorScheme.primary }
    )

    object ResumePositionManagement : HomeOption(
        "Manage Resume Positions",
        textColor = { MaterialTheme.colorScheme.primary }
    )
}

@ExperimentalMaterial3Api
@Composable
fun HomeScreenRoute(
    appState: AppState,
    localPrefs: LocalPrefs,
    navigateToSettings: () -> Unit,
    navigateToVideoList: () -> Unit,
    navigateToUpload: () -> Unit,
    navigateToStreaming: () -> Unit,
    navigateToPlayer: (String, Long) -> Unit,
    navigateToResumeSettings: () -> Unit,
    navigateToResumeManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    HomeScreenContent(
        modifier = modifier,
        showDialog,
        onOptionClick = { option ->
            when (option) {
                HomeOption.VideoPlayer -> {
                    navigateToVideoList()
                }

                HomeOption.VideoUpload -> {
                    navigateToUpload()
                }

                HomeOption.CameraUpload -> {
                    navigateToStreaming()
                }

                HomeOption.DirectVideoPlay -> {
                    showDialog = true
                }

                HomeOption.BunnyStreamConfiguration -> {
                    navigateToSettings()
                }

                HomeOption.ResumePositionSettings -> {
                    navigateToResumeSettings()
                }

                HomeOption.ResumePositionManagement -> {
                    navigateToResumeManagement()
                }
            }
        },
        onPlayDirect = { videoId, libraryId ->
            showDialog = false
            navigateToPlayer(videoId, libraryId.toLong())
        },
        onDismiss = {
            showDialog = false
        }
    )
}

@ExperimentalMaterial3Api
@Composable
fun HomeScreenContent(
    modifier: Modifier,
    showDialog: Boolean = false,
    onOptionClick: (HomeOption) -> Unit,
    onPlayDirect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text(stringResource(R.string.bunnystream_demo)) }
                )
            }
        },
    ) { innerPadding ->
        OptionsList(
            onOptionClick = onOptionClick,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        )

        if (showDialog) {
            EnterVideoIdDialog(
                initialValue = "",
                onPlay = { videoId, libraryId ->
                    onPlayDirect(videoId, libraryId)     // fire the navigation/callback
                },
                onDismiss = {
                    onDismiss()
                }
            )
        }
    }
}
@Composable
fun OptionsList(
    onOptionClick: (HomeOption) -> Unit,
    modifier: Modifier
) {
    val actionItems = listOf(
        HomeOption.VideoPlayer,
        HomeOption.VideoUpload,
        HomeOption.CameraUpload,
        HomeOption.DirectVideoPlay
    )

    val resumeItems = listOf(
        HomeOption.ResumePositionSettings,
        HomeOption.ResumePositionManagement
    )

    val configItems = listOf(
        HomeOption.BunnyStreamConfiguration
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            OptionsCategory(title = "Actions")
            OptionsGroupCard(items = actionItems, onItemClick = onOptionClick)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            OptionsCategory(title = "Resume Positions")
            OptionsGroupCard(items = resumeItems, onItemClick = onOptionClick)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            OptionsCategory(title = "Configuration")
            OptionsGroupCard(items = configItems, onItemClick = onOptionClick)
        }
    }
}


@Composable
fun OptionsGroupCard(
    items: List<HomeOption>,
    onItemClick: (HomeOption) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            items.forEachIndexed { index, option ->
                OptionsItem(option = option) {
                    onItemClick(option)
                }
                if (index < items.lastIndex) {
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
fun OptionsItem(
    option: HomeOption,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = option.title,
            style = MaterialTheme.typography.bodyLarge,
            color = option.textColor()
        )
    }
}

@Composable
fun OptionsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun EnterVideoIdDialog(
    initialValue: String = "",
    onPlay: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var videoId by remember { mutableStateOf(initialValue) }
    var libraryId by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enter Video ID",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Please enter the ID of the video you want to play",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = videoId,
                    onValueChange = { videoId = it },
                    placeholder = { Text("Video ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please enter the Library ID of the video you want to play",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = libraryId,
                    onValueChange = { libraryId = it },
                    placeholder = { Text("Video Library ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPlay(videoId, libraryId) }
            ) {
                Text("Play", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun OptionsScreenPreview() {
    BunnyStreamTheme {
        HomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            onOptionClick = {},
            onPlayDirect = { videoId, libraryId ->
            },
            onDismiss = { },
        )
    }
}