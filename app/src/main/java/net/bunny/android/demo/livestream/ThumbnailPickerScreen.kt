package net.bunny.android.demo.livestream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import net.bunny.android.demo.ui.AppState

/**
 * Lets the user pick an existing library video's thumbnail to use as a live stream's thumbnail —
 * the image equivalent of [TrailerPickerRoute]. Reuses [TrailerPickerViewModel] (a plain library
 * video list) and only offers videos that already have a thumbnail. Selection (the thumbnail URL)
 * is returned to the editor via [onPicked]; this screen never mutates anything.
 */
@Composable
fun ThumbnailPickerRoute(
    appState: AppState,
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrailerPickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ThumbnailPickerScreen(
        modifier = modifier,
        state = state,
        onBack = { appState.navController.popBackStack() },
        onSelect = onPicked,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailPickerScreen(
    modifier: Modifier,
    state: TrailerPickerViewModel.State,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text("Choose thumbnail") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = "Back",
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
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (state) {
                is TrailerPickerViewModel.State.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is TrailerPickerViewModel.State.Failed -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Couldn't load videos: ${state.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }

                is TrailerPickerViewModel.State.Loaded -> {
                    // Only videos that actually have a thumbnail can supply one.
                    val withThumbs = state.videos.filter { !it.thumbnailUrl.isNullOrBlank() }
                    if (withThumbs.isEmpty()) {
                        Text(
                            text = "No video thumbnails available in this library yet",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(withThumbs) { video ->
                                ThumbnailPickRow(
                                    video = video,
                                    onClick = { video.thumbnailUrl?.let(onSelect) },
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
private fun ThumbnailPickRow(
    video: TrailerPickerViewModel.TrailerVideo,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
