package net.bunny.tv.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import kotlin.coroutines.cancellation.CancellationException

open class BunnyTVPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var tvControls: TVPlayerControlsView
    private lateinit var keyEventHandler: TVKeyEventHandler
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private var progressJob: Job? = null
    private var bunnyPlayer: BunnyPlayer? = null
    private var videoId: String? = null
    private var libraryId: Long? = null
    private var currentVideo: VideoModel? = null
    private var isResumeDialogShowing = false
    private var isVideoInitialized = false

    // FIXED: Add main thread handler for thread-safe operations
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "BunnyTVPlayerActivity"
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
        Log.d(TAG, "onCreate - Starting TV Player Activity")

        setContentView(R.layout.activity_tv_player)

        // Make full screen and keep screen on
        setupFullScreenMode()

        // Get intent data
        videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        libraryId = intent.getLongExtra(EXTRA_LIBRARY_ID, -1L)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)

        Log.d(TAG, "onCreate - Video ID: $videoId, Library ID: $libraryId")

        setupViews()
        setupPlayer()
        setupKeyHandling()
        setupErrorHandling()

        // Set video title if provided
        videoTitle?.let { tvControls.setVideoTitle(it) }

        // Validate parameters before loading
        if (videoId.isNullOrEmpty()) {
            Log.e(TAG, "onCreate - Video ID is null or empty")
            showError("Invalid Parameters", "Video ID is missing")
            return
        }

        if (libraryId == null || libraryId == -1L) {
            Log.e(TAG, "onCreate - Library ID is invalid: $libraryId")
            showError("Invalid Parameters", "Library ID is missing or invalid")
            return
        }

        // Load video
        loadVideo(videoId!!, libraryId!!)
    }

    private fun setupFullScreenMode() {
        Log.d(TAG, "setupFullScreenMode")

        // Hide system UI for immersive experience on TV
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

        // Set window flags to hide native TV UI
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // For Android TV, also hide the action bar
        supportActionBar?.hide()
    }

    private fun setupViews() {
        Log.d(TAG, "setupViews")

        playerView = findViewById(R.id.tv_player_view)
        tvControls = findViewById(R.id.tv_controls)
        errorContainer = findViewById(R.id.tv_error_container)
        errorMessage = findViewById(R.id.tv_error_message)
        retryButton = findViewById(R.id.tv_error_retry)

        // Configure player view for TV - disable native controls completely
        playerView.useController = false // Disable native controls
        playerView.controllerAutoShow = false // Don't auto-show controls
        playerView.controllerHideOnTouch = false // Don't respond to touch
        playerView.setShutterBackgroundColor(Color.BLACK)

        Log.d(TAG, "setupViews - Views initialized")
    }

    private fun setupPlayer() {
        Log.d(TAG, "setupPlayer")

        try {
            bunnyPlayer = DefaultBunnyPlayer.getInstance(this)
            tvControls.setBunnyPlayer(bunnyPlayer!!)

            // Setup settings click listener - FIXED
            tvControls.setOnSettingsClickListener {
                Log.d(TAG, "Settings button clicked")
                showSettingsDialog()
            }

            Log.d(TAG, "setupPlayer - Player initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "setupPlayer - Error initializing player", e)
            showError("Player Error", "Failed to initialize video player: ${e.message}")
        }
    }

    private fun setupKeyHandling() {
        Log.d(TAG, "setupKeyHandling")

        keyEventHandler = TVKeyEventHandler { keyEvent ->
            handleTVKeyEvent(keyEvent)
        }
    }

    private fun setupErrorHandling() {
        Log.d(TAG, "setupErrorHandling")

        retryButton.setOnClickListener {
            Log.d(TAG, "Retry button clicked")
            hideError()
            isVideoInitialized = false
            isResumeDialogShowing = false
            videoId?.let { id ->
                libraryId?.let { libId ->
                    loadVideo(id, libId)
                }
            }
        }
    }

    private fun loadVideo(videoId: String, libraryId: Long, retryCount: Long = 0) {
        Log.d(TAG, "loadVideo - Starting to load video: $videoId from library: $libraryId")

        // FIXED: Ensure UI updates happen on main thread
        runOnUiThread {
            tvControls.showLoading()
            hideError()
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "loadVideo - Fetching video data from API")
                withTimeout(30000) { // 30 second timeout
                    // Your existing load logic
                    val videoPlayData = withContext(Dispatchers.IO) {
                        BunnyStreamApi.getInstance().videosApi.videoGetVideoPlayData(
                            libraryId, videoId
                        )
                    }

                    val video = videoPlayData.video?.toVideoModel()

                    if (video == null) {
                        Log.e(TAG, "loadVideo - Video data is null")
                        showError("Video Not Found", "The requested video could not be found.")
                        return@withTimeout
                    }

                    Log.d(TAG, "loadVideo - Video data loaded successfully: ${video.title}")
                    currentVideo = video

                    // Load player settings - FIXED to use the correct response structure
                    val playerSettings = withContext(Dispatchers.IO) {
                        try {
                            val settingsResult =
                                BunnyStreamApi.getInstance().fetchPlayerSettings(libraryId, videoId)
                            settingsResult.fold(
                                ifLeft = { error ->
                                    Log.w(TAG, "Failed to fetch player settings: $error")
                                    createDefaultPlayerSettings()
                                },
                                ifRight = { settings ->
                                    Log.d(TAG, "Player settings loaded successfully")
                                    settings
                                }
                            )
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "loadVideo - Failed to fetch player settings, using defaults",
                                e
                            )
                            createDefaultPlayerSettings()
                        }
                    }

                    Log.d(TAG, "loadVideo - Player settings configured")

                    // FIXED: Initialize video on main thread
                    withContext(Dispatchers.Main) {
                        initializeVideo(video, playerSettings)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (retryCount < 3 && !isFinishing) {
                    delay(2000 * (retryCount + 1)) // Exponential backoff
                    loadVideo(videoId, libraryId, retryCount + 1)
                } else {
                    showError("Connection Timeout", "Please check your network connection")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadVideo - Error loading video", e)
                showError("Error Loading Video", e.message ?: "An unknown error occurred")
            }
        }
    }

    // FIXED: Ensure all player operations happen on main thread
    private fun initializeVideo(video: VideoModel, playerSettings: PlayerSettings) {
        Log.d(TAG, "initializeVideo - Initializing video: ${video.title}")

        if (isVideoInitialized) {
            Log.w(TAG, "initializeVideo - Video already initialized, skipping")
            return
        }

        tvControls.hideLoading()

        // Set video title
        video.title?.let {
            Log.d(TAG, "initializeVideo - Setting video title: $it")
            tvControls.setVideoTitle(it)
        }

        try {
            // Setup resume position functionality
            Log.d(TAG, "initializeVideo - Setting up resume position")
            bunnyPlayer?.enableResumePosition(ResumeConfig())

            // Set resume position listener with duplicate prevention
            bunnyPlayer?.setResumePositionListener(object :
                net.bunny.api.playback.ResumePositionListener {
                override fun onResumePositionAvailable(
                    videoId: String,
                    position: PlaybackPosition
                ) {
                    Log.d(TAG, "Resume position available: ${position.position}")

                    // FIXED: Ensure dialog is shown on main thread
                    // This post is actually redundant since the listener is called on the main thread
                    if (!isResumeDialogShowing) {
                        isResumeDialogShowing = true
                        showResumeDialog(position) { shouldResume ->
                            isResumeDialogShowing = false
                            if (shouldResume) {
                                // FIXED: Ensure seek operation is on main thread
                                bunnyPlayer?.seekTo(position.position)
                                startPlayback()
                            } else {
                                // Always start playback after resume decision
                                startPlayback()
                            }
                        }
                    }
                }

                override fun onResumePositionSaved(videoId: String, position: PlaybackPosition) {
                    Log.d(TAG, "Resume position saved: ${position.position}")
                }
            })

            // FIXED: Initialize player with video using the correct method signature
            Log.d(TAG, "initializeVideo - Starting video playback")

            // Create metadata map for the player
            val videoMetadata = mapOf<String, Any>(
                "title" to (video.title ?: ""),
                "duration" to (video.length ?: 0),
                "videoId" to videoId!!,
                "libraryId" to libraryId!!
            )

            // Use the correct method to play video
            bunnyPlayer?.playVideo(playerView, video, emptyMap(), playerSettings)

            isVideoInitialized = true

            // FIXED: Start progress updates on the main thread
            startProgressUpdates()

            // Start playback after a delay (if no resume position dialog)
            lifecycleScope.launch(Dispatchers.Main) {
                delay(3000) // Give more time for video to be ready
                if (!isResumeDialogShowing && isVideoInitialized) {
                    Log.d(TAG, "initializeVideo - Auto-starting playback (no resume)")
                    startPlayback()
                }
            }

            Log.d(TAG, "initializeVideo - Video initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "initializeVideo - Error during video initialization", e)
            showError("Playback Error", e.message ?: "Failed to start video playback")
        }
    }

    private fun startPlayback() {
        Log.d(TAG, "startPlayback - Starting video playback")

        try {
            bunnyPlayer?.let { player ->
                if (isVideoInitialized && !isFinishing) {
                    player.play()
                    tvControls.show()
                }
            } ?: Log.w(TAG, "Cannot start playback: player is null")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
        }
    }

    private fun createDefaultPlayerSettings(): PlayerSettings {
        Log.d(TAG, "createDefaultPlayerSettings")

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
            captionsPath = "",
            // FIXED: Increase the save progress interval to reduce threading conflicts
            // The default of 10 seconds may be too frequent and cause threading issues
            saveProgressInterval = 30000 // Changed from 10000 to 30000 (30 seconds)
        )
    }

    // FIXED: Ensure progress updates are thread-safe
    private fun startProgressUpdates() {
        // Cancel any existing job
        progressJob?.cancel()

        progressJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    if (bunnyPlayer != null && isVideoInitialized && !isFinishing) {
                        withContext(Dispatchers.Main) {
                            tvControls.updateProgress()
                        }
                    }
                    delay(1000)
                } catch (e: CancellationException) {
                    throw e // Rethrow to properly cancel
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating progress", e)
                }
            }
        }
    }


    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // onUserLeaveHint is called on the main thread, no need for post
        bunnyPlayer?.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isFinishing || isDestroyed) {
            return super.onKeyDown(keyCode, event)
        }

        return try {
            event?.let { keyEvent ->
                handleTVKeyEvent(keyEvent) || super.onKeyDown(keyCode, event)
            } ?: super.onKeyDown(keyCode, event)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling key event", e)
            super.onKeyDown(keyCode, event)
        }
    }
    // FIXED: Ensure all player operations in key handling are on main thread
    protected open fun handleTVKeyEvent(keyEvent: KeyEvent): Boolean {
        val keyCode = keyEvent.keyCode
        Log.d(TAG, "handleTVKeyEvent - Handling key: $keyCode, action: ${keyEvent.action}")

        // Only handle key down events
        if (keyEvent.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                Log.d(TAG, "handleTVKeyEvent - Center/Enter pressed")
                if (tvControls.isControlsVisible()) {
                    return false // Let the controls handle it
                } else {
                    tvControls.show()
                    return true
                }
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                Log.d(TAG, "handleTVKeyEvent - Play/Pause pressed")
                bunnyPlayer?.let { player ->
                    try {
                        if (player.isPlaying()) {
                            Log.d(TAG, "handleTVKeyEvent - Pausing video")
                            player.pause()
                        } else {
                            Log.d(TAG, "handleTVKeyEvent - Playing video")
                            player.play()
                        }
                        tvControls.show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling playback", e)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!tvControls.isControlsVisible()) {
                    Log.d(TAG, "handleTVKeyEvent - Fast forward")
                    try {
                        bunnyPlayer?.skipForward()
                        tvControls.show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error skipping forward", e)
                    }
                    return true
                }
                return false // Let controls handle navigation
            }

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!tvControls.isControlsVisible()) {
                    Log.d(TAG, "handleTVKeyEvent - Rewind")
                    try {
                        bunnyPlayer?.replay()
                        tvControls.show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error rewinding", e)
                    }
                    return true
                }
                return false // Let controls handle navigation
            }

            KeyEvent.KEYCODE_BACK -> {
                Log.d(TAG, "handleTVKeyEvent - Back pressed")
                if (tvControls.isControlsVisible()) {
                    tvControls.hide()
                    return true
                } else {
                    Log.d(TAG, "handleTVKeyEvent - Finishing activity")
                    finish()
                    return true
                }
            }

            KeyEvent.KEYCODE_MENU -> {
                Log.d(TAG, "handleTVKeyEvent - Menu pressed - showing controls")
                tvControls.show()
                return true
            }
        }
        return false
    }

    protected open fun showResumeDialog(position: PlaybackPosition, callback: (Boolean) -> Unit) {
        Log.d(TAG, "showResumeDialog - Position: ${position.position}")

        // This check is already redundant if called from the main thread, but a good defensive practice
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showResumeDialog(position, callback) }
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Resume Playback")
            .setMessage("Continue watching from ${formatTime(position.position)}?")
            .setPositiveButton("Resume") { _, _ ->
                Log.d(TAG, "showResumeDialog - User chose to resume")
                callback(true)
            }
            .setNegativeButton("Start Over") { _, _ ->
                Log.d(TAG, "showResumeDialog - User chose to start over")
                callback(false)
            }
            .setCancelable(false)
            .setOnDismissListener {
                isResumeDialogShowing = false
            }
            .show()
    }

    // FIXED: Settings dialog implementation
    protected open fun showSettingsDialog() {
        Log.d(TAG, "showSettingsDialog")

        bunnyPlayer?.let { player ->
            try {
                val settingsDialog = TVSettingsDialog(this, player)
                settingsDialog.show()
                Log.d(TAG, "Settings dialog shown successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog", e)
                // Fallback: show a simple dialog if the custom one fails
                showSimpleSettingsDialog()
            }
        } ?: run {
            Log.w(TAG, "Cannot show settings: bunnyPlayer is null")
        }
    }

    // Fallback settings dialog
    private fun showSimpleSettingsDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val speedValues = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                val selectedSpeed = speedValues[which]

                // Ensure speed changes are on the main thread
                bunnyPlayer?.setSpeed(selectedSpeed)
                Log.d(TAG, "Speed changed to: ${selectedSpeed}x")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    protected open fun showError(title: String, message: String) {
        Log.e(TAG, "showError - Title: $title, Message: $message")

        runOnUiThread {
            tvControls.hideLoading()
            errorMessage.text = message
            errorContainer.visibility = View.VISIBLE
            retryButton.requestFocus()
        }
    }

    private fun hideError() {
        Log.d(TAG, "hideError")
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
        Log.d(TAG, "onResume")
        // Hide system UI again when resuming
        setupFullScreenMode()

        // FIXED: Only resume if video is initialized - onResume is already on main thread
        if (isVideoInitialized) {
            try {
                bunnyPlayer?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming playback", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")

        // FIXED: onPause is already on main thread
        try {
            bunnyPlayer?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")

        // FIXED: onStop is already on main thread
        try {
            bunnyPlayer?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        lifecycleScope.cancel()

        try {
            bunnyPlayer?.release()
            bunnyPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
        }
        super.onDestroy()
    }
}