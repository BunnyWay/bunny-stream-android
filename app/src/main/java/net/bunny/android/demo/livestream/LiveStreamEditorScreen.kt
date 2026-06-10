package net.bunny.android.demo.livestream

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunny.android.demo.player.BunnyPlayerComposable
import net.bunny.android.demo.recording.GoLiveActivity
import net.bunny.android.demo.ui.AppState
import net.bunny.api.BunnyStreamApi
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.model.LiveStreamStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Full-screen create/edit form for live streams — replaces the old [AlertDialog]-based editor.
 *
 * Exposes everything the Manage Live Streams API accepts on create/update, mirroring the web
 * dashboard's "Advanced settings": scheduling, DVR window, countdown, pre-stream trailer,
 * category and collection. A thumbnail can be attached too — either a locally-picked image
 * (uploaded as binary) or a remote URL — and is applied once the stream is created/updated.
 * (Watermark upload uses a separate endpoint not yet in the SDK, so it is not included here.)
 */
@Composable
fun LiveStreamEditorRoute(
    appState: AppState,
    streamId: String?,
    modifier: Modifier = Modifier,
    viewModel: LiveStreamEditorViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(streamId) {
        if (streamId != null) viewModel.load(streamId)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            Log.d("BunnyLive/EditorUI", "saved — popping back")
            appState.navController.popBackStack()
        }
    }

    uiState.error?.also { message ->
        ErrorDialog(message = message, onDismiss = viewModel::onErrorDismissed)
    }

    val created = uiState.createdStream
    if (created != null) {
        val context = LocalContext.current
        LiveStreamSummaryScreen(
            modifier = modifier,
            stream = created,
            onDone = { appState.navController.popBackStack() },
            onGoLive = {
                context.startActivity(GoLiveActivity.createIntent(context, created.id))
            },
            onWatch = {
                appState.navController.navigateToLiveStreamPlayer(
                    streamId = created.id,
                    fallbackHlsUrl = created.playbackUrlHls,
                    title = created.title,
                )
            },
        )
    } else {
        LiveStreamEditorScreen(
            modifier = modifier,
            isEdit = streamId != null,
            loading = uiState.loading,
            saving = uiState.saving,
            initial = uiState.stream,
            trailer = uiState.trailer,
            onUploadTrailer = viewModel::uploadTrailer,
            onDeleteTrailer = viewModel::deleteTrailer,
            onBack = { appState.navController.popBackStack() },
            onSubmit = { request, thumbnail -> viewModel.save(streamId, request, thumbnail) },
        )
    }
}

/**
 * Post-create summary mirroring the dashboard's "Live stream links" card: stream key, ingest
 * URLs, publish URL, HLS playlist and IDs — each copyable to the clipboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveStreamSummaryScreen(
    modifier: Modifier,
    stream: LiveStream,
    onDone: () -> Unit,
    onGoLive: () -> Unit,
    onWatch: () -> Unit,
) {
    val ingest = net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT
    val publishUrl = stream.streamKey?.let { "$ingest/$it" }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text("Live stream created") },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = "Done",
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stream.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(16.dp))

            RtmpStreamDetailsCard(stream = stream)

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Live stream links") {
                CopyableRow("RTMP publish URL (ingest + key)", publishUrl)
                CopyableRow("Live HLS Playlist URL", stream.playbackUrlHls)
                CopyableRow("Video ID", stream.id)
                CopyableRow("Pre-stream trailer video ID", stream.preStreamTrailerVideoId)
                CopyableRow("Thumbnail file", stream.thumbnailFileName)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onGoLive, modifier = Modifier.fillMaxWidth()) {
                Text("Go live now")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
                Text("Watch live stream")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Mirror of the dashboard's "RTMP stream details" card: stream key, primary/backup ingest URL
 * (copyable) and the current stream status.
 */
@Composable
private fun RtmpStreamDetailsCard(stream: LiveStream) {
    val ingest = net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT

    SectionCard(title = "RTMP stream details") {
        CopyableRow("Stream key", stream.streamKey)
        CopyableRow("Primary ingest URL", ingest)
        CopyableRow("Backup ingest URL", ingest)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = statusLabel(stream.status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun statusLabel(status: LiveStreamStatus): String = when (status) {
    LiveStreamStatus.CREATED, LiveStreamStatus.SCHEDULED -> "Ready for live stream"
    LiveStreamStatus.PREVIEW -> "Preview"
    LiveStreamStatus.RUNNING -> "Live"
    LiveStreamStatus.ENDED -> "Ended"
    LiveStreamStatus.VOD_PROCESSING -> "Processing VOD"
    LiveStreamStatus.ERROR -> "Error"
    LiveStreamStatus.UNKNOWN -> "Unknown"
}

/** Label + value row with a copy-to-clipboard action. Hidden when [value] is null/blank. */
@Composable
private fun CopyableRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveStreamEditorScreen(
    modifier: Modifier,
    isEdit: Boolean,
    loading: Boolean,
    saving: Boolean,
    initial: LiveStream?,
    trailer: LiveStreamEditorViewModel.TrailerState,
    onUploadTrailer: (Uri) -> Unit,
    onDeleteTrailer: () -> Unit,
    onBack: () -> Unit,
    onSubmit: (LiveStreamCreateRequest, LiveStreamEditorViewModel.ThumbnailSource?) -> Unit,
) {
    val context = LocalContext.current
    // Form state, re-seeded when the stream loads (edit mode).
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    // Collection retained from the loaded stream so edits don't wipe it (no UI field after the
    // Advanced section was removed).
    val collectionId by remember(initial) { mutableStateOf(initial?.collectionId.orEmpty()) }
    var isPublic by remember(initial) { mutableStateOf(initial?.isPublic ?: true) }
    var recordVod by remember(initial) { mutableStateOf(initial?.recordVod ?: false) }
    var enableCountdown by remember(initial) { mutableStateOf(initial?.enableCountdown ?: false) }
    var scheduleEnabled by remember(initial) {
        mutableStateOf(!initial?.scheduledStartTime.isNullOrBlank())
    }
    var scheduledStart by remember(initial) {
        mutableStateOf(initial?.scheduledStartTime.orEmpty())
    }
    var scheduledEnd by remember(initial) { mutableStateOf(initial?.scheduledEndTime.orEmpty()) }
    var dvrEnabled by remember(initial) { mutableStateOf(initial?.dvrEnabled ?: false) }
    var dvrWindow by remember(initial) {
        mutableStateOf(initial?.dvrWindowSeconds?.let(::formatHms) ?: "12:00:00")
    }
    // Pre-stream trailer on/off. The trailer video itself is owned by the ViewModel ([trailer]);
    // this only toggles whether the form submits it. Seeded on from an existing trailer (edit mode).
    var trailerEnabled by remember(initial) {
        mutableStateOf(!initial?.preStreamTrailerVideoId.isNullOrBlank())
    }
    val trailerVideoId = (trailer as? LiveStreamEditorViewModel.TrailerState.Ready)?.videoId

    val pickTrailer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) onUploadTrailer(uri) },
    )

    // Thumbnail: either a locally-picked image (uploaded as binary) or a remote URL. Picking one
    // clears the other so submit has a single, unambiguous source. The switch reveals the options
    // and is seeded on when the stream already has a thumbnail (edit mode).
    var thumbnailEnabled by remember(initial) {
        mutableStateOf(!initial?.thumbnailFileName.isNullOrBlank())
    }
    var thumbnailUri by remember(initial) { mutableStateOf<Uri?>(null) }
    var thumbnailUrl by remember(initial) { mutableStateOf("") }

    val pickThumbnail = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                thumbnailUri = uri
                thumbnailUrl = ""
            }
        },
    )

    val dvrWindowSeconds = parseHms(dvrWindow)
    val dvrWindowError = dvrEnabled && dvrWindowSeconds == null
    val canSubmit = title.isNotBlank() && !dvrWindowError && !saving && !loading

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text(if (isEdit) "Edit live stream" else "New live stream") },
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (initial != null) {
                RtmpStreamDetailsCard(stream = initial)
                Spacer(modifier = Modifier.height(16.dp))
            }

            SectionCard(title = "Details") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow("Public", isPublic) { isPublic = it }
                SwitchRow(
                    label = "Video on demand",
                    subtitle = "Store the stream in your library as VOD when it ends",
                    checked = recordVod,
                ) { recordVod = it }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Schedule") {
                SwitchRow(
                    label = "Schedule start date and time",
                    subtitle = "Select when you want to go live",
                    checked = scheduleEnabled,
                ) { scheduleEnabled = it }
                if (scheduleEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTimePickerField(
                        label = "Scheduled start",
                        isoValue = scheduledStart,
                        onIsoChange = { scheduledStart = it },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTimePickerField(
                        label = "Scheduled end (optional)",
                        isoValue = scheduledEnd,
                        onIsoChange = { scheduledEnd = it },
                        clearable = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        label = "Enable countdown",
                        subtitle = "Show a countdown in the player before the stream starts",
                        checked = enableCountdown,
                    ) { enableCountdown = it }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "DVR") {
                SwitchRow(
                    label = "DVR",
                    subtitle = "Let viewers rewind behind the live point (30s – 12hrs)",
                    checked = dvrEnabled,
                ) { dvrEnabled = it }
                if (dvrEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dvrWindow,
                        onValueChange = { dvrWindow = it },
                        label = { Text("DVR timeframe (HH:MM:SS)") },
                        placeholder = { Text("12:00:00") },
                        singleLine = true,
                        isError = dvrWindowError,
                        supportingText = {
                            if (dvrWindowError) Text("Use HH:MM:SS between 00:00:30 and 12:00:00")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Pre-stream trailer") {
                SwitchRow(
                    label = "Pre-stream trailer",
                    subtitle = "Play a short video in the player before the stream starts",
                    checked = trailerEnabled,
                ) { trailerEnabled = it }

                if (trailerEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TrailerControl(
                        trailer = trailer,
                        onPick = {
                            pickTrailer.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly,
                                ),
                            )
                        },
                        onDelete = onDeleteTrailer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Thumbnail") {
                SwitchRow(
                    label = "Thumbnail",
                    subtitle = "Set a custom thumbnail image for the stream",
                    checked = thumbnailEnabled,
                ) { thumbnailEnabled = it }

                if (thumbnailEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (thumbnailUri != null) {
                        AsyncImage(
                            model = thumbnailUri,
                            contentDescription = "Selected thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                pickThumbnail.launch(
                                    PickVisualMediaRequest(
                                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (thumbnailUri == null) "Choose image" else "Change image")
                        }
                        if (thumbnailUri != null) {
                            TextButton(onClick = { thumbnailUri = null }) { Text("Remove") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = thumbnailUrl,
                        onValueChange = {
                            thumbnailUrl = it
                            if (it.isNotBlank()) thumbnailUri = null
                        },
                        label = { Text("…or image URL") },
                        placeholder = { Text("https://example.com/thumb.jpg") },
                        singleLine = true,
                        enabled = thumbnailUri == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val thumbnail = if (thumbnailEnabled) {
                        resolveThumbnail(context, thumbnailUri, thumbnailUrl)
                    } else {
                        null
                    }
                    onSubmit(
                        LiveStreamCreateRequest(
                            title = title.trim(),
                            description = description.trim().takeIf { it.isNotEmpty() },
                            collectionId = collectionId.trim().takeIf { it.isNotEmpty() },
                            isPublic = isPublic,
                            scheduledStartTime = scheduledStart.trim()
                                .takeIf { scheduleEnabled && it.isNotEmpty() },
                            scheduledEndTime = scheduledEnd.trim()
                                .takeIf { scheduleEnabled && it.isNotEmpty() },
                            dvrEnabled = dvrEnabled,
                            dvrWindowSeconds = if (dvrEnabled) dvrWindowSeconds else null,
                            recordVod = recordVod,
                            enableCountdown = if (scheduleEnabled) enableCountdown else null,
                            preStreamTrailerVideoId = trailerVideoId?.takeIf { trailerEnabled },
                        ),
                        thumbnail,
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        saving -> "Saving…"
                        isEdit -> "Save changes"
                        else -> "Create live stream"
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Upload / preview / delete control for the pre-stream trailer, driven by
 * [LiveStreamEditorViewModel.TrailerState]:
 *  - [None][LiveStreamEditorViewModel.TrailerState.None] / [Failed][LiveStreamEditorViewModel.TrailerState.Failed]
 *    → an "Upload" button (and the failure message).
 *  - [Uploading][LiveStreamEditorViewModel.TrailerState.Uploading] → a progress bar.
 *  - [Ready][LiveStreamEditorViewModel.TrailerState.Ready] → an inline player preview plus
 *    Replace / Delete actions.
 */
@Composable
private fun TrailerControl(
    trailer: LiveStreamEditorViewModel.TrailerState,
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    when (trailer) {
        is LiveStreamEditorViewModel.TrailerState.None,
        is LiveStreamEditorViewModel.TrailerState.Failed -> {
            if (trailer is LiveStreamEditorViewModel.TrailerState.Failed) {
                Text(
                    text = "Upload failed: ${trailer.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text("Upload trailer video")
            }
        }

        is LiveStreamEditorViewModel.TrailerState.Uploading -> {
            Text(
                text = "Uploading… ${trailer.percentage}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { trailer.percentage / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is LiveStreamEditorViewModel.TrailerState.Ready -> {
            BunnyPlayerComposable(
                videoId = trailer.videoId,
                libraryId = BunnyStreamApi.libraryId,
                resumeEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                    Text("Replace")
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/**
 * A read-only field that opens a Material date picker, then a time picker, and writes the chosen
 * moment back as an ISO-8601 UTC string (e.g. `2026-06-07T18:00:00Z`) via [onIsoChange]. The
 * displayed text is the same moment rendered in the device's local time zone. The wall-clock time
 * the user picks is interpreted as local time and converted to UTC for storage, matching how the
 * Stream API and the player treat scheduled times.
 *
 * @param clearable when true, shows a clear (✕) action so an optional value (e.g. scheduled end)
 *                  can be removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerField(
    label: String,
    isoValue: String,
    onIsoChange: (String) -> Unit,
    clearable: Boolean = false,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Date chosen in the date dialog, carried until the time is also chosen.
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val existing = remember(isoValue) { isoToLocalDateTime(isoValue) }

    // Detect taps on the field body (it's read-only, so it won't open a keyboard) and open the
    // date picker. Using an interaction source keeps the trailing icon independently clickable.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDatePicker = true
        }
    }

    OutlinedTextField(
        value = isoToDisplay(isoValue),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Tap to select date & time") },
        trailingIcon = {
            if (clearable && isoValue.isNotBlank()) {
                IconButton(onClick = { onIsoChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear $label")
                }
            } else {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            }
        },
        interactionSource = interactionSource,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = existing
                ?.toLocalDate()
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
                ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateMillis = dateState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = existing?.hour ?: 12,
            initialMinute = existing?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDateMillis?.let { dateMillis ->
                        onIsoChange(pickedToIso(dateMillis, timeState.hour, timeState.minute))
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            title = { Text("Select time") },
            text = { TimePicker(state = timeState) },
        )
    }
}

/** Lenient ISO-8601 -> Instant: accepts both zoned (`...Z`) and zone-less (treated as UTC) forms. */
private fun isoToInstant(iso: String): Instant? {
    if (iso.isBlank()) return null
    return try {
        Instant.parse(iso)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
            null
        }
    }
}

/** The stored ISO instant rendered in the device's local time zone, or null if unparseable. */
private fun isoToLocalDateTime(iso: String): LocalDateTime? =
    isoToInstant(iso)?.atZone(ZoneId.systemDefault())?.toLocalDateTime()

/** Human-readable local-time label for the field (empty when there's no value yet). */
private fun isoToDisplay(iso: String): String =
    isoToLocalDateTime(iso)?.format(DISPLAY_DATE_TIME) ?: ""

/**
 * Combines the date picker's selected day (UTC-midnight millis) with the picked wall-clock time,
 * interpreting the result in the device's local zone, and returns it as an ISO-8601 UTC string.
 */
private fun pickedToIso(dateMillisUtc: Long, hour: Int, minute: Int): String {
    val date = Instant.ofEpochMilli(dateMillisUtc).atZone(ZoneOffset.UTC).toLocalDate()
    val local = LocalDateTime.of(date, LocalTime.of(hour, minute))
    return local.atZone(ZoneId.systemDefault()).toInstant().toString()
}

private val DISPLAY_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm")

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text("Error") },
        text = { Text(message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

/**
 * Builds a [LiveStreamEditorViewModel.ThumbnailSource] from the form's thumbnail inputs: a
 * locally-picked [uri] (read into bytes via the [ContentResolver][android.content.ContentResolver])
 * takes precedence over a remote [url]. Returns null when neither is set or the image can't be read.
 */
private fun resolveThumbnail(
    context: android.content.Context,
    uri: Uri?,
    url: String,
): LiveStreamEditorViewModel.ThumbnailSource? = when {
    uri != null -> {
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            null
        } else {
            LiveStreamEditorViewModel.ThumbnailSource.Local(
                bytes = bytes,
                contentType = resolver.getType(uri) ?: "image/*",
            )
        }
    }

    url.isNotBlank() -> LiveStreamEditorViewModel.ThumbnailSource.Url(url.trim())
    else -> null
}

/** Parses "HH:MM:SS" into seconds; null if malformed or outside 30s..12h. */
internal fun parseHms(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 3) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val s = parts[2].toIntOrNull() ?: return null
    if (m !in 0..59 || s !in 0..59 || h < 0) return null
    val total = h * 3600 + m * 60 + s
    return total.takeIf { it in 30..(12 * 3600) }
}

internal fun formatHms(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
