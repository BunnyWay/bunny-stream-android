package net.bunny.android.demo.home

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
import androidx.annotation.StringRes
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
 * Titles live in string resources so the demo stays localizable.
 */
sealed class HomeOption(
    @StringRes val titleRes: Int,
    val textColor: @Composable () -> Color = { MaterialTheme.colorScheme.onSurface }
) {
    object VideoPlayer : HomeOption(R.string.home_option_video_player)

    object VideoUpload : HomeOption(R.string.home_option_video_upload)

    object CameraUpload : HomeOption(R.string.home_option_camera_upload)

    object LiveStreams : HomeOption(R.string.home_option_live_streams)

    object DirectVideoPlay : HomeOption(
        R.string.home_option_direct_play,
        textColor = { MaterialTheme.colorScheme.primary })

    object BunnyStreamConfiguration : HomeOption(
        R.string.screen_settings,
        textColor = { MaterialTheme.colorScheme.primary }
    )

    object ResumePositionSettings : HomeOption(
        R.string.resume_position_settings,
        textColor = { MaterialTheme.colorScheme.primary }
    )

    object ResumePositionManagement : HomeOption(
        R.string.home_option_resume_management,
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
    navigateToLiveStreams: () -> Unit,
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

                HomeOption.LiveStreams -> {
                    navigateToLiveStreams()
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
            navigateToPlayer(videoId, libraryId)
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
    onPlayDirect: (String, Long) -> Unit,
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
        HomeOption.LiveStreams,
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
            OptionsCategory(title = stringResource(R.string.home_category_actions))
            OptionsGroupCard(items = actionItems, onItemClick = onOptionClick)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            OptionsCategory(title = stringResource(R.string.home_category_resume))
            OptionsGroupCard(items = resumeItems, onItemClick = onOptionClick)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            OptionsCategory(title = stringResource(R.string.home_category_configuration))
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
            text = stringResource(option.titleRes),
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
    onPlay: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var videoId by remember { mutableStateOf(initialValue) }
    var libraryId by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val errorVideoIdEmpty = stringResource(R.string.error_video_id_empty)
    val errorLibraryIdInvalid = stringResource(R.string.error_library_id_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_enter_video_id_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_enter_video_id_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = videoId,
                    onValueChange = {
                        videoId = it
                        errorMessage = null
                    },
                    placeholder = { Text(stringResource(R.string.placeholder_video_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_enter_library_id_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = libraryId,
                    onValueChange = {
                        libraryId = it
                        errorMessage = null
                    },
                    placeholder = { Text(stringResource(R.string.placeholder_library_id)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.note_direct_play_access_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedVideoId = videoId.trim().substringBefore('?')
                    val trimmedLibraryId = libraryId.trim().substringBefore('?')
                    val parsedLibraryId = trimmedLibraryId.toLongOrNull()
                    when {
                        trimmedVideoId.isEmpty() -> {
                            errorMessage = errorVideoIdEmpty
                        }
                        parsedLibraryId == null -> {
                            errorMessage = errorLibraryIdInvalid
                        }
                        else -> onPlay(trimmedVideoId, parsedLibraryId)
                    }
                }
            ) {
                Text(stringResource(R.string.button_play), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.button_cancel))
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
            onPlayDirect = { _, _ -> },
            onDismiss = { },
        )
    }
}