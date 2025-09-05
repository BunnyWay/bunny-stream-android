package net.bunny.bunnystreamplayer.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bunny.api.BunnyStreamApi
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.ResumeConfig
import net.bunny.api.playback.ResumePositionListener
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.bunnystreamplayer.DefaultBunnyPlayer
import net.bunny.bunnystreamplayer.common.DeviceType
import net.bunny.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunny.bunnystreamplayer.model.PlayerIconSet
import net.bunny.bunnystreamplayer.model.getSanitizedRetentionData
import net.bunny.bunnystreamplayer.ui.fullscreen.FullScreenPlayerActivity
import net.bunny.bunnystreamplayer.ui.widget.BunnyPlayerView
import net.bunny.player.databinding.ViewBunnyVideoPlayerBinding
import org.openapitools.client.models.VideoModel
import org.openapitools.client.models.VideoPlayDataModelVideo


class BunnyStreamPlayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), BunnyPlayer {

    companion object {
        private const val TAG = "BunnyVideoPlayer"
        private const val AUTO_SAVE_INTERVAL = 10_000L // 10 seconds
    }

    private var job: Job? = null
    private var scope: CoroutineScope? = null
    private var loadVideoJob: Job? = null
    private var autoSaveJob: Job? = null // Add auto-save job
    private var pendingJob: (() -> Job)? = null

    private val binding = ViewBunnyVideoPlayerBinding.inflate(LayoutInflater.from(context), this)

    private val playerView by lazy {
        binding.playerView
    }

    override var iconSet: PlayerIconSet = PlayerIconSet()
        set(value) {
            field = value
            playerView.iconSet = value
        }

    private val bunnyPlayer = DefaultBunnyPlayer.getInstance(context)

