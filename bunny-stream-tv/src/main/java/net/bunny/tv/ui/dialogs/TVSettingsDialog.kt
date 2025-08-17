// bunny-stream-tv/src/main/java/net/bunny/tv/ui/dialogs/TVSettingsDialog.kt
package net.bunny.tv.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.bunny.bunnystreamplayer.common.BunnyPlayer
import net.bunny.tv.R

class TVSettingsDialog(
    context: Context,
    private val bunnyPlayer: BunnyPlayer
) : Dialog(context, R.style.TVDialogTheme) {

    private lateinit var settingsRecyclerView: RecyclerView
    private lateinit var settingsAdapter: TVSettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_tv_settings)

        setupViews()
        setupSettings()
    }

    private fun setupViews() {
        settingsRecyclerView = findViewById(R.id.tv_settings_recycler)
        settingsRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupSettings() {
        val settingsItems = mutableListOf<TVSettingsItem>()

        // Playback Speed
        val currentSpeed = bunnyPlayer.getSpeed()
        val speeds = bunnyPlayer.getPlaybackSpeeds()
        settingsItems.add(
            TVSettingsItem.SpeedSetting(
                title = "Playback Speed",
                currentValue = "${currentSpeed}x",
                options = speeds,
                onSpeedSelected = { speed ->
                    bunnyPlayer.setSpeed(speed)
                    dismiss()
                }
            )
        )

        // Video Quality
        val qualityOptions = bunnyPlayer.getVideoQualityOptions()
        if (qualityOptions != null && qualityOptions.options.isNotEmpty()) {
            settingsItems.add(
                TVSettingsItem.QualitySetting(
                    title = "Video Quality",
                    currentValue = formatQuality(qualityOptions.selectedOption),
                    options = qualityOptions,
                    onQualitySelected = { quality ->
                        bunnyPlayer.selectQuality(quality)
                        dismiss()
                    }
                )
            )
        }

        // Subtitles
        val subtitles = bunnyPlayer.getSubtitles()
        if (subtitles.subtitles.isNotEmpty()) {
            settingsItems.add(
                TVSettingsItem.SubtitleSetting(
                    title = "Subtitles",
                    currentValue = subtitles.selectedSubtitle?.title ?: "Off",
                    subtitles = subtitles,
                    onSubtitleSelected = { subtitle ->
                        bunnyPlayer.selectSubtitle(subtitle)
                        dismiss()
                    }
                )
            )
        }

        settingsAdapter = TVSettingsAdapter(settingsItems)
        settingsRecyclerView.adapter = settingsAdapter
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
}

// Adapter for settings
class TVSettingsAdapter(
    private val items: List<TVSettingsItem>
) : RecyclerView.Adapter<TVSettingsAdapter.ViewHolder>() {

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

        holder.itemView.setOnClickListener {
            when (item) {
                is TVSettingsItem.SpeedSetting -> {
                    // Show speed selection dialog
                    showSpeedSelection(holder.itemView.context, item)
                }
                is TVSettingsItem.QualitySetting -> {
                    // Show quality selection dialog
                    showQualitySelection(holder.itemView.context, item)
                }
                is TVSettingsItem.SubtitleSetting -> {
                    // Show subtitle selection dialog
                    showSubtitleSelection(holder.itemView.context, item)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    private fun showSpeedSelection(context: Context, item: TVSettingsItem.SpeedSetting) {
        // Implementation for speed selection
    }

    private fun showQualitySelection(context: Context, item: TVSettingsItem.QualitySetting) {
        // Implementation for quality selection
    }

    private fun showSubtitleSelection(context: Context, item: TVSettingsItem.SubtitleSetting) {
        // Implementation for subtitle selection
    }
}
