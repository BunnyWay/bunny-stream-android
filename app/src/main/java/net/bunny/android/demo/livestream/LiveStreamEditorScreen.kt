package net.bunny.android.demo.livestream

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import net.bunny.android.demo.R
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
                    title = { Text(stringResource(R.string.live_screen_created_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = stringResource(R.string.button_done),
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

            SectionCard(title = stringResource(R.string.live_section_links)) {
                CopyableRow(stringResource(R.string.live_label_publish_url), publishUrl)
                CopyableRow(stringResource(R.string.live_label_hls_playlist_url), stream.playbackUrlHls)
                CopyableRow(stringResource(R.string.live_label_video_id), stream.id)
                CopyableRow(stringResource(R.string.live_label_trailer_video_id), stream.preStreamTrailerVideoId)
                CopyableRow(stringResource(R.string.live_label_thumbnail_file), stream.thumbnailFileName)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // A live stream can only be broadcast once: the "Go live" action is only meaningful
            // while it's still waiting to be used (created/scheduled, or an encoder is connected in
            // PREVIEW). Once it has gone RUNNING — or has ENDED / is processing VOD — going live
            // again is no longer possible, so hide the button instead of letting it fail.
            if (canGoLive(stream.status)) {
                Button(onClick = onGoLive, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.live_button_go_live_now))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.live_button_watch_stream))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.button_done))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Mirror of the dashboard's ingest details card: stream key, primary/backup ingest URL
 * (copyable) and the current stream status.
 */
