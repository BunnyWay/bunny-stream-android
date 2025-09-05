package net.bunny.tv.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.bunny.bunnystreamplayer.common.BunnyPlayer
import net.bunny.tv.R

class TVSettingsDialog(
    context: Context,
    private val bunnyPlayer: BunnyPlayer
) : Dialog(context, R.style.TVDialogTheme) {

    companion object {
        private const val TAG = "TVSettingsDialog"
    }

    private lateinit var settingsRecyclerView: RecyclerView
    private lateinit var closeButton: Button
    private lateinit var settingsAdapter: TVSettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Creating TV Settings Dialog")

        try {
            setContentView(R.layout.dialog_tv_settings)
            setupViews()
            setupSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating settings dialog", e)
        }
    }

    private fun setupViews() {
        Log.d(TAG, "Setting up views")

        settingsRecyclerView = findViewById(R.id.tv_settings_recycler)
        closeButton = findViewById(R.id.tv_settings_close)

        settingsRecyclerView.layoutManager = LinearLayoutManager(context)

        closeButton.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            dismiss()
        }

        // Make the close button focusable for TV navigation
        closeButton.isFocusable = true
        closeButton.requestFocus()
    }

    private fun setupSettings() {
        Log.d(TAG, "Setting up settings items")

        val settingsItems = mutableListOf<TVSettingsItem>()

        try {
            // Playback Speed - Always add this as it's a core feature
            val currentSpeed = getCurrentSpeed()
            val availableSpeeds = getAvailableSpeeds()

            Log.d(TAG, "Current speed: ${currentSpeed}x, Available speeds: $availableSpeeds")

            settingsItems.add(
                TVSettingsItem.SpeedSetting(
                    title = "Playback Speed",
                    currentValue = "${currentSpeed}x",
                    options = availableSpeeds,
                    onSpeedSelected = { speed ->
                        Log.d(TAG, "Speed selected: ${speed}x")
                        try {
                            bunnyPlayer.setSpeed(speed)
                            dismiss()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting speed", e)
                        }
                    }
                )
            )

            // Video Quality
            try {
                val qualityOptions = bunnyPlayer.getVideoQualityOptions()
                if (qualityOptions != null && qualityOptions.options.isNotEmpty()) {
                    Log.d(TAG, "Adding quality settings with ${qualityOptions.options.size} options")
                    settingsItems.add(
                        TVSettingsItem.QualitySetting(
                            title = "Video Quality",
                            currentValue = formatQuality(qualityOptions.selectedOption),
                            options = qualityOptions,
                            onQualitySelected = { quality ->
                                Log.d(TAG, "Quality selected: ${formatQuality(quality)}")
                                try {
                                    bunnyPlayer.selectQuality(quality)
                                    dismiss()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error setting quality", e)
                                }
                            }
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get video quality options", e)
            }

            // Subtitles
            try {
                val subtitles = bunnyPlayer.getSubtitles()
                if (subtitles.subtitles.isNotEmpty()) {
                    Log.d(TAG, "Adding subtitle settings with ${subtitles.subtitles.size} options")
                    settingsItems.add(
                        TVSettingsItem.SubtitleSetting(
                            title = "Subtitles",
                            currentValue = subtitles.selectedSubtitle?.title ?: "Off",
                            subtitles = subtitles,
                            onSubtitleSelected = { subtitle ->
                                Log.d(TAG, "Subtitle selected: ${subtitle.title}")
                                try {
                                    bunnyPlayer.selectSubtitle(subtitle)
                                    dismiss()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error setting subtitle", e)
                                }
                            }
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get subtitle options", e)
            }

            if (settingsItems.isEmpty()) {
                Log.w(TAG, "No settings items available")
                // Add a placeholder item
                settingsItems.add(
                    TVSettingsItem.InfoSetting(
                        title = "Settings",
                        currentValue = "No options available"
                    )
                )
            }

            Log.d(TAG, "Created ${settingsItems.size} settings items")
            settingsAdapter = TVSettingsAdapter(settingsItems)
            settingsRecyclerView.adapter = settingsAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up settings", e)
            // Create a minimal adapter with error info
            val errorItem = TVSettingsItem.InfoSetting(
                title = "Settings Error",
                currentValue = "Unable to load settings"
            )
            settingsAdapter = TVSettingsAdapter(listOf(errorItem))
            settingsRecyclerView.adapter = settingsAdapter
        }
    }

    private fun getCurrentSpeed(): Float {
        return try {
            bunnyPlayer.getSpeed()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get current speed", e)
            1.0f
        }
    }

    private fun getAvailableSpeeds(): List<Float> {
        return try {
            val speeds = bunnyPlayer.getPlaybackSpeeds()
            if (speeds.isNotEmpty()) {
                speeds
            } else {
                // Default speeds if none available
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get available speeds", e)
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        }
    }

    private fun formatQuality(quality: net.bunny.bunnystreamplayer.model.VideoQuality?): String {
        return if (quality?.width == Int.MAX_VALUE) {
            "Auto"
        } else {
            "${quality?.width}x${quality?.height}"
        }
    }
}

// Settings item classes
sealed class TVSettingsItem {
    abstract val title: String
    abstract val currentValue: String

    data class SpeedSetting(
        override val title: String,
        override val currentValue: String,
        val options: List<Float>,
        val onSpeedSelected: (Float) -> Unit
    ) : TVSettingsItem()

    data class QualitySetting(
        override val title: String,
        override val currentValue: String,
        val options: net.bunny.bunnystreamplayer.model.VideoQualityOptions,
        val onQualitySelected: (net.bunny.bunnystreamplayer.model.VideoQuality) -> Unit
    ) : TVSettingsItem()

    data class SubtitleSetting(
        override val title: String,
        override val currentValue: String,
        val subtitles: net.bunny.bunnystreamplayer.model.Subtitles,
        val onSubtitleSelected: (net.bunny.bunnystreamplayer.model.SubtitleInfo) -> Unit
    ) : TVSettingsItem()

    data class InfoSetting(
        override val title: String,
        override val currentValue: String
    ) : TVSettingsItem()
}

// Adapter for settings
class TVSettingsAdapter(
    private val items: List<TVSettingsItem>
) : RecyclerView.Adapter<TVSettingsAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "TVSettingsAdapter"
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.setting_title)
        val valueText: TextView = view.findViewById(R.id.setting_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tv_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleText.text = item.title
        holder.valueText.text = item.currentValue

        // Make item focusable for TV navigation
        holder.itemView.isFocusable = true

        holder.itemView.setOnClickListener {
            Log.d(TAG, "Setting item clicked: ${item.title}")
            when (item) {
                is TVSettingsItem.SpeedSetting -> {
                    showSpeedSelection(holder.itemView.context, item)
                }
                is TVSettingsItem.QualitySetting -> {
                    showQualitySelection(holder.itemView.context, item)
                }
                is TVSettingsItem.SubtitleSetting -> {
                    showSubtitleSelection(holder.itemView.context, item)
                }
                is TVSettingsItem.InfoSetting -> {
                    // Do nothing for info items
                }
            }
        }

        // Focus handling for TV
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.isSelected = hasFocus
            // Remove the animate() calls - just use simple background change
        }

        // Request focus for the first item
        if (position == 0) {
            holder.itemView.requestFocus()
        }
    }

    override fun getItemCount() = items.size

    private fun showSpeedSelection(context: Context, item: TVSettingsItem.SpeedSetting) {
        Log.d(TAG, "Showing speed selection dialog")

        val speedLabels = item.options.map { "${it}x" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Select Playback Speed")
            .setItems(speedLabels) { dialog, which ->
                val selectedSpeed = item.options[which]
                Log.d(TAG, "Speed selected: ${selectedSpeed}x")
                item.onSpeedSelected(selectedSpeed)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showQualitySelection(context: Context, item: TVSettingsItem.QualitySetting) {
        Log.d(TAG, "Showing quality selection dialog")

        val qualityLabels = item.options.options.map { quality ->
            if (quality.width == Int.MAX_VALUE) {
                "Auto"
            } else {
                "${quality.width}x${quality.height}"
            }
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Select Video Quality")
            .setItems(qualityLabels) { dialog, which ->
                val selectedQuality = item.options.options[which]
                Log.d(TAG, "Quality selected: ${qualityLabels[which]}")
                item.onQualitySelected(selectedQuality)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showSubtitleSelection(context: Context, item: TVSettingsItem.SubtitleSetting) {
        Log.d(TAG, "Showing subtitle selection dialog")

        val subtitleLabels = mutableListOf("Off")
        subtitleLabels.addAll(item.subtitles.subtitles.map { it.title })

        AlertDialog.Builder(context)
            .setTitle("Select Subtitles")
            .setItems(subtitleLabels.toTypedArray()) { dialog, which ->
                if (which == 0) {
                    // "Off" selected - disable subtitles
                    Log.d(TAG, "Subtitles turned off")
                    // You might need to call a different method to disable subtitles
                    // bunnyPlayer.disableSubtitles() or similar
                } else {
                    val selectedSubtitle = item.subtitles.subtitles[which - 1]
                    Log.d(TAG, "Subtitle selected: ${selectedSubtitle.title}")
                    item.onSubtitleSelected(selectedSubtitle)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}