    // Resume position functionality
    private var resumePositionCallback: ((PlaybackPosition, (Boolean) -> Unit) -> Unit)? = null
    private var currentVideoId: String? = null
    private var currentLibraryId: Long? = null
    private var resumeConfig: ResumeConfig = ResumeConfig()
    /**
     * Check if the app is running on Android TV
     */
    fun isRunningOnTV(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /**
     * Get the device type (TV, Mobile, or Unknown)
     */
    fun getDeviceType(): DeviceType {
        return when {
            isRunningOnTV() -> DeviceType.TV
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> DeviceType.MOBILE
            else -> DeviceType.UNKNOWN
        }
    }
    /**
     * Play video with automatic TV/Mobile detection
     */
    fun playVideoWithTVDetection(videoId: String, libraryId: Long?) {
        if (isRunningOnTV()) {
            // Launch TV player - need to import from the tv module
            try {
                val tvPlayerClass = Class.forName("net.bunny.tv.ui.BunnyTVPlayerActivity")
                val startMethod = tvPlayerClass.getMethod("start", Context::class.java, String::class.java, Long::class.java, String::class.java)
                startMethod.invoke(null, context, videoId, libraryId ?: -1L, null)
            } catch (e: Exception) {
                Log.w(TAG, "TV player not available, falling back to mobile player", e)
                playVideo(videoId, libraryId, videoTitle = "") //TODO: must be change to real video title
            }
        } else {
            // Use regular mobile player
            playVideo(videoId, libraryId, videoTitle = "") //TODO: must be change to real video title
        }
    }
    private val resumePositionListener = object : ResumePositionListener {
        override fun onResumePositionAvailable(videoId: String, position: PlaybackPosition) {
            Log.d(TAG, "Resume position available: $position")
            resumePositionCallback?.invoke(position) { shouldResume ->
                if (shouldResume) {
                    bunnyPlayer.seekTo(position.position)
                }
            }
        }

        override fun onResumePositionSaved(videoId: String, position: PlaybackPosition) {
            Log.d(TAG, "Resume position saved: $position")
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (bunnyPlayer.autoPaused) {
                bunnyPlayer.play()
            }
            startAutoSave() // Resume auto-save when active
        }

        override fun onPause(owner: LifecycleOwner) {
            val autoPaused = bunnyPlayer.isPlaying()
            bunnyPlayer.pause(autoPaused)

            // Save immediately on pause - use coroutine
            scope?.launch {
                saveCurrentPosition()
            }
            stopAutoSave()
        }

        override fun onStop(owner: LifecycleOwner) {
            // Save when app goes to background - use coroutine
            scope?.launch {
                saveCurrentPosition()
            }
            stopAutoSave()
        }


        override fun onDestroy(owner: LifecycleOwner) {
            // Final save before destroy - use coroutine
            scope?.launch {
                saveCurrentPosition()
            }
            stopAutoSave()
        }
    }

    init {
        playerView.iconSet = iconSet
        playerView.fullscreenListener = object : BunnyPlayerView.FullscreenListener {
            override fun onFullscreenToggleClicked() {
                saveCurrentPosition() // Save before fullscreen transition
                playerView.bunnyPlayer = null
                FullScreenPlayerActivity.show(context, iconSet) {
                    Log.d(TAG, "onFullscreenExited")
                    playerView.bunnyPlayer = bunnyPlayer
                    startAutoSave() // Resume auto-save after returning from fullscreen
                }
            }
        }

        // Set up resume position listener
        bunnyPlayer.setResumePositionListener(resumePositionListener)

        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                Log.d(TAG, "onViewAttachedToWindow")
                job = SupervisorJob()
                scope = CoroutineScope(Dispatchers.Main + job!!)

                pendingJob?.let {
                    Log.d(TAG, "there is pending job, executing...")
                    pendingJob?.invoke()
                    pendingJob = null
                }

                findViewTreeLifecycleOwner()?.lifecycle?.addObserver(lifecycleObserver)
            }

            override fun onViewDetachedFromWindow(view: View) {
                Log.d(TAG, "onViewDetachedFromWindow")
                saveCurrentPosition() // Save on detach
                stopAutoSave()
                job?.cancel()
                findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(lifecycleObserver)
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow")

        // Save before detaching - use coroutine scope if available
        scope?.launch {
            saveCurrentPosition()
        }
        stopAutoSave()
        bunnyPlayer.stop()
    }

    fun setPlaybackSpeedConfig(config: PlaybackSpeedConfig) {
        val defaultPlayer = DefaultBunnyPlayer.getInstance(context)
        defaultPlayer.setPlaybackSpeedConfig(config)
    }

    /**
     * Enable resume position functionality with auto-save
     */
    fun enableResumePosition(
        config: ResumeConfig = ResumeConfig(),
        onResumePositionCallback: ((PlaybackPosition, (Boolean) -> Unit) -> Unit)? = null
    ) {
        this.resumeConfig = config
        bunnyPlayer.enableResumePosition(config)

        // Set the callback if provided
        onResumePositionCallback?.let { callback ->
            this.resumePositionCallback = callback
        }

        // Start auto-save if enabled in config
        if (config.enableAutoSave) {
            startAutoSave()
        }
    }

    /**
     * Disable resume position functionality
     */
    fun disableResumePosition() {
        bunnyPlayer.disableResumePosition()
        this.resumePositionCallback = null
        stopAutoSave()
    }

    /**
     * Clear saved position for specific video
     */
    fun clearSavedPosition(videoId: String) {
        bunnyPlayer.clearSavedPosition(videoId)
    }

    /**
     * Clear all saved positions
     */
    fun clearAllSavedPositions() {
        scope?.launch {
            bunnyPlayer.positionManager?.clearAllPositions()
        }
    }

    /**
     * Get all saved positions for debugging/management
     */
    fun getAllSavedPositions(callback: (List<PlaybackPosition>) -> Unit) {
        scope?.launch {
            val positions = bunnyPlayer.positionManager?.getAllPositions() ?: emptyList()
            callback(positions)
        }
    }

    override fun playVideo(videoId: String, libraryId: Long?, videoTitle: String) {
        Log.d(TAG, "playVideo videoId=$videoId")

        currentVideoId = videoId
        currentLibraryId = libraryId
        val providedLibraryId = libraryId ?: BunnyStreamApi.libraryId

        if (!BunnyStreamApi.isInitialized()) {
            Log.e(
                TAG,
                "Unable to play video, initialize the player first using BunnyStreamSdk.initialize"
            )
            return
        }

        // Save previous video position before switching
        saveCurrentPosition()

        loadVideoJob?.cancel()

        pendingJob = {
            scope!!.launch {

                val video: VideoModel

                try {
                    video = withContext(Dispatchers.IO) {
                        BunnyStreamApi.getInstance().videosApi.videoGetVideoPlayData(
                            providedLibraryId,
                            videoId
                        ).video?.toVideoModel()!!
                    }
                    Log.d(TAG, "video=$video")
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching video: $e")
                    return@launch
                }

                val settings = BunnyStreamApi.getInstance()
                    .fetchPlayerSettings(providedLibraryId, videoId)

                settings.fold(
                    ifLeft = {
                        initializeVideo(
                            video, PlayerSettings(
                                thumbnailUrl = "",
                                controls = "",
                                keyColor = 0,
                                captionsFontSize = 0,
                                captionsFontColor = null,
                                captionsBackgroundColor = null,
                                uiLanguage = "",
                                showHeatmap = false,
                                fontFamily = "",
                                playbackSpeeds = listOf(
                                    0.25f,
                                    0.5f,
                                    0.75f,
                                    1.0f,
                                    1.25f,
                                    1.5f,
                                    2.0f,
                                    3.0f,
                                    4.0f
                                ),
                                drmEnabled = false,
                                vastTagUrl = null,
                                videoUrl = "",
                                seekPath = "",
                                captionsPath = ""
                            )
                        )
                        playerView.showError(it)
                    },
                    ifRight = { initializeVideo(video, it) }
                )
            }
        }

        if (scope == null) {
            Log.d(TAG, "scope not created yet")
            return
        }

        loadVideoJob = pendingJob?.invoke()
        pendingJob = null
    }

    override fun pause() {
        scope?.launch {
            saveCurrentPosition()
        }
        bunnyPlayer.pause()
    }

    override fun play() {
        bunnyPlayer.play()
        // Auto-save will start automatically via lifecycle observer
    }

    private suspend fun initializeVideo(video: VideoModel, playerSettings: PlayerSettings) {
        playerView.showPreviewThumbnail(playerSettings.thumbnailUrl)

        var retentionData: Map<Int, Int> = mutableMapOf()

        if (playerSettings.showHeatmap) {
            try {
                val retentionDataResponse = withContext(Dispatchers.IO) {
                    BunnyStreamApi.getInstance().videosApi.videoGetVideoHeatmap(
                        video.videoLibraryId!!,
                        video.guid!!
                    )
                }
                retentionData = retentionDataResponse.getSanitizedRetentionData()
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching video heatmap")
            }
        }

        bunnyPlayer.playVideo(binding.playerView, video, retentionData, playerSettings)
        playerView.bunnyPlayer = bunnyPlayer

        // Start auto-save after video starts playing
        if (resumeConfig.enableAutoSave) {
            startAutoSave()
        }
    }

    private fun startAutoSave() {
        stopAutoSave() // Stop any existing auto-save job

        autoSaveJob = scope?.launch(Dispatchers.Main) { // <- Use Main dispatcher for timer
            while (isActive) {
                delay(resumeConfig.saveInterval)
                if (bunnyPlayer.isPlaying()) { // Safe on main thread
                    // Move save to background
                    launch(Dispatchers.IO) {
                        val position = withContext(Dispatchers.Main) {
                            bunnyPlayer.getCurrentPosition()
                        }
                        val duration = withContext(Dispatchers.Main) {
                            bunnyPlayer.getDuration()
                        }

                        currentVideoId?.let { videoId ->
                            if (position > 0 && duration > 0) {
                                bunnyPlayer.positionManager?.savePosition(videoId, position, duration)
                            }
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Auto-save started with interval: ${resumeConfig.saveInterval}ms")
    }
    private fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        Log.d(TAG, "Auto-save stopped")
    }

    private fun saveCurrentPosition() {
        currentVideoId?.let { videoId ->
            scope?.launch {
                try {
                    // Get position and duration on main thread
                    val position = withContext(Dispatchers.Main) {
                        bunnyPlayer.getCurrentPosition()
                    }
                    val duration = withContext(Dispatchers.Main) {
                        bunnyPlayer.getDuration()
                    }

                    // Save on background thread
                    if (position > 0 && duration > 0) {
                        withContext(Dispatchers.IO) {
                            bunnyPlayer.positionManager?.savePosition(videoId, position, duration)
                        }
                        Log.d(TAG, "Position saved for $videoId: ${formatTime(position)}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving current position", e)
                }
            }
        }
    }
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun VideoPlayDataModelVideo.toVideoModel(): VideoModel = VideoModel(
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
}