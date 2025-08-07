
package net.bunnystream.android.demo.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme

@Composable
fun ResumePositionSettingsRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    // In a real app, these would come from a ViewModel or SharedPreferences
    var resumeEnabled by remember { mutableStateOf(true) }
    var retentionDays by remember { mutableStateOf("7") }
    var minimumWatchTime by remember { mutableStateOf("30") }
    var resumeThreshold by remember { mutableStateOf("5") }
    var nearEndThreshold by remember { mutableStateOf("95") }

    ResumePositionSettingsScreen(
        modifier = modifier,
        onBackClicked = { appState.navController.popBackStack() },
        resumeEnabled = resumeEnabled,
        onResumeEnabledChanged = { resumeEnabled = it },
        retentionDays = retentionDays,
        onRetentionDaysChanged = { retentionDays = it },
        minimumWatchTime = minimumWatchTime,
        onMinimumWatchTimeChanged = { minimumWatchTime = it },
        resumeThreshold = resumeThreshold,
        onResumeThresholdChanged = { resumeThreshold = it },
        nearEndThreshold = nearEndThreshold,
        onNearEndThresholdChanged = { nearEndThreshold = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumePositionSettingsScreen(
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit,
    resumeEnabled: Boolean,
    onResumeEnabledChanged: (Boolean) -> Unit,
    retentionDays: String,
    onRetentionDaysChanged: (String) -> Unit,
    minimumWatchTime: String,
    onMinimumWatchTimeChanged: (String) -> Unit,
    resumeThreshold: String,
    onResumeThresholdChanged: (String) -> Unit,
    nearEndThreshold: String,
    onNearEndThresholdChanged: (String) -> Unit,
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
                        Text("Resume Position Settings")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
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
                .padding(16.dp)
        ) {
            // Enable/Disable Resume Position
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Resume Position",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = resumeEnabled,
                            onCheckedChange = onResumeEnabledChanged
                        )
                    }
                    Text(
                        text = "Automatically save and resume video playback from where you left off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (resumeEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Resume Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Resume Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = retentionDays,
                            onValueChange = onRetentionDaysChanged,
                            label = { Text("Retention Days") },
                            supportingText = { Text("How many days to keep resume positions") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = minimumWatchTime,
                            onValueChange = onMinimumWatchTimeChanged,
                            label = { Text("Minimum Watch Time (seconds)") },
                            supportingText = { Text("Minimum time to watch before saving position") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = resumeThreshold,
                            onValueChange = onResumeThresholdChanged,
                            label = { Text("Resume Threshold (%)") },
                            supportingText = { Text("Don't resume if less than this percentage watched") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = nearEndThreshold,
                            onValueChange = onNearEndThresholdChanged,
                            label = { Text("Near End Threshold (%)") },
                            supportingText = { Text("Don't resume if more than this percentage watched") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ResumePositionSettingsScreenPreview() {
    BunnyStreamTheme {
        ResumePositionSettingsScreen(
            onBackClicked = {},
            resumeEnabled = true,
            onResumeEnabledChanged = {},
            retentionDays = "7",
            onRetentionDaysChanged = {},
            minimumWatchTime = "30",
            onMinimumWatchTimeChanged = {},
            resumeThreshold = "5",
            onResumeThresholdChanged = {},
            nearEndThreshold = "95",
            onNearEndThresholdChanged = {}
        )
    }
}
