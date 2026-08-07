package net.bunny.bunnystreamplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
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
import net.bunny.api.StreamApi
import net.bunny.api.error.errorOrNull
import net.bunny.api.error.fold
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.ResumeConfig
import net.bunny.api.playback.ResumePositionListener
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.bunnystreamplayer.livestream.livePlayerSettings
import net.bunny.bunnystreamplayer.DefaultBunnyPlayer
import net.bunny.bunnystreamplayer.cmcd.CmcdStreamType
import net.bunny.bunnystreamplayer.common.DeviceType
import net.bunny.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunny.bunnystreamplayer.model.PlayerIconSet
import net.bunny.bunnystreamplayer.model.getSanitizedRetentionData
import net.bunny.bunnystreamplayer.ui.fullscreen.FullScreenPlayerActivity
import net.bunny.bunnystreamplayer.ui.widget.BunnyPlayerView
import net.bunny.player.databinding.ViewBunnyVideoPlayerBinding
import net.bunny.api.error.getOrNull
import net.bunny.api.video.domain.model.Video


/**
 * The video player view of the Bunny Stream SDK. Add it to a layout (or wrap it in `AndroidView`
 * from Compose) and call [playVideo] with a video id from your library:
 *
 * ```kotlin
 * player.playVideo(videoId = "your-video-guid")
 * ```
 *
 * The view needs an SDK instance: either [net.bunny.api.BunnyStreamApi.initialize] has been
 * called, or [bunny] is set to an instance from `BunnyStreamApi.create`. With neither, [playVideo]
 * logs an error and shows nothing. Appearance (accent color, visible controls, captions styling)
 * comes from the library's player settings in the Bunny dashboard. Icons can be replaced through
 * [iconSet].
 *
 * What the view handles on its own:
 * - playback controls, seek-bar preview thumbnails, chapters, moments and captions
 * - fullscreen (opens a dedicated fullscreen screen) and Picture-in-Picture (the host activity
 *   must declare `android:supportsPictureInPicture="true"`)
 * - Chromecast, when Google Play services are available
 * - pausing when the host goes to the background and resuming on return
 * - resume positions, once enabled with [enableResumePosition]
 *
 * The view attaches to its host lifecycle automatically. Playback stops when the view is detached
 * from the window. One playback engine is shared per process; use one player view at a time and
 * detach it before starting playback in another.
 */
class BunnyStreamPlayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), BunnyPlayer {

    companion object {
        private const val TAG = "BunnyVideoPlayer"
        private const val AUTO_SAVE_INTERVAL = 10_000L // 10 seconds

    }

    /**
     * The SDK instance this view plays from. Leave it null to use the one
     * [net.bunny.api.BunnyStreamApi.initialize] registered.
     *
     * Set it when your app addresses more than one library and this view belongs to a specific
     * one. Assign before calling [playVideo]; the value is read per call, so a view can be moved
     * between instances.
     */
    var bunny: StreamApi? = null

    /** The instance to use right now. Resolved per call, never cached. */
    private val sdk: StreamApi get() = bunny ?: BunnyStreamApi.getInstance()

    /** True when this view has an instance to work with, whether its own or the default one. */
    private val hasSdk: Boolean get() = bunny != null || BunnyStreamApi.isInitialized()

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

    /**
     * When `true`, the player periodically samples the video frame behind the position/duration
     * readout and flips the text between black and white based on luminance, so the readout stays
     * legible regardless of scene brightness. Defaults to `false` to preserve existing behavior;
     * opt in from your app.
     *
     * Forwards to [BunnyPlayerView.autoProgressTextColor].
     */
    var autoProgressTextColor: Boolean
        get() = playerView.autoProgressTextColor
        set(value) {
            playerView.autoProgressTextColor = value
        }

    /**
     * Manual override for the position/duration text color. Has no lasting effect while
     * [autoProgressTextColor] is enabled — the sampler overrides it on each tick.
     *
     * Forwards to [BunnyPlayerView.progressTextColor].
     */
    var progressTextColor: Int
        get() = playerView.progressTextColor
        set(value) {
            playerView.progressTextColor = value
        }

    /**
     * Invoked with the current video's pixel dimensions (first frame and on change) so the host can
     * size its container to the real aspect ratio — supporting both 16:9 and 9:16 (vertical) content.
     *
     * Forwards to [BunnyPlayerView.onVideoSizeChanged].
     */
    var onVideoSizeChanged: ((width: Int, height: Int) -> Unit)?
        get() = playerView.onVideoSizeChanged
        set(value) {
            playerView.onVideoSizeChanged = value
        }