@Composable
private fun RtmpStreamDetailsCard(stream: LiveStream) {
    val fallback = net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT

    SectionCard(title = stringResource(R.string.live_section_ingest_details)) {
        CopyableRow(stringResource(R.string.live_label_stream_key), stream.streamKey)
        CopyableRow(
            stringResource(R.string.live_label_ingest_url),
            stream.primaryIngestUrl ?: fallback,
            badge = stringResource(R.string.live_badge_primary),
        )
        CopyableRow(
            stringResource(R.string.live_label_ingest_url),
            stream.backupIngestUrl ?: fallback,
            badge = stringResource(R.string.live_badge_backup),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.label_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(statusLabelRes(stream.status)),
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

@StringRes
private fun statusLabelRes(status: LiveStreamStatus): Int = when (status) {
    LiveStreamStatus.CREATED, LiveStreamStatus.SCHEDULED -> R.string.live_status_ready
    LiveStreamStatus.PREVIEW -> R.string.live_status_preview
    LiveStreamStatus.RUNNING -> R.string.live_status_live
    LiveStreamStatus.ENDED -> R.string.live_status_ended
    LiveStreamStatus.VOD_PROCESSING -> R.string.live_status_vod_processing
    LiveStreamStatus.ERROR -> R.string.live_status_error
    LiveStreamStatus.UNKNOWN -> R.string.live_status_unknown
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
                contentDescription = stringResource(R.string.cd_copy, label),
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
                    title = {
                        Text(
                            stringResource(
                                if (isEdit) R.string.live_screen_edit_title else R.string.live_screen_new_title,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = stringResource(R.string.cd_back),
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

            SectionCard(title = stringResource(R.string.live_section_details)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.live_label_title_required)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.live_label_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(stringResource(R.string.live_label_public), isPublic) { isPublic = it }
                SwitchRow(
                    label = stringResource(R.string.live_label_video_on_demand),
                    subtitle = stringResource(R.string.live_subtitle_video_on_demand),
                    checked = recordVod,
                ) { recordVod = it }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = stringResource(R.string.live_section_schedule)) {
                SwitchRow(
                    label = stringResource(R.string.live_label_schedule_start),
                    subtitle = stringResource(R.string.live_subtitle_schedule_start),
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
                        label = stringResource(R.string.live_label_scheduled_start),
                        isoValue = scheduledStart,
                        onIsoChange = { scheduledStart = it },
                        zone = scheduleZone,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTimePickerField(
                        label = stringResource(R.string.live_label_scheduled_end),
                        isoValue = scheduledEnd,
                        onIsoChange = { scheduledEnd = it },
                        zone = scheduleZone,
                        clearable = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        label = stringResource(R.string.live_label_enable_countdown),
                        subtitle = stringResource(R.string.live_subtitle_enable_countdown),
                        checked = enableCountdown,
                    ) { enableCountdown = it }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = stringResource(R.string.live_label_dvr)) {
                SwitchRow(
                    label = stringResource(R.string.live_label_dvr),
                    subtitle = stringResource(R.string.live_subtitle_dvr),
                    checked = dvrEnabled,
                ) { dvrEnabled = it }
                if (dvrEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dvrWindow,
                        onValueChange = { dvrWindow = it },
                        label = { Text(stringResource(R.string.live_label_dvr_timeframe)) },
                        placeholder = { Text(stringResource(R.string.live_placeholder_dvr_timeframe)) },
                        singleLine = true,
                        isError = dvrWindowError,
                        supportingText = {
                            if (dvrWindowError) Text(stringResource(R.string.live_error_dvr_timeframe))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = stringResource(R.string.live_section_trailer)) {
                SwitchRow(
                    label = stringResource(R.string.live_section_trailer),
                    subtitle = stringResource(R.string.live_subtitle_trailer),
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

            SectionCard(title = stringResource(R.string.live_section_thumbnail)) {
                SwitchRow(
                    label = stringResource(R.string.live_section_thumbnail),
                    subtitle = stringResource(R.string.live_subtitle_thumbnail),
                    checked = thumbnailEnabled,
                ) { thumbnailEnabled = it }

                if (thumbnailEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (thumbnailUri != null) {
                        AsyncImage(
                            model = thumbnailUri,
                            contentDescription = stringResource(R.string.live_cd_selected_thumbnail),
                            error = painterResource(R.drawable.thumbnail_placeholder),
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
                            Text(
                                stringResource(
                                    if (thumbnailUri == null) {
                                        R.string.live_button_choose_image
                                    } else {
                                        R.string.live_button_change_image
                                    },
                                ),
                            )
                        }
                        if (thumbnailUri != null) {
                            TextButton(onClick = { thumbnailUri = null }) {
                                Text(stringResource(R.string.button_remove))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onPickThumbnailFromLibrary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.live_button_choose_from_library))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = thumbnailUrl,
                        onValueChange = {
                            thumbnailUrl = it
                            if (it.isNotBlank()) thumbnailUri = null
                        },
                        label = { Text(stringResource(R.string.live_label_image_url)) },
                        placeholder = { Text(stringResource(R.string.live_placeholder_thumbnail_url)) },
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

            SectionCard(title = stringResource(R.string.live_section_live_outputs)) {
                Text(
                    text = stringResource(R.string.live_note_live_outputs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                rtmpOutputs.forEachIndexed { index, output ->
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = output.url,
                        onValueChange = { rtmpOutputs[index] = output.copy(url = it) },
                        label = { Text(stringResource(R.string.live_label_stream_url)) },
                        placeholder = { Text(stringResource(R.string.live_placeholder_rtmp_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = output.key,
                        onValueChange = { rtmpOutputs[index] = output.copy(key = it) },
                        label = { Text(stringResource(R.string.live_label_rtmp_stream_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { rtmpOutputs.removeAt(index) }) {
                            Text(stringResource(R.string.button_remove))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (rtmpOutputs.size < MAX_LIVE_OUTPUTS) {
                    OutlinedButton(
                        onClick = { rtmpOutputs.add(RtmpOutputDraft("", "")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.live_button_add_live_output))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.live_note_max_live_outputs, MAX_LIVE_OUTPUTS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = stringResource(R.string.live_section_dual_publish)) {
                SwitchRow(
                    label = stringResource(R.string.live_label_dual_publish),
                    checked = dualPublish,
                    subtitle = stringResource(R.string.live_subtitle_dual_publish),
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
                    stringResource(
                        when {
                            saving -> R.string.live_button_saving
                            isEdit -> R.string.live_button_save_changes
                            else -> R.string.live_button_create_stream
                        },
                    ),
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
                    text = stringResource(R.string.live_error_trailer_upload, trailer.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(onClick = onPickUpload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.live_button_upload_trailer))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onPickFromLibrary, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.live_button_choose_from_library))
            }
        }

        is LiveStreamEditorViewModel.TrailerState.Uploading -> {
            Text(
                text = stringResource(R.string.live_label_uploading, trailer.percentage),
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
                libraryId = App.di.libraryId,
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
                    Text(stringResource(R.string.live_button_replace))
                }
                if (trailer.deletable) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.dialog_button_delete)) }
                } else {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.button_remove)) }
                }
            }
        }
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
        placeholder = { Text(stringResource(R.string.live_placeholder_pick_datetime)) },
        trailingIcon = {
            if (clearable && isoValue.isNotBlank()) {
                IconButton(onClick = { onIsoChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear, label))
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
                ) { Text(stringResource(R.string.button_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.button_cancel)) }
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
                }) { Text(stringResource(R.string.dialog_button_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.button_cancel)) }
            },
            title = { Text(stringResource(R.string.dialog_title_select_time)) },
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
        label = { Text(stringResource(R.string.live_label_time_zone)) },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        },
        title = { Text(stringResource(R.string.dialog_title_select_time_zone)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.label_search)) },
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
                                text = stringResource(R.string.live_label_no_matching_zones),
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

private const val MAX_LIVE_OUTPUTS = 4

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
        title = { Text(stringResource(R.string.dialog_title_error)) },
        text = { Text(message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_ok)) }
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
        text = stringResource(R.string.live_label_generated_thumbnails),
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
                text = stringResource(R.string.live_error_load_thumbnails, state.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is LiveStreamEditorViewModel.ThumbnailListState.Loaded -> {
            val thumbs = state.items
            if (thumbs.isEmpty()) {
                Text(
                    text = stringResource(R.string.live_label_no_thumbnails),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(thumbs) { thumb ->
                        AsyncImage(
                            model = thumb.url,
                            contentDescription = thumb.timestamp
                                ?: stringResource(R.string.live_section_thumbnail),
                            error = painterResource(R.drawable.thumbnail_placeholder),
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
                    Text(stringResource(R.string.live_button_delete_thumbnail))
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_close)) }
        },
        text = {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.live_cd_thumbnail_preview),
                error = painterResource(R.drawable.thumbnail_placeholder),
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
