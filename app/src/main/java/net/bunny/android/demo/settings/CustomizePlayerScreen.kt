package net.bunny.android.demo.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.bunny.android.demo.App
import net.bunny.android.demo.ui.AppState
import net.bunny.bunnystreamplayer.livestream.LiveControls
import net.bunny.bunnystreamplayer.livestream.LivePlayerConfig

/**
 * Demo screen that edits the [LivePlayerConfig] applied to the live player. The config is persisted
 * in [LocalPrefs] and read back by the live-stream player route, so the choices here take effect the
 * next time a live stream is opened. Only the knobs that meaningfully affect the live player are
 * exposed (matching what the SDK actually wires up); changes auto-save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizePlayerRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    val localPrefs = App.di.localPrefs
    var config by remember { mutableStateOf(localPrefs.livePlayerConfig) }

    fun update(new: LivePlayerConfig) {
        config = new
        localPrefs.livePlayerConfig = new
    }

    fun updateControls(new: LiveControls) = update(config.copy(controls = new))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = { Text("Customize Player") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = "Applies to the live player (BunnyLiveStreamPlayer). Open a live stream to see " +
                    "the result.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Accent color") {
                Text(
                    "Tints the scrub bar plus the countdown text and LIVE dot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ColorSwatchRow(
                    selected = config.primaryColor,
                    onSelect = { update(config.copy(primaryColor = it)) },
                )
            }

            SectionCard(title = "Font") {
                Text(
                    "Google Font for the transport bar (current time / duration) and captions. Does " +
                        "not restyle the countdown / offline overlays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ChoiceChipRow(
                    options = FONT_OPTIONS,
                    selected = config.fontFamily,
                    onSelect = { update(config.copy(fontFamily = it)) },
                )
            }

            SectionCard(title = "UI language") {
                Text(
                    "Localises the settings menu and the countdown / offline copy. Auto uses the " +
                        "device locale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ChoiceChipRow(
                    options = LANGUAGE_OPTIONS,
                    selected = config.uiLanguage,
                    onSelect = { update(config.copy(uiLanguage = it)) },
                )
            }

            SectionCard(title = "Layout") {
                SwitchRow(
                    label = "Compact controls",
                    checked = config.compactControls,
                    onCheckedChange = { update(config.copy(compactControls = it)) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "When on, hides the secondary controls (settings, PiP, duration, captions, " +
                        "rewind / fast-forward) regardless of the Controls toggles below. Keeps play, " +
                        "scrub bar, current time, mute, fullscreen, and cast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Controls") {
                SwitchRow("Play / pause", config.controls.livePlayPause) {
                    updateControls(config.controls.copy(livePlayPause = it))
                }
                SwitchRow("DVR seek bar", config.controls.progress && config.controls.dvr) {
                    updateControls(config.controls.copy(progress = it, dvr = it))
                }
                SwitchRow("Time counter", config.controls.duration) {
                    updateControls(config.controls.copy(duration = it))
                }
                SwitchRow("Mute", config.controls.mute) {
                    updateControls(config.controls.copy(mute = it))
                }
                SwitchRow("Settings", config.controls.settings) {
                    updateControls(config.controls.copy(settings = it))
                }
                SwitchRow("Fullscreen", config.controls.fullScreen) {
                    updateControls(config.controls.copy(fullScreen = it))
                }
                SwitchRow("Picture in picture", config.controls.pip) {
                    updateControls(config.controls.copy(pip = it))
                }
                SwitchRow("Chromecast", config.controls.chromecast) {
                    updateControls(config.controls.copy(chromecast = it))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { update(LivePlayerConfig()) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("Reset to defaults")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val FONT_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Default",
    "Rubik" to "Rubik",
    "Roboto" to "Roboto",
    "Poppins" to "Poppins",
    "Lobster" to "Lobster",
)

private val LANGUAGE_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Auto",
    "en" to "English",
    "de" to "German",
    "fr" to "French",
    "es" to "Spanish",
    "pl" to "Polish",
)

/** `null` = SDK default (white scrub bar). ARGB ints otherwise. */
private val COLOR_SWATCHES: List<Pair<Int?, String>> = listOf(
    null to "Default",
    0xFFFF7755.toInt() to "Orange",
    0xFF4F8DFD.toInt() to "Blue",
    0xFF37B24D.toInt() to "Green",
    0xFFB197FC.toInt() to "Purple",
    0xFFE64980.toInt() to "Pink",
)

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColorSwatchRow(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        COLOR_SWATCHES.forEach { (argb, label) ->
            val isSelected = selected == argb
            val swatchColor = argb?.let { Color(it) } ?: Color.White
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(swatchColor, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (argb == null) Color.Black else Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChoiceChipRow(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                leadingIcon = if (selected == value) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
