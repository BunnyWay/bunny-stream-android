package net.bunny.android.demo.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunny.android.demo.R
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme
import net.bunny.api.livestream.domain.model.LibraryWatermarkSettings

@Composable
fun SettingsRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current

    // Load the current watermark placement so the size/position fields can prefill.
    LaunchedEffect(Unit) { viewModel.loadWatermarkSettings() }

    var accessKey by remember { mutableStateOf(viewModel.accessKey) }
    var accountApiKey by remember { mutableStateOf(viewModel.accountApiKey) }
    var libraryId by remember {
        mutableStateOf(
            if (viewModel.libraryId == -1L) ""
            else viewModel.libraryId.toString()
        )
    }

    // Picked watermark image, shown as a preview until the user confirms the upload.
    var pickedWatermarkUri by remember { mutableStateOf<Uri?>(null) }

    val pickWatermark = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pickedWatermarkUri = uri },
    )

    SettingsScreen(
        modifier = modifier,
        onBackClicked = { appState.navController.popBackStack() },
        accessKey = accessKey,
        onAccessKeyUpdated = { accessKey = it },
        accountApiKey = accountApiKey,
        onAccountApiKeyUpdated = { accountApiKey = it },
        libraryId = libraryId,
        onLibraryIdUpdated = { libraryId = it },
        onSaveClicked = {
            viewModel.updateKeys(accessKey, libraryId.toLongOrDefault(-1), accountApiKey)
            appState.navController.popBackStack()
        },
        watermarkState = viewModel.watermarkState,
        watermarkPickedUri = pickedWatermarkUri,
        watermarkCachedUri = viewModel.cachedWatermarkUri,
        watermarkPlacement = viewModel.watermarkPlacement,
        onSaveWatermarkPosition = viewModel::saveWatermarkSettings,
        onPickWatermark = {
            pickWatermark.launch(
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        },
        onUploadWatermark = {
            val uri = pickedWatermarkUri
            if (uri != null) {
                val resolver = context.contentResolver
                val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
                    .getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.uploadWatermark(bytes, resolver.getType(uri) ?: "image/png")
                }
            }
        },
        onRemoveWatermark = viewModel::removeWatermark,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit,
    accessKey: String,
    onAccessKeyUpdated: (String) -> Unit,
    libraryId: String,
    onLibraryIdUpdated: (String) -> Unit,
    onSaveClicked: () -> Unit,
    accountApiKey: String = "",
    onAccountApiKeyUpdated: (String) -> Unit = {},
    watermarkState: SettingsViewModel.WatermarkState = SettingsViewModel.WatermarkState.Idle,
    watermarkPickedUri: Uri? = null,
    watermarkCachedUri: Uri? = null,
    watermarkPlacement: LibraryWatermarkSettings? = null,
    onSaveWatermarkPosition: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onPickWatermark: () -> Unit = {},
    onUploadWatermark: () -> Unit = {},
    onRemoveWatermark: () -> Unit = {},
) {

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = {
                        Text(stringResource(id = R.string.screen_settings))
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClicked
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = null
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
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                value = libraryId,
                onValueChange = onLibraryIdUpdated,
                label = { Text(stringResource(id = R.string.hint_library_id)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                value = accessKey,
                onValueChange = onAccessKeyUpdated,
                singleLine = true,
                label = { Text(stringResource(id = R.string.hint_access_key)) }
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                value = accountApiKey,
                onValueChange = onAccountApiKeyUpdated,
                singleLine = true,
                label = { Text("Account API key (for watermark)") },
                supportingText = {
                    Text("From Account Settings → API. Needed only for the library watermark.")
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = onSaveClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = stringResource(id = R.string.button_save_settings), color = MaterialTheme.colorScheme.onPrimary)
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            WatermarkSection(
                modifier = Modifier.padding(16.dp),
                state = watermarkState,
                pickedUri = watermarkPickedUri,
                cachedUri = watermarkCachedUri,
                placement = watermarkPlacement,
                onSavePosition = onSaveWatermarkPosition,
                onPick = onPickWatermark,
                onUpload = onUploadWatermark,
                onRemove = onRemoveWatermark,
            )
        }
    }
}

/**
 * Library-level watermark control. Bunny's watermark is set per video library — once uploaded it is
 * applied to every live stream (and video) in the library — so it lives in settings rather than the
 * per-stream editor. Targets the currently saved library; save the library ID above first.
 */
@Composable
private fun WatermarkSection(
    modifier: Modifier = Modifier,
    state: SettingsViewModel.WatermarkState,
    pickedUri: Uri? = null,
    cachedUri: Uri? = null,
    placement: LibraryWatermarkSettings? = null,
    onSavePosition: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onPick: () -> Unit,
    onUpload: () -> Unit,
    onRemove: () -> Unit,
) {
    val working = state is SettingsViewModel.WatermarkState.Working
    // Show the freshly-picked image if any, otherwise the last one uploaded from this app.
    val previewUri = pickedUri ?: cachedUri

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Watermark",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Upload a PNG logo to apply a watermark across all live streams in this library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Size and position are configured in your library's encoder settings on the " +
                "bunny.net dashboard (same place as for VOD).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Preview: the freshly-picked image, or the last one uploaded from this app. Bunny exposes
        // no API to read the stored watermark, so a watermark set elsewhere (e.g. dashboard) can't
        // be shown here.
        if (previewUri != null) {
            AsyncImage(
                model = previewUri,
                contentDescription = "Watermark preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPick,
                enabled = !working,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (previewUri == null) "Choose PNG" else "Change")
            }
            Button(
                onClick = onUpload,
                enabled = pickedUri != null && !working,
                modifier = Modifier.weight(1f),
            ) {
                Text("Upload")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRemove,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Remove watermark")
        }

        Spacer(modifier = Modifier.height(16.dp))
        WatermarkPositionFields(
            placement = placement,
            enabled = !working,
            onSave = onSavePosition,
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (state) {
            is SettingsViewModel.WatermarkState.Working -> {
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

            is SettingsViewModel.WatermarkState.Result -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            SettingsViewModel.WatermarkState.Idle -> Unit
        }
    }
}

/**
 * Watermark position/size inputs (all in %), prefilled from the library's current [placement] and
 * saved via [onSave] (left, top, width, height). Maps to the library's encoder watermark settings.
 */
@Composable
private fun WatermarkPositionFields(
    placement: LibraryWatermarkSettings?,
    enabled: Boolean,
    onSave: (Int, Int, Int, Int) -> Unit,
) {
    // Seeded from the loaded placement; re-seeds when it changes (i.e. when the load completes).
    var left by remember(placement) { mutableStateOf(placement?.positionLeft?.toString() ?: "0") }
    var top by remember(placement) { mutableStateOf(placement?.positionTop?.toString() ?: "0") }
    var width by remember(placement) { mutableStateOf(placement?.width?.toString() ?: "0") }
    var height by remember(placement) { mutableStateOf(placement?.height?.toString() ?: "0") }

    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    fun sanitize(value: String) = value.filter { it.isDigit() }.take(3)

    Text(
        text = "Position & size (% of the video frame)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = left,
            onValueChange = { left = sanitize(it) },
            label = { Text("Left %") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = numberKeyboard,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = top,
            onValueChange = { top = sanitize(it) },
            label = { Text("Top %") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = numberKeyboard,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = width,
            onValueChange = { width = sanitize(it) },
            label = { Text("Width %") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = numberKeyboard,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = height,
            onValueChange = { height = sanitize(it) },
            label = { Text("Height %") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = numberKeyboard,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            onSave(
                left.toIntOrNull() ?: 0,
                top.toIntOrNull() ?: 0,
                width.toIntOrNull() ?: 0,
                height.toIntOrNull() ?: 0,
            )
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save size & position")
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    BunnyStreamTheme {
        SettingsScreen(
            onBackClicked = {},
            accessKey = "",
            onAccessKeyUpdated = {},
            libraryId = "",
            onLibraryIdUpdated = {},
            onSaveClicked = {},
        )
    }
}
