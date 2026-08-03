package net.bunny.android.demo.livestream

import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunny.android.demo.App
import net.bunny.android.demo.ui.AppState
import net.bunny.api.BunnyStreamApi
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.model.LiveStreamStatus
import net.bunny.bunnystreamplayer.livestream.BunnyLiveStreamPlayer
import net.bunny.bunnystreamplayer.livestream.BunnyLiveStreamPlayerViewModel
import java.util.Locale

/** Lifetime of a demo-signed playback token (1 hour) — long enough for a viewing session. */
private const val PLAYBACK_TOKEN_TTL_SECONDS = 3600L

/**
 * Demo route for live-stream playback. Mirrors the VOD player screen's structure: a 16:9 player
 * up top followed by metadata cards. Status handling, countdown, trailer, polling, and HLS
 * playback live in the SDK ([BunnyLiveStreamPlayer]); this route only renders the chrome and
 * the metadata panel.
 *
 * To keep the player and the metadata panel in sync we obtain the SDK ViewModel directly here
 * and pass the same instance into [BunnyLiveStreamPlayer]. That way the metadata cards read the
 * `liveStream` flow the polling loop is already updating — no second fetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamPlayerRoute(
    appState: AppState,
    streamId: String,
    title: String?,
    modifier: Modifier = Modifier,
    viewModel: BunnyLiveStreamPlayerViewModel = viewModel(),
) {
    LaunchedEffect(streamId) {
        Log.d(
            "BunnyLive/DemoRoute",
            "LiveStreamPlayerRoute entered — streamId=$streamId title='$title' " +
                "libraryId=${App.di.libraryId} apiInit=${BunnyStreamApi.isInitialized()}",
        )
    }

    val liveStream by viewModel.liveStream.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    Text(
                        liveStream?.title?.takeIf { it.isNotBlank() }
                            ?: title.takeUnless { it.isNullOrBlank() }
                            ?: "Live stream",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { appState.navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val libraryId = App.di.libraryId
        if (libraryId == -1L || !BunnyStreamApi.isInitialized()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Bunny Stream API isn't initialized — set the library ID and API key " +
                        "in Settings.",
                    color = Color.White,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Token authentication: when the library has a token-auth key configured (demo reads it
            // from local.properties), sign a short-lived playback token so the SDK can fetch
            // play-data for token-protected streams. Blank key → no token (token auth off).
            // NOTE: demo-only — generate tokens server-side in production, never ship the key.
            val tokenAuthKey = App.di.localPrefs.tokenAuthKey
            val tokenExpires = remember(streamId, tokenAuthKey) {
                if (tokenAuthKey.isBlank()) null
                else System.currentTimeMillis() / 1000 + PLAYBACK_TOKEN_TTL_SECONDS
            }
            val playbackToken = remember(streamId, tokenAuthKey, tokenExpires) {
                if (tokenAuthKey.isBlank() || tokenExpires == null) null
                else EmbedToken.generate(tokenAuthKey, streamId, tokenExpires)
            }

            // Frame adapts to the stream's real aspect ratio so both 16:9 and 9:16 (vertical)
            // content fill correctly; defaults to 16:9 until the first frame's size is known.
            var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
            // Player customization (colour, font, language, controls, compact) is server-driven:
            // the SDK reads it from the live /play endpoint, i.e. the Bunny dashboard's player
            // settings — mirroring the iOS SDK. Nothing to pass from the app.
            BunnyLiveStreamPlayer(
                libraryId = libraryId,
                streamId = streamId,
                token = playbackToken,
                expires = tokenExpires,
                viewModel = viewModel,
                onVideoSizeChanged = { width, height ->
                    if (width > 0 && height > 0) {
                        videoAspectRatio = width.toFloat() / height.toFloat()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status card: status badge + stream lifecycle timestamps. Hidden until the first
            // snapshot arrives so we don't flash an empty card.
            liveStream?.let { stream ->
                LiveStatusCard(stream = stream)
                Spacer(modifier = Modifier.height(16.dp))
                LiveStreamPropertiesCard(stream = stream)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Status pill + lifecycle timestamps. Visually parallels the speed-control card on the VOD
 * screen — same card shape, same horizontal padding.
 */