    /**
     * Invoked when the engine reports a playback error. The live player uses it to re-poll the
     * stream and rebuild playback from the live edge. Forwards to [BunnyPlayerView.onPlaybackError].
     */
    var onPlaybackError: ((message: String) -> Unit)?
        get() = playerView.onPlaybackError
        set(value) {
            playerView.onPlaybackError = value
        }

    /**
     * Condensed control bar (hides secondary controls like settings/captions/duration). Driven for
     * live playback by the dashboard's `enableCompactControls` from the live `/play` customization.
     * Forwards to [BunnyPlayerView.compactControls].
     */
    var compactControls: Boolean
        get() = playerView.compactControls
        set(value) {
            playerView.compactControls = value
        }

    private val bunnyPlayer = DefaultBunnyPlayer.getInstance(context)
    private var progressListener: BunnyPlayer.ProgressListener? = null
    private var progressListenerJob: Job? = null

    private var cachedProgress: Float = 0f
    private var lastProgressUpdate: Long = 0L
    private val PROGRESS_CACHE_MS = 100L


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
    fun playVideoWithTVDetection(videoId: String, libraryId: Long?, token: String? = null, expires: Long? = null) {
        if (isRunningOnTV()) {
            // Launch TV player - need to import from the tv module
            try {
                val tvPlayerClass = Class.forName("net.bunny.tv.ui.BunnyTVPlayerActivity")
                val startMethod = tvPlayerClass.getMethod("start", Context::class.java, String::class.java, Long::class.java, String::class.java, String::class.java, Long::class.java)
                startMethod.invoke(null, context, videoId, libraryId ?: -1L, null, token, expires ?: -1L)
            } catch (e: Exception) {
                Log.w(TAG, "TV player not available, falling back to mobile player", e)
                playVideo(videoId, libraryId, videoTitle = "", token = token, expires = expires) //TODO: must be change to real video title
            }
        } else {
            // Use regular mobile player
            playVideo(videoId, libraryId, videoTitle = "", token = token, expires = expires) //TODO: must be change to real video title
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

    /** True when the hosting Activity is currently in picture-in-picture mode. */
    private fun isInPictureInPictureMode(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx.isInPictureInPictureMode
            ctx = ctx.baseContext
        }
        return false
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (bunnyPlayer.autoPaused) {
                bunnyPlayer.play()
            }
            startAutoSave() // Resume auto-save when active
        }

        override fun onPause(owner: LifecycleOwner) {
            // Entering picture-in-picture fires ON_PAUSE on the host Activity too — playback must
            // keep running inside the PiP window, so skip the auto-pause (auto-save keeps going).
            if (isInPictureInPictureMode()) {
                Log.d(TAG, "ON_PAUSE while in PiP — keeping playback running")
                return
            }
            val autoPaused = bunnyPlayer.isPlaying()
            bunnyPlayer.pause(autoPaused)

            // Save immediately on pause - use coroutine
            scope?.launch {
                saveCurrentPosition()
            }
            stopAutoSave()
        }

        override fun onStop(owner: LifecycleOwner) {
            // Closing the PiP window skips the (PiP-guarded) ON_PAUSE pause and lands here —
            // stop playback so audio doesn't keep playing in the background. autoPaused=false:
            // the user dismissed the window deliberately, don't auto-resume on return. In the
            // normal background flow ON_PAUSE already paused, so this is a no-op.
            if (bunnyPlayer.isPlaying()) {
                bunnyPlayer.pause(autoPaused = false)
            }
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
        stopProgressListener() // Add this line
        bunnyPlayer.stop()

        // Hand the engine's output back before this view goes away. The engine is shared by every
        // player view in the process, so a detached view that keeps holding it leaves the output
        // bound to a surface that no longer exists — and the next view gets a player that decodes
        // into nothing: playback runs, the timeline moves, the picture stays black and no error is
        // raised. Only the second and later playback in a session hit it, which is why every test
        // that played one video passed.
        binding.playerView.player = null
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

    /**
     * Play a pre-resolved HLS URL through the standard Bunny player UI (custom controller,
     * progress text auto-contrast, fullscreen, etc.). Used by the live-stream Compose surface in
     * `net.bunny.bunnystreamplayer.livestream` so live playback looks visually identical to VOD
     * — same controls, same chrome.
     *
     * Unlike [playVideo], this method does not fetch video metadata or player settings from the
     * server: the caller already has both (resolved from the live stream's play-data endpoint),
     * and going through the videos play-data endpoint would 404 for a live-only stream id. We
     * synthesise the minimal [VideoModel] the engine needs; [PlayerSettings] is built from the
     * live `/play` customization via [livePlayerSettings] — the live player is server-driven
     * (dashboard player settings), mirroring the iOS SDK. No client-side configuration.
     *
     * @param libraryId the Bunny library id.
     * @param streamId  the live-stream GUID (used as the engine's `currentVideoId` so position
     *                  resume keys don't collide with VOD entries).
     * @param videoTitle title for analytics/logging; not shown in the controls (the demo screen
     *                  already renders a top app bar with the title).
     * @param hlsUrl    pre-resolved playable URL (videoPlaylistUrl > fallbackUrl > playbackUrlHls).
     * @param enableSubtitles forwarded to the synthetic PlayerSettings. Defaults to `false` for
     *                  live (Bunny doesn't currently surface live captions through this path).
     * @param playData  the live `/play` response carrying the dashboard customization (accent
     *                  colour, font, UI language, control tokens, compact mode, heatmap). `null`
     *                  (not fetched yet) falls back to SDK defaults.
     * @param isVodRecording the URL is the ended stream's recording (live→VOD hand-off): the
     *                  timeline stays (a recording is fully seekable) and CMCD reports `st=v`.
     */
    fun playLiveUrl(
        libraryId: Long,
        streamId: String,
        videoTitle: String,
        hlsUrl: String,
        enableSubtitles: Boolean = false,
        playData: LiveStreamPlayData? = null,
        dvrEnabled: Boolean = false,
        isVodRecording: Boolean = false,
    ) {
        Log.d(
            TAG,
            "playLiveUrl streamId=$streamId hlsUrl=${hlsUrl.take(80)} " +
                "serverCustomization=${playData != null}",
        )
        if (!hasSdk) {
            Log.e(TAG, "Unable to play live, initialize BunnyStreamApi first")
            return
        }

        currentVideoId = streamId
        currentLibraryId = libraryId

        loadVideoJob?.cancel()

        // Synthetic Video — the engine only reads id/title/library id/captions for the happy
        // path; the rest stays at its empty defaults and the engine treats them as missing.
        val video = Video(id = streamId, videoLibraryId = libraryId, title = videoTitle)

        // Compact mode is a view-level layout concern (not part of PlayerSettings), so forward the
        // dashboard's flag straight to the player view before building the settings.
        compactControls = playData?.enableCompactControls ?: false

        // Theming + control flags come from the dashboard's live /play customization (server-
        // driven, like iOS); DVR gating strips the timeline tokens for non-DVR live streams,
        // while an ended stream's recording keeps its full, seekable timeline.
        val settings = livePlayerSettings(
            playData = playData,
            hlsUrl = hlsUrl,
            dvrEnabled = dvrEnabled,
            enableSubtitles = enableSubtitles,
            isVodRecording = isVodRecording,
        )

        // CMCD (CTA-5004 v2) stream type: the ended stream's recording is plain VOD (st=v); a
        // DVR-enabled live stream reports st=e (event), a plain live stream st=l. The
        // transmission mode itself is fixed internally by the SDK.
        (bunnyPlayer as? DefaultBunnyPlayer)?.setCmcdStreamType(
            when {
                isVodRecording -> CmcdStreamType.VOD
                dvrEnabled -> CmcdStreamType.EVENT
                else -> CmcdStreamType.LIVE
            },
        )

        pendingJob = {
            scope!!.launch { initializeVideo(video, settings) }
        }
        if (scope == null) {
            Log.d(TAG, "playLiveUrl deferred — view not yet attached")
            return
        }
        loadVideoJob = pendingJob?.invoke()
        pendingJob = null
    }

    override fun playVideo(videoId: String, libraryId: Long?, videoTitle: String, token: String?, expires: Long?) {
        Log.d(TAG, "playVideo videoId=$videoId")

        currentVideoId = videoId
        currentLibraryId = libraryId

        if (!hasSdk) {
            Log.e(
                TAG,
                "Unable to play video, initialize the player first using BunnyStreamSdk.initialize"
            )
            return
        }

        // Read after the guard: the library id now lives on the instance, so there is nothing to
        // read until one exists.
        val providedLibraryId = libraryId ?: sdk.libraryId

        // CMCD (CTA-5004 v2) stream type for VOD playback (st=v).
        (bunnyPlayer as? DefaultBunnyPlayer)?.setCmcdStreamType(CmcdStreamType.VOD)

        // Save previous video position before switching
        saveCurrentPosition()

        loadVideoJob?.cancel()

        pendingJob = {
            scope!!.launch {

                val playData = sdk.videoRepository
                    .fetchVideoPlayData(providedLibraryId, videoId, token, expires)
                val video = playData.getOrNull()?.video
                if (video == null) {
                    // Tell the viewer and the host app. This used to log and return, leaving a
                    // black view with no explanation — indistinguishable from a player that is
                    // still loading, and the commonest thing behind "the player shows nothing".
                    val reason = playData.errorOrNull()?.message
                        ?: "Video $videoId is not available in library $providedLibraryId"
                    Log.w(TAG, "Error fetching video $videoId — $reason")
                    playerView.showError(reason)
                    onPlaybackError?.invoke(reason)
                    return@launch
                }
                Log.d(TAG, "video=$video")

                val settings = sdk
                    .fetchPlayerSettings(providedLibraryId, videoId, token, expires)

                settings.fold(
                    onErr = {
                        initializeVideo(
                            video,
                            token = token,
                            expires = expires,
                            playerSettings = PlayerSettings(
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
                        playerView.showError(it.message)
                    },
                    onOk = { initializeVideo(video, it, token, expires) }
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
    override fun seekTo(position: Long) {
        bunnyPlayer.seekTo(position)
    }

    override fun getCurrentPosition(): Long {
        return bunnyPlayer.getCurrentPosition()
    }

    override fun getDuration(): Long {
        return bunnyPlayer.getDuration()
    }

    override fun getProgress(): Float {
        val duration = getDuration()
        return if (duration > 0) getCurrentPosition().toFloat() / duration else 0f
    }

    override fun isPlaying(): Boolean {
        return bunnyPlayer.isPlaying()
    }

    override fun isEnded(): Boolean {
        val position = getCurrentPosition()
        val duration = getDuration()
        return duration > 0 && position >= duration
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

    /**
     * Get current position as formatted time string (e.g., "1:23" or "1:23:45")
     */
    fun getCurrentPositionFormatted(): String {
        return formatTime(getCurrentPosition())
    }

    /**
     * Get duration as formatted time string
     */
    fun getDurationFormatted(): String {
        return formatTime(getDuration())
    }

    override fun setProgressListener(listener: BunnyPlayer.ProgressListener?) {
        progressListener = listener

        if (listener != null) {
            startProgressListener()
        } else {
            stopProgressListener()
        }
    }

    private fun startProgressListener() {
        stopProgressListener()

        progressListenerJob = scope?.launch(Dispatchers.Main) {
            while (isActive && progressListener != null) {
                if (bunnyPlayer.isPlaying()) {
                    val position = getCurrentPosition()
                    val duration = getDuration()
                    val progress = getProgress()
                    progressListener?.onProgressChanged(position, duration, progress)
                }
                delay(250) // Update 4 times per second
            }
        }
    }

    private fun stopProgressListener() {
        progressListenerJob?.cancel()
        progressListenerJob = null
    }

    private suspend fun initializeVideo(
        video: Video,
        playerSettings: PlayerSettings,
        token: String? = null,
        expires: Long? = null,
    ) {
        // A fresh load invalidates any error from the previous source — without this, a
        // late-arriving error from the torn-down player (e.g. the stale live URL during the
        // live→VOD hand-off) stays painted over working playback.
        playerView.hideError()
        playerView.showPreviewThumbnail(playerSettings.thumbnailUrl)

        var retentionData: Map<Int, Int> = mutableMapOf()

        if (playerSettings.showHeatmap) {
            try {
                retentionData = sdk.videoRepository
                    .fetchVideoHeatmap(video.videoLibraryId, video.id)
                    .getOrNull()
                    .orEmpty()
                    .getSanitizedRetentionData()
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching video heatmap")
            }
        }

        // Claim the engine before starting it, not after. Assigning this hands the engine this
        // view's state listener, and playVideo emits onPlayerTypeChanged during the call — the
        // event that actually puts the player into a view. With the old order the engine still
        // held the listener of the previous, already destroyed view, so the second and every later
        // playback in a session delivered its player to a view nobody could see: the timeline ran,
        // the picture stayed black, and nothing failed loudly enough to notice.
        playerView.bunnyPlayer = bunnyPlayer

        bunnyPlayer.playVideo(
            binding.playerView,
            video,
            retentionData,
            playerSettings,
            // The engine builds the Widevine license URL from this host, so a view pointed at a
            // specific instance licenses against that instance's deployment. Falls back to the
            // production host if the instance vanished mid-load (release() racing this coroutine)
            // rather than killing playback that is otherwise ready to start.
            licenseBaseApi = runCatching { sdk.config.baseApi }
                .getOrElse { net.bunny.api.BuildConfig.BASE_API },
            // The pair also rides on the license URL for cast receivers, which fetch the
            // license themselves without the Referer header the local player sends.
            token = token,
            expires = expires,
        )
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


}