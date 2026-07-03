package net.bunny.android.demo.livestream

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import net.bunny.android.demo.player.BunnyPlayerComposable
import net.bunny.android.demo.App
import net.bunny.android.demo.recording.GoLiveActivity
import net.bunny.android.demo.ui.AppState
import net.bunny.api.BunnyStreamApi
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.RtmpOutput
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
 * A library-level watermark can also be uploaded here, mirroring the web dashboard's uploader on
 * this page — note it applies to every stream in the library (Bunny has no per-stream watermark)
 * and its position/size still live in the Settings screen.
 */
@Composable
fun LiveStreamEditorRoute(
    appState: AppState,
    streamId: String?,
    savedStateHandle: SavedStateHandle? = null,
    modifier: Modifier = Modifier,
    viewModel: LiveStreamEditorViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(streamId) {
        if (streamId != null) viewModel.load(streamId)
    }

    // A trailer video chosen on the picker screen is handed back via this entry's savedStateHandle.
    // Apply it to the ViewModel, then clear the key so it isn't re-applied on later recompositions.
    val pickedTrailerId by remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(TRAILER_PICK_RESULT_KEY, null)
            ?: MutableStateFlow<String?>(null)
    }.collectAsStateWithLifecycle()
    LaunchedEffect(pickedTrailerId) {
        pickedTrailerId?.takeIf { it.isNotBlank() }?.let { id ->
            viewModel.selectTrailer(id)
            savedStateHandle?.set(TRAILER_PICK_RESULT_KEY, null)
        }
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

        // A live stream can only be broadcast once. The summary holds a snapshot taken at create
        // time (status CREATED), so after the user goes live and returns here we re-fetch the
        // stream on resume to pick up its new status — which lets the screen hide "Go live now".
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCreatedStream()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

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
            onClearTrailer = viewModel::clearTrailer,
            onPickTrailerFromLibrary = { appState.navController.navigateToTrailerPicker() },
            thumbnails = uiState.thumbnails,
            onDeleteThumbnail = { streamId?.let(viewModel::deleteThumbnail) },
            onPickThumbnailFromLibrary = { appState.navController.navigateToThumbnailPicker() },
            watermark = uiState.watermark,
            watermarkPreviewUri = uiState.watermarkPreviewUri,
            onUploadWatermark = viewModel::uploadWatermark,
            onRemoveWatermark = viewModel::removeWatermark,
            savedStateHandle = savedStateHandle,
            onBack = { appState.navController.popBackStack() },
            onSubmit = { request, thumbnail, dual -> viewModel.save(streamId, request, thumbnail, dual) },
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
    val ingest = stream.primaryIngestUrl ?: net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT
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

            // A live stream can only be broadcast once: the "Go live" action is only meaningful
            // while it's still waiting to be used (created/scheduled, or an encoder is connected in
            // PREVIEW). Once it has gone RUNNING — or has ENDED / is processing VOD — going live
            // again is no longer possible, so hide the button instead of letting it fail.
            if (canGoLive(stream.status)) {
                Button(onClick = onGoLive, modifier = Modifier.fillMaxWidth()) {
                    Text("Go live now")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
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
    val fallback = net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT

    SectionCard(title = "RTMP stream details") {
        CopyableRow("Stream key", stream.streamKey)
        CopyableRow("Ingest URL", stream.primaryIngestUrl ?: fallback, badge = "Primary")
        CopyableRow("Ingest URL", stream.backupIngestUrl ?: fallback, badge = "Backup")
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

/**
 * Whether the stream can still be broadcast. A Bunny live stream is single-use: it can only be
 * taken live before it has ever run. `CREATED`/`SCHEDULED` are "ready"; `PREVIEW` means an encoder
 * is connected but not yet live (go-live transitions it to `RUNNING`). Everything else — already
 * `RUNNING`, `ENDED`, `VOD_PROCESSING`, `ERROR`, `UNKNOWN` — can no longer go live.
 */
private fun canGoLive(status: LiveStreamStatus): Boolean = when (status) {
    LiveStreamStatus.CREATED,
    LiveStreamStatus.SCHEDULED,
    LiveStreamStatus.PREVIEW -> true

    LiveStreamStatus.RUNNING,
    LiveStreamStatus.ENDED,
    LiveStreamStatus.VOD_PROCESSING,
    LiveStreamStatus.ERROR,
    LiveStreamStatus.UNKNOWN -> false
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

/**
 * Label + value row with a copy-to-clipboard action. Hidden when [value] is null/blank.
 *
 * @param badge optional pill shown next to the label (e.g. "Primary"/"Backup" on ingest URLs).
 */
@Composable
private fun CopyableRow(label: String, value: String?, badge: String? = null) {
    if (value.isNullOrBlank()) return
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    RtmpBadge(text = badge)
                }
            }
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

/** Small rounded pill used to tag a row, e.g. the Primary/Backup ingest URLs. */
@Composable
private fun RtmpBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
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
    onClearTrailer: () -> Unit,
    onPickTrailerFromLibrary: () -> Unit,
    thumbnails: LiveStreamEditorViewModel.ThumbnailListState,
    onDeleteThumbnail: () -> Unit,
    onPickThumbnailFromLibrary: () -> Unit,
    watermark: LiveStreamEditorViewModel.WatermarkState,
    watermarkPreviewUri: Uri?,
    onUploadWatermark: (ByteArray, String) -> Unit,
    onRemoveWatermark: () -> Unit,
    savedStateHandle: SavedStateHandle?,
    onBack: () -> Unit,
    onSubmit: (LiveStreamCreateRequest, LiveStreamEditorViewModel.ThumbnailSource?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Form state, re-seeded when the stream loads (edit mode). Uses rememberSaveable so it survives
    // navigating away to a sub-screen (e.g. the trailer picker) and back, which disposes this
    // composable.
    var title by rememberSaveable(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable(initial) { mutableStateOf(initial?.description.orEmpty()) }
    // Collection retained from the loaded stream so edits don't wipe it (no UI field after the
    // Advanced section was removed).
    val collectionId by rememberSaveable(initial) { mutableStateOf(initial?.collectionId.orEmpty()) }
    var isPublic by rememberSaveable(initial) { mutableStateOf(initial?.isPublic ?: true) }
    var recordVod by rememberSaveable(initial) { mutableStateOf(initial?.recordVod ?: false) }
    var enableCountdown by rememberSaveable(initial) { mutableStateOf(initial?.enableCountdown ?: false) }
    var scheduleEnabled by rememberSaveable(initial) {
        mutableStateOf(!initial?.scheduledStartTime.isNullOrBlank())
    }
    var scheduledStart by rememberSaveable(initial) {
        mutableStateOf(initial?.scheduledStartTime.orEmpty())
    }
    var scheduledEnd by rememberSaveable(initial) { mutableStateOf(initial?.scheduledEndTime.orEmpty()) }
    // Time zone the user picks scheduled times in. Stored times are always UTC instants; this only
    // controls how the wall-clock time is interpreted on pick and rendered for display. Defaults to
    // the device zone, preserving prior behavior. Held as the zone *id* so it's Bundle-saveable.
    var scheduleZoneId by rememberSaveable(initial) { mutableStateOf(ZoneId.systemDefault().id) }
    val scheduleZone = remember(scheduleZoneId) {
        runCatching { ZoneId.of(scheduleZoneId) }.getOrDefault(ZoneId.systemDefault())
    }
    var dvrEnabled by rememberSaveable(initial) { mutableStateOf(initial?.dvrEnabled ?: false) }
    var dvrWindow by rememberSaveable(initial) {
        mutableStateOf(initial?.dvrWindowSeconds?.let(::formatHms) ?: "12:00:00")
    }
    // Client-side broadcast option (not a Bunny stream field): seeded from the per-stream local
    // preference in edit mode, off for a new stream.
    var dualPublish by rememberSaveable(initial) {
        mutableStateOf(initial?.id?.let { App.di.dualPublishPreferences.isDualPublish(it) } ?: false)
    }
    // Pre-stream trailer on/off. The trailer video itself is owned by the ViewModel ([trailer]);
    // this only toggles whether the form submits it. Seeded on from an existing trailer (edit mode).
    var trailerEnabled by rememberSaveable(initial) {
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
    var thumbnailEnabled by rememberSaveable(initial) {
        mutableStateOf(!initial?.thumbnailFileName.isNullOrBlank())
    }
    var thumbnailUri by rememberSaveable(initial) { mutableStateOf<Uri?>(null) }
    var thumbnailUrl by rememberSaveable(initial) { mutableStateOf("") }
    // URL of the generated thumbnail currently shown enlarged in the preview dialog (null = closed).
    var thumbnailPreviewUrl by remember { mutableStateOf<String?>(null) }

    // A thumbnail picked from the library picker comes back via this entry's savedStateHandle.
    // Apply it as the (URL) thumbnail source, then clear the key so it isn't re-applied.
    val pickedThumbnailUrl by remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(THUMBNAIL_PICK_RESULT_KEY, null)
            ?: MutableStateFlow<String?>(null)
    }.collectAsStateWithLifecycle()
    LaunchedEffect(pickedThumbnailUrl) {
        pickedThumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            thumbnailEnabled = true
            thumbnailUri = null
            thumbnailUrl = url
            savedStateHandle?.set(THUMBNAIL_PICK_RESULT_KEY, null)
        }
    }

    // RTMP outputs (up to 4): the incoming stream is forwarded to each destination. Seeded from an
    // existing stream (edit mode); rememberSaveable (via a flat-string saver) so entries survive
    // sub-screen navigation.
    val rtmpOutputs = rememberSaveable(initial, saver = rtmpOutputsSaver) {
        mutableStateListOf<RtmpOutputDraft>().apply {
            initial?.rtmpOutputs?.forEach { add(RtmpOutputDraft(it.endpoint.orEmpty(), it.streamKey.orEmpty())) }
        }
    }

    val pickThumbnail = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                thumbnailUri = uri
                thumbnailUrl = ""
            }
        },
    )

    // Watermark is library-wide (Bunny has no per-stream watermark): picking an image uploads it
    // immediately to the whole library, mirroring the web dashboard's uploader on this page.
    val pickWatermark = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val resolver = context.contentResolver
                val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
                    .getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    onUploadWatermark(bytes, resolver.getType(uri) ?: "image/png")
                }
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
                    TimeZonePickerField(
                        zone = scheduleZone,
                        onZoneChange = { scheduleZoneId = it.id },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTimePickerField(
                        label = "Scheduled start",
                        isoValue = scheduledStart,
                        onIsoChange = { scheduledStart = it },
                        zone = scheduleZone,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTimePickerField(
                        label = "Scheduled end (optional)",
                        isoValue = scheduledEnd,
                        onIsoChange = { scheduledEnd = it },
                        zone = scheduleZone,
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
                        onPickUpload = {
                            pickTrailer.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly,
                                ),
                            )
                        },
                        onPickFromLibrary = onPickTrailerFromLibrary,
                        onDelete = onDeleteTrailer,
                        onClear = onClearTrailer,
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
                    OutlinedButton(
                        onClick = onPickThumbnailFromLibrary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose from library")
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

                // Generated thumbnails (list / preview / delete) — only an existing stream has them.
                if (initial != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ThumbnailGallery(
                        state = thumbnails,
                        onPreview = { thumbnailPreviewUrl = it },
                        onDelete = onDeleteThumbnail,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Watermark") {
                WatermarkControl(
                    state = watermark,
                    previewUri = watermarkPreviewUri,
                    onPick = {
                        pickWatermark.launch(
                            PickVisualMediaRequest(
                                mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onRemove = onRemoveWatermark,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "RTMP outputs") {
                Text(
                    text = "Forward the incoming stream to up to 4 external RTMP destinations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                rtmpOutputs.forEachIndexed { index, output ->
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = output.url,
                        onValueChange = { rtmpOutputs[index] = output.copy(url = it) },
                        label = { Text("Stream URL") },
                        placeholder = { Text("rtmp://live.example.com/app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = output.key,
                        onValueChange = { rtmpOutputs[index] = output.copy(key = it) },
                        label = { Text("Stream Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { rtmpOutputs.removeAt(index) }) { Text("Remove") }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (rtmpOutputs.size < MAX_RTMP_OUTPUTS) {
                    OutlinedButton(
                        onClick = { rtmpOutputs.add(RtmpOutputDraft("", "")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add RTMP output")
                    }
                } else {
                    Text(
                        text = "Maximum of $MAX_RTMP_OUTPUTS RTMP outputs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Dual publish") {
                SwitchRow(
                    label = "Publish to primary + backup at once",
                    checked = dualPublish,
                    subtitle = "Streams to both ingests simultaneously for instant failover. " +
                        "Doubles the upload bandwidth, so use it only on a strong connection.",
                ) { dualPublish = it }
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
                            rtmpOutputs = rtmpOutputs
                                .mapNotNull { draft ->
                                    val url = draft.url.trim()
                                    if (url.isEmpty()) {
                                        null
                                    } else {
                                        RtmpOutput(
                                            endpoint = url,
                                            streamKey = draft.key.trim().takeIf { it.isNotEmpty() },
                                        )
                                    }
                                }
                                .takeIf { it.isNotEmpty() },
                        ),
                        thumbnail,
                        dualPublish,
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

        thumbnailPreviewUrl?.let { url ->
            ThumbnailPreviewDialog(url = url, onDismiss = { thumbnailPreviewUrl = null })
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
 * Upload / choose-from-library / preview / delete control for the pre-stream trailer, driven by
 * [LiveStreamEditorViewModel.TrailerState]:
 *  - [None][LiveStreamEditorViewModel.TrailerState.None] / [Failed][LiveStreamEditorViewModel.TrailerState.Failed]
 *    → an "Upload" button plus a "Choose from library" button (and the failure message).
 *  - [Uploading][LiveStreamEditorViewModel.TrailerState.Uploading] → a progress bar.
 *  - [Ready][LiveStreamEditorViewModel.TrailerState.Ready] → an inline player preview plus a
 *    "Replace" (re-pick from library) action and either "Delete" (uploaded → removed from library)
 *    or "Remove" (existing/selected → deselect only), per [Ready.deletable].
 *
 * @param onPickUpload pick a local video file to upload as the trailer.
 * @param onPickFromLibrary open the picker to choose an existing library video.
 * @param onDelete delete an uploaded trailer video from the library.
 * @param onClear deselect a trailer without deleting the underlying video.
 */
@Composable
private fun TrailerControl(
    trailer: LiveStreamEditorViewModel.TrailerState,
    onPickUpload: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
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
            Button(onClick = onPickUpload, modifier = Modifier.fillMaxWidth()) {
                Text("Upload trailer video")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onPickFromLibrary, modifier = Modifier.fillMaxWidth()) {
                Text("Choose from library")
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
                OutlinedButton(onClick = onPickFromLibrary, modifier = Modifier.weight(1f)) {
                    Text("Replace")
                }
                if (trailer.deletable) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                } else {
                    TextButton(onClick = onClear) { Text("Remove") }
                }
            }
        }
    }
}

/**
 * Library-level watermark control mirroring the web dashboard's uploader on the live-stream page:
 * upload a PNG logo, preview the last image uploaded from this app, and remove it. Driven by
 * [LiveStreamEditorViewModel.WatermarkState].
 *
 * The watermark is **library-wide** — Bunny has no per-stream watermark — so uploading here applies
 * it to every stream in the library. Placement (position/size) lives in the Settings screen.
 *
 * @param previewUri the last watermark image uploaded from this app (Bunny exposes no API to read
 *        back a watermark set elsewhere, e.g. via the dashboard), or null if none.
 */
@Composable
private fun WatermarkControl(
    state: LiveStreamEditorViewModel.WatermarkState,
    previewUri: Uri?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val working = state is LiveStreamEditorViewModel.WatermarkState.Working

    Text(
        text = "Upload a PNG logo to watermark this stream. Applies to every live stream in the " +
            "library, and needs your account API key (set it in Settings).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (previewUri != null) {
        Spacer(modifier = Modifier.height(12.dp))
        AsyncImage(
            model = previewUri,
            contentDescription = "Watermark preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onPick, enabled = !working, modifier = Modifier.weight(1f)) {
            Text(if (previewUri == null) "Upload watermark" else "Replace watermark")
        }
        if (previewUri != null) {
            TextButton(onClick = onRemove, enabled = !working) { Text("Remove") }
        }
    }

    when (state) {
        is LiveStreamEditorViewModel.WatermarkState.Working -> {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is LiveStreamEditorViewModel.WatermarkState.Result -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        LiveStreamEditorViewModel.WatermarkState.Idle -> Unit
    }
}

/**
 * A read-only field that opens a Material date picker, then a time picker, and writes the chosen
 * moment back as an ISO-8601 UTC string (e.g. `2026-06-07T18:00:00Z`) via [onIsoChange]. The
 * displayed text is the same moment rendered in [zone]. The wall-clock time the user picks is
 * interpreted in [zone] and converted to UTC for storage, matching how the Stream API and the
 * player treat scheduled times.
 *
 * @param zone the time zone the picked wall-clock time is interpreted in (and displayed in).
 * @param clearable when true, shows a clear (✕) action so an optional value (e.g. scheduled end)
 *                  can be removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerField(
    label: String,
    isoValue: String,
    onIsoChange: (String) -> Unit,
    zone: ZoneId,
    clearable: Boolean = false,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Date chosen in the date dialog, carried until the time is also chosen.
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val existing = remember(isoValue, zone) { isoToLocalDateTime(isoValue, zone) }

    // Detect taps on the field body (it's read-only, so it won't open a keyboard) and open the
    // date picker. Using an interaction source keeps the trailing icon independently clickable.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDatePicker = true
        }
    }

    OutlinedTextField(
        value = isoToDisplay(isoValue, zone),
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
                        onIsoChange(pickedToIso(dateMillis, timeState.hour, timeState.minute, zone))
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

/** The stored ISO instant rendered in [zone], or null if unparseable. */
private fun isoToLocalDateTime(iso: String, zone: ZoneId): LocalDateTime? =
    isoToInstant(iso)?.atZone(zone)?.toLocalDateTime()

/** Human-readable label for the field, in [zone] (empty when there's no value yet). */
private fun isoToDisplay(iso: String, zone: ZoneId): String =
    isoToLocalDateTime(iso, zone)?.format(DISPLAY_DATE_TIME) ?: ""

/**
 * Combines the date picker's selected day (UTC-midnight millis) with the picked wall-clock time,
 * interpreting the result in [zone], and returns it as an ISO-8601 UTC string.
 */
private fun pickedToIso(dateMillisUtc: Long, hour: Int, minute: Int, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(dateMillisUtc).atZone(ZoneOffset.UTC).toLocalDate()
    val local = LocalDateTime.of(date, LocalTime.of(hour, minute))
    return local.atZone(zone).toInstant().toString()
}

/** "Area/City (GMT±hh:mm)" label for a zone, using its current UTC offset. */
private fun zoneLabel(zone: ZoneId): String {
    val offsetId = zone.rules.getOffset(Instant.now()).id // "Z" for UTC, else "+05:00"
    val offsetText = if (offsetId == "Z") "GMT" else "GMT$offsetId"
    return "${zone.id}  ($offsetText)"
}

/**
 * Read-only field that opens a searchable dialog to pick any IANA time zone. Mirrors the
 * tap-to-open pattern used by [DateTimePickerField].
 */
@Composable
private fun TimeZonePickerField(
    zone: ZoneId,
    onZoneChange: (ZoneId) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDialog = true
        }
    }

    OutlinedTextField(
        value = zoneLabel(zone),
        onValueChange = {},
        readOnly = true,
        label = { Text("Time zone") },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        interactionSource = interactionSource,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showDialog) {
        TimeZonePickerDialog(
            current = zone,
            onDismiss = { showDialog = false },
            onSelect = {
                onZoneChange(it)
                showDialog = false
            },
        )
    }
}

/** Search box + scrollable list of every available IANA zone, sorted, with current UTC offset. */
@Composable
private fun TimeZonePickerDialog(
    current: ZoneId,
    onDismiss: () -> Unit,
    onSelect: (ZoneId) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val allZones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isBlank()) allZones else allZones.filter { it.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Select time zone") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered) { id ->
                        val z = ZoneId.of(id)
                        Text(
                            text = zoneLabel(z),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (z == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(z) }
                                .padding(vertical = 12.dp),
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "No matching time zones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

private val DISPLAY_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm")

private const val MAX_RTMP_OUTPUTS = 4

/** Editable draft of one RTMP output row (Stream URL + Stream Key). */
private data class RtmpOutputDraft(val url: String, val key: String)

/**
 * Persists the RTMP output drafts as a flat `[url, key, url, key, …]` string list so the list
 * survives configuration changes and sub-screen navigation via rememberSaveable.
 */
private val rtmpOutputsSaver = listSaver<SnapshotStateList<RtmpOutputDraft>, String>(
    save = { list -> list.flatMap { listOf(it.url, it.key) } },
    restore = { flat ->
        mutableStateListOf<RtmpOutputDraft>().apply {
            flat.chunked(2).forEach { add(RtmpOutputDraft(it.getOrElse(0) { "" }, it.getOrElse(1) { "" })) }
        }
    },
)

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
 * Generated-thumbnails control shown in edit mode: a horizontal gallery of the stream's server
 * thumbnails (tap to preview) plus a "Delete thumbnail" action — the list/preview/delete half of
 * the thumbnail feature (upload lives above).
 */
@Composable
private fun ThumbnailGallery(
    state: LiveStreamEditorViewModel.ThumbnailListState,
    onPreview: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Text(
        text = "Generated thumbnails",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))

    when (state) {
        is LiveStreamEditorViewModel.ThumbnailListState.Idle,
        is LiveStreamEditorViewModel.ThumbnailListState.Loading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp))
            }
        }

        is LiveStreamEditorViewModel.ThumbnailListState.Failed -> {
            Text(
                text = "Couldn't load thumbnails: ${state.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is LiveStreamEditorViewModel.ThumbnailListState.Loaded -> {
            val thumbs = state.items
            if (thumbs.isEmpty()) {
                Text(
                    text = "No thumbnails yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(thumbs) { thumb ->
                        AsyncImage(
                            model = thumb.url,
                            contentDescription = thumb.timestamp ?: "Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { thumb.url?.let(onPreview) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete thumbnail")
                }
            }
        }
    }
}

/** Full-width preview of a single thumbnail [url], dismissed with Close or a scrim tap. */
@Composable
private fun ThumbnailPreviewDialog(url: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            AsyncImage(
                model = url,
                contentDescription = "Thumbnail preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            )
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