@Composable
private fun LiveStatusCard(stream: LiveStream) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Current:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusPill(status = stream.status)
            }
        }
    }
}

@Composable
private fun StatusPill(status: LiveStreamStatus) {
    // Colours roughly follow the web dashboard: red for live, blue/grey otherwise. Using
    // primary/error/secondary from the theme so it tracks the demo's palette.
    val (label, color) = when (status) {
        LiveStreamStatus.RUNNING -> "LIVE" to Color(0xFFE53935)
        LiveStreamStatus.SCHEDULED -> "SCHEDULED" to MaterialTheme.colorScheme.primary
        LiveStreamStatus.CREATED -> "CREATED" to MaterialTheme.colorScheme.secondary
        LiveStreamStatus.PREVIEW -> "PREVIEW" to MaterialTheme.colorScheme.secondary
        LiveStreamStatus.ENDED -> "ENDED" to MaterialTheme.colorScheme.outline
        LiveStreamStatus.VOD_PROCESSING -> "PROCESSING" to MaterialTheme.colorScheme.primary
        LiveStreamStatus.ERROR -> "ERROR" to MaterialTheme.colorScheme.error
        LiveStreamStatus.UNKNOWN -> "UNKNOWN" to MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Detail card analogous to the VOD screen's Title/Duration/Views/Size panel. Includes only the
 * properties that meaningfully change from stream to stream — hides empty rows so a brand-new
 * stream with no resolution/framerate yet doesn't show blanks.
 */
@Composable
private fun LiveStreamPropertiesCard(stream: LiveStream) {
    val rows = buildList {
        add("Title" to stream.title.ifBlank { "—" })
        add("Stream ID" to stream.id)
        add("Library ID" to stream.videoLibraryId.toString())
        stream.scheduledStartTime?.takeIf { it.isNotBlank() }?.let {
            add("Scheduled start" to it)
        }
        stream.scheduledEndTime?.takeIf { it.isNotBlank() }?.let {
            add("Scheduled end" to it)
        }
        stream.startedAt?.takeIf { it.isNotBlank() }?.let { add("Started at" to it) }
        stream.endedAt?.takeIf { it.isNotBlank() }?.let { add("Ended at" to it) }
        stream.durationSeconds?.takeIf { it > 0 }?.let {
            add("Duration" to formatDuration(it))
        }
        add("DVR" to if (stream.dvrEnabled) {
            val window = stream.dvrWindowSeconds?.let { " (${formatDuration(it)} window)" } ?: ""
            "Enabled$window"
        } else {
            "Disabled"
        })
        add("Record VOD" to if (stream.recordVod) "Enabled" else "Disabled")
        val width = stream.width
        val height = stream.height
        if (width != null && height != null && width > 0 && height > 0) {
            add("Resolution" to "${width} × ${height}")
        }
        stream.framerate?.takeIf { it > 0 }?.let {
            add("Framerate" to String.format(Locale.US, "%.2f fps", it))
        }
        stream.ingestRegion?.takeIf { it.isNotBlank() }?.let { add("Ingest region" to it) }
        stream.availableResolutions?.takeIf { it.isNotBlank() }?.let {
            add("Available resolutions" to it)
        }
        stream.peakConcurrentViewers?.let { add("Peak viewers" to it.toString()) }
        stream.totalViewerSeconds?.takeIf { it > 0 }?.let {
            add("Total viewer time" to formatDuration((it / 1_000L).toInt()))
        }
        stream.preStreamTrailerVideoId?.takeIf { it.isNotBlank() }?.let {
            add("Pre-stream trailer" to it)
        }
        if (stream.rtmpOutputs.isNotEmpty()) {
            add("RTMP outputs" to stream.rtmpOutputs.size.toString())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            rows.forEachIndexed { index, (label, value) ->
                PropertyRow(label = label, value = value)
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "%dh %02dm %02ds".format(h, m, s)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "${s}s"
    }
}
