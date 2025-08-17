// bunny-stream-tv/src/main/java/net/bunny/tv/ui/BunnyTVPlayerActivity.kt
package net.bunny.tv.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bunny.api.BunnyStreamApi
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.ResumeConfig
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.bunnystreamplayer.DefaultBunnyPlayer
import net.bunny.bunnystreamplayer.common.BunnyPlayer
import net.bunny.tv.R
import net.bunny.tv.navigation.TVKeyEventHandler
import net.bunny.tv.ui.controls.TVPlayerControlsView
import net.bunny.tv.ui.dialogs.TVSettingsDialog
import org.openapitools.client.models.VideoModel
import org.openapitools.client.models.VideoPlayDataModelVideo

class BunnyTVPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var tvControls: TVPlayerControlsView
    private lateinit var keyEventHandler: TVKeyEventHandler
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button

    private var bunnyPlayer: BunnyPlayer? = null
    private var videoId: String? = null
    private var libraryId: Long? = null
    private var currentVideo: VideoModel? = null

    companion object {
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_LIBRARY_ID = "library_id"
        private const val EXTRA_VIDEO_TITLE = "video_title"

        fun start(context: Context, videoId: String, libraryId: Long, videoTitle: String? = null) {
            val intent = Intent(context, BunnyTVPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_LIBRARY_ID, libraryId)
                putExtra(EXTRA_VIDEO_TITLE, videoTitle)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_player)

        // Make full screen and keep screen on
        setupFullScreenMode()

        // Get intent data
        videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        libraryId = intent.getLongExtra(EXTRA_LIBRARY_ID, -1L)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)

        setupViews()
        setupPlayer()
        setupKeyHandling()
        setupErrorHandling()

        // Set video title if provided
        videoTitle?.let { tvControls.setVideoTitle(it) }

        // Load video
        videoId?.let { id ->
            libraryId?.let { libId ->
                if (libId != -1L) {
                    loadVideo(id, libId)
                } else {
                    showError("Invalid library ID", "Library ID is missing or invalid")
                }
            }
        } ?: run {
            showError("Invalid video parameters", "Video ID is missing")
        }
    }

    private fun setupFullScreenMode() {
        // Hide system UI for immersive experience
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupViews() {
        playerView = findViewById(R.id.tv_player_view)
        tvControls = findViewById(R.id.tv_controls)
        errorContainer = findViewById(R.id.tv_error_container)
        errorMessage = findViewById(R.id.tv_error_message)
        retryButton = findViewById(R.id.tv_error_retry)

        // Configure player view for TV
        playerView.useController = false // We'll use our custom controls
        playerView.setShutterBackgroundColor(Color.BLACK)
    }

    private fun setupPlayer() {
        bunnyPlayer = DefaultBunnyPlayer.getInstance(this)
        tvControls.setBunnyPlayer(bunnyPlayer!!)

        // Setup settings click listener
        tvControls.setOnSettingsClickListener {
            showSettingsDialog()
        }
    }

    private fun setupKeyHandling() {
        keyEventHandler = TVKeyEventHandler { keyEvent ->
            handleTVKeyEvent(keyEvent)
        }
    }

    private fun setupErrorHandling() {
        retryButton.setOnClickListener {
            hideError()
            videoId?.let { id ->
                libraryId?.let { libId ->
                    loadVideo(id, libId)
                }
            }
        }
    }

    private fun loadVideo(videoId: String, libraryId: Long) {
        tvControls.showLoading()
        hideError()

        lifecycleScope.launch {
            try {
                // Load video data
                val video = withContext(Dispatchers.IO) {
                    BunnyStreamApi.getInstance().videosApi.videoGetVideoPlayData(
                        libraryId, videoId
                    ).video?.toVideoModel()
                }

                if (video == null) {
                    showError("Video Not Found", "The requested video could not be found.")
                    return@launch
                }

                currentVideo = video

                // Load player settings
                val settingsResult = withContext(Dispatchers.IO) {
                    BunnyStreamApi.getInstance().fetchPlayerSettings(libraryId, videoId)
                }

                settingsResult.fold(
                    ifLeft = { error ->
                        // Use default settings if fetch fails
                        val defaultSettings = createDefaultPlayerSettings()
                        initializeVideo(video, defaultSettings)
                    },
                    ifRight = { playerSettings ->
                        initializeVideo(video, playerSettings)
                    }
                )

            } catch (e: Exception) {
                showError("Error Loading Video", e.message ?: "An unknown error occurred")
            }
        }
    }

    private fun initializeVideo(video: VideoModel, playerSettings: PlayerSettings) {
        tvControls.hideLoading()

        // Set video title
        video.title?.let { tvControls.setVideoTitle(it) }

        // Setup resume position functionality
        bunnyPlayer?.enableResumePosition(
            config = ResumeConfig(),
            onResumePositionCallback = { position, callback ->
                showResumeDialog(position, callback)
            }
        )

        // Initialize player with video
        try {
            bunnyPlayer?.playVideo(playerView, video, emptyMap(), playerSettings)

            // Start progress updates
            startProgressUpdates()

        } catch (e: Exception) {
            showError("Playback Error", e.message ?: "Failed to start video playback")
        }
    }

    private fun createDefaultPlayerSettings(): PlayerSettings {
        return PlayerSettings(
            thumbnailUrl = "",
            controls = "play,progress,current-time,duration,mute,fullscreen,settings",
            keyColor = Color.WHITE,
            captionsFontSize = 16,
            captionsFontColor = null,
            captionsBackgroundColor = null,
            uiLanguage = "en",
            showHeatmap = false,
            fontFamily = "roboto",
            playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
            drmEnabled = false,
            vastTagUrl = null,
            videoUrl = "",
            seekPath = "",
            captionsPath = ""
        )
    }

    private fun startProgressUpdates() {
        lifecycleScope.launch {
            while (isActive && bunnyPlayer != null) {
                tvControls.updateProgress()
                delay(1000)
            }
        }
    }

    protected open fun handleTVKeyEvent(keyEvent: KeyEvent): Boolean {
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (tvControls.isControlsVisible()) {
                        // Controls are visible, let them handle it
                        return false
                    } else {
                        // Show controls
                        tvControls.show()
                        return true
                    }
                }
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    bunnyPlayer?.let { player ->
                        if (player.isPlaying()) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    }
                    tvControls.show()
                    return true
                }
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN && !tvControls.isControlsVisible()) {
                    bunnyPlayer?.skipForward()
                    tvControls.show()
                    return true
                }
            }

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN && !tvControls.isControlsVisible()) {
                    bunnyPlayer?.replay()
                    tvControls.show()
                    return true
                }
            }

            KeyEvent.KEYCODE_BACK -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (tvControls.isControlsVisible()) {
                        tvControls.hide()
                        return true
                    } else {
                        finish()
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let { keyEvent ->
            if (handleTVKeyEvent(keyEvent)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    protected open fun showResumeDialog(position: PlaybackPosition, callback: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tv_resume_title))
            .setMessage(getString(R.string.tv_resume_message, formatTime(position.position)))
            .setPositiveButton(getString(R.string.tv_resume_yes)) { _, _ -> callback(true) }
            .setNegativeButton(getString(R.string.tv_resume_no)) { _, _ -> callback(false) }
            .setCancelable(false)
            .show()
    }

    protected open fun showSettingsDialog() {
        bunnyPlayer?.let { player ->
            val settingsDialog = TVSettingsDialog(this, player)
            settingsDialog.show()
        }
    }

    protected open fun showError(title: String, message: String) {
        tvControls.hideLoading()
        errorMessage.text = message
        errorContainer.visibility = View.VISIBLE
        retryButton.requestFocus()
    }

    private fun hideError() {
        errorContainer.visibility = View.GONE
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

    // Extension function to convert VideoPlayDataModelVideo to VideoModel
    private fun VideoPlayDataModelVideo.toVideoModel(): VideoModel = VideoModel(
        videoLibraryId = this.videoLibraryId,
        guid = this.guid,
        title = this.title,
        dateUploaded = this.dateUploaded,
        views = this.views,
        isPublic = this.isPublic,
        length = this.length,
        status = this.status,
        framerate = this.framerate,
        rotation = this.rotation,
        width = this.width,
        height = this.height,
        availableResolutions = this.availableResolutions,
        outputCodecs = this.outputCodecs,
        thumbnailCount = this.thumbnailCount,
        encodeProgress = this.encodeProgress,
        storageSize = this.storageSize,
        captions = this.captions,
        hasMP4Fallback = this.hasMP4Fallback,
        collectionId = this.collectionId,
        thumbnailFileName = this.thumbnailFileName,
        averageWatchTime = this.averageWatchTime,
        totalWatchTime = this.totalWatchTime,
        category = this.category,
        chapters = this.chapters,
        moments = this.moments,
        metaTags = this.metaTags,
        transcodingMessages = this.transcodingMessages
    )

    override fun onResume() {
        super.onResume()
        bunnyPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        bunnyPlayer?.pause()
    }

    override fun onDestroy() {
        bunnyPlayer?.release()
        super.onDestroy()
    }
}