// bunny-stream-tv/src/main/java/net/bunny/tv/ui/controls/TVPlayerControlsView.kt
package net.bunny.tv.ui.controls

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import net.bunny.bunnystreamplayer.common.BunnyPlayer
import net.bunny.tv.R

class TVPlayerControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    // Views
    private val videoTitle: TextView
    private val playPauseButton: ImageButton
    private val seekBackButton: ImageButton
    private val seekForwardButton: ImageButton
    private val settingsButton: ImageButton
    private val progressBar: ProgressBar
    private val timeDisplay: TextView
    private val loadingIndicator: ProgressBar
    private val loadingText: TextView
    private val controlsContainer: View

    // State
    private var bunnyPlayer: BunnyPlayer? = null
    private var isVisible = false
    private var hideRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    // Settings
    private var onSettingsClickListener: (() -> Unit)? = null

    companion object {
        private const val HIDE_DELAY_MS = 5000L
        private const val SEEK_INCREMENT_MS = 10000L // 30 seconds for TV
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.tv_player_controls, this, true)

        // Find views
        videoTitle = findViewById(R.id.tv_video_title)
        playPauseButton = findViewById(R.id.tv_play_pause)
        seekBackButton = findViewById(R.id.tv_seek_back)
        seekForwardButton = findViewById(R.id.tv_seek_forward)
        settingsButton = findViewById(R.id.tv_settings)
        progressBar = findViewById(R.id.tv_progress)
        timeDisplay = findViewById(R.id.tv_time_display)
        loadingIndicator = findViewById(R.id.tv_loading)
        loadingText = findViewById(R.id.tv_loading_text)
        controlsContainer = findViewById(R.id.tv_controls_container)

        setupControls()
        setupFocusHandling()

        // Start hidden
        visibility = View.GONE
    }

    private fun setupControls() {
        playPauseButton.setOnClickListener {
            bunnyPlayer?.let { player ->
                if (player.isPlaying()) {
                    player.pause()
                    playPauseButton.setImageResource(R.drawable.ic_play_48dp)
                } else {
                    player.play()
                    playPauseButton.setImageResource(R.drawable.ic_pause_48dp)
                }
            }
            resetHideTimer()
        }

        seekBackButton.setOnClickListener {
            bunnyPlayer?.let { player ->
                val newPosition = (player.getCurrentPosition() - SEEK_INCREMENT_MS).coerceAtLeast(0)
                player.seekTo(newPosition)
                updateProgress()
            }
            resetHideTimer()
        }

        seekForwardButton.setOnClickListener {

            bunnyPlayer?.let { player ->
                val duration = player.getDuration()
                val newPosition = (player.getCurrentPosition() + SEEK_INCREMENT_MS).coerceAtMost(duration)
                player.seekTo(newPosition)
                updateProgress()
            }
            resetHideTimer()
        }

        settingsButton.setOnClickListener {
            onSettingsClickListener?.invoke()
            resetHideTimer()
        }
    }

    private fun setupFocusHandling() {
        // Make controls focusable for D-pad navigation
        playPauseButton.isFocusable = true
        seekBackButton.isFocusable = true
        seekForwardButton.isFocusable = true
        settingsButton.isFocusable = true

        // Handle focus changes with simple background change - NO ANIMATIONS
        val focusChangeListener = OnFocusChangeListener { view, hasFocus ->
            view.isSelected = hasFocus
            if (hasFocus) {
                resetHideTimer()
            }
        }

        playPauseButton.onFocusChangeListener = focusChangeListener
        seekBackButton.onFocusChangeListener = focusChangeListener
        seekForwardButton.onFocusChangeListener = focusChangeListener
        settingsButton.onFocusChangeListener = focusChangeListener
    }

    fun setBunnyPlayer(player: BunnyPlayer) {
        this.bunnyPlayer = player
        updatePlayPauseButton()
    }

    fun setVideoTitle(title: String) {
        videoTitle.text = title
    }

    fun setOnSettingsClickListener(listener: () -> Unit) {
        onSettingsClickListener = listener
    }

    fun show() {
        if (!isVisible) {
            isVisible = true
            visibility = View.VISIBLE
            alpha = 1f // Set directly, no animation

            // Focus the play/pause button by default
            playPauseButton.requestFocus()
        }
        resetHideTimer()
    }

    fun hide() {
        if (isVisible) {
            isVisible = false
            visibility = View.GONE
            alpha = 0f // Set directly, no animation
        }
        hideRunnable?.let { handler.removeCallbacks(it) }
    }

    fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        controlsContainer.visibility = View.GONE
        progressBar.visibility = View.GONE
        timeDisplay.visibility = View.GONE
    }

    fun hideLoading() {
        loadingIndicator.visibility = View.GONE
        loadingText.visibility = View.GONE
        controlsContainer.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        timeDisplay.visibility = View.VISIBLE
    }

    private fun resetHideTimer() {
        try {
            hideRunnable?.let { handler.removeCallbacks(it) }
            hideRunnable = Runnable {
                try {
                    hide()
                } catch (e: Exception) {
                    Log.e("TVControls", "Error hiding controls", e)
                }
            }
            handler.postDelayed(hideRunnable!!, HIDE_DELAY_MS)
        } catch (e: Exception) {
            Log.e("TVControls", "Error resetting hide timer", e)
        }
    }
    private fun updatePlayPauseButton() {
        bunnyPlayer?.let { player ->
            val iconRes = if (player.isPlaying()) {
                R.drawable.ic_pause_48dp

            } else {
                R.drawable.ic_play_48dp
            }
            playPauseButton.setImageResource(iconRes)
        }
    }

    fun updateProgress() {
        bunnyPlayer?.let { player ->
            val position = player.getCurrentPosition()
            val duration = player.getDuration()

            if (duration > 0) {
                val progress = (position * 100 / duration).toInt()
                progressBar.progress = progress

                timeDisplay.text = "${formatTime(position)} / ${formatTime(duration)}"
            }
        }
        updatePlayPauseButton()
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun isControlsVisible(): Boolean = isVisible

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hideRunnable?.let { handler.removeCallbacks(it) }
    }
}
