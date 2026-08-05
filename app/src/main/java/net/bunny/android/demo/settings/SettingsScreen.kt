package net.bunny.android.demo.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.bunny.android.demo.R
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme

@Composable
fun SettingsRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    var accessKey by remember { mutableStateOf(viewModel.accessKey) }
    var libraryId by remember {
        mutableStateOf(
            if (viewModel.libraryId == -1L) ""
            else viewModel.libraryId.toString()
        )
    }

    val state = viewModel.state

    // Only leave once the credentials have actually been proven to work.
    LaunchedEffect(state) {
        if (state == SettingsState.Verified) appState.navController.popBackStack()
    }

    SettingsScreen(
        modifier = modifier,
        onBackClicked = { appState.navController.popBackStack() },
        accessKey = accessKey,
        onAccessKeyUpdated = {
            accessKey = it
            viewModel.dismissError()
        },
        libraryId = libraryId,
        onLibraryIdUpdated = {
            libraryId = it
            viewModel.dismissError()
        },
        onSaveClicked = { viewModel.saveAndVerify(accessKey, libraryId) },
        checking = state == SettingsState.Checking,
        errorMessage = (state as? SettingsState.Failed)?.message,
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
    checking: Boolean = false,
    errorMessage: String? = null,
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

            if (errorMessage != null) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = onSaveClicked,
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (checking) "Checking…" else stringResource(id = R.string.button_save_settings),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
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
