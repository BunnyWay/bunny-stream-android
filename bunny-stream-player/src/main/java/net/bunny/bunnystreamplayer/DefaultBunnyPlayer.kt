package net.bunny.bunnystreamplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import net.bunny.bunnystreamplayer.cmcd.CmcdPlayerSnapshot
import net.bunny.bunnystreamplayer.cmcd.CmcdResolver
import net.bunny.bunnystreamplayer.cmcd.CmcdSession
import net.bunny.bunnystreamplayer.cmcd.CmcdStreamType
import com.google.android.gms.cast.framework.CastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bunny.bunnystreamplayer.ui.widget.BunnyPlayerView
import net.bunny.player.R
import net.bunny.api.BunnyCdn
import net.bunny.api.playback.DefaultPlaybackPositionManager
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.PlaybackPositionManager
import net.bunny.api.playback.ResumeConfig
import net.bunny.api.playback.ResumePositionListener
import net.bunny.api.settings.PlaybackSpeedManager
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.settings.toUri
import net.bunny.bunnystreamplayer.cast.BunnyMediaItemConverter
import net.bunny.bunnystreamplayer.cast.CastCustomData
import net.bunny.bunnystreamplayer.cast.CastTrackBridge
import net.bunny.bunnystreamplayer.common.BunnyPlayer
import net.bunny.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunny.bunnystreamplayer.config.PlaybackSpeedPreferences
import net.bunny.bunnystreamplayer.context.AppCastContext
import net.bunny.bunnystreamplayer.model.AudioTrackInfo
import net.bunny.bunnystreamplayer.model.AudioTrackInfoOptions
import net.bunny.bunnystreamplayer.model.Chapter
import net.bunny.bunnystreamplayer.model.Moment
import net.bunny.bunnystreamplayer.model.RetentionGraphEntry
import net.bunny.bunnystreamplayer.model.SeekThumbnail
import net.bunny.bunnystreamplayer.model.SubtitleInfo
import net.bunny.bunnystreamplayer.model.Subtitles
import net.bunny.bunnystreamplayer.model.VideoQuality
import net.bunny.bunnystreamplayer.model.VideoQualityOptions
import net.bunny.api.video.domain.model.Video
import kotlin.math.ceil
import kotlin.math.round
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * The ExoPlayer-based implementation of [BunnyPlayer], obtained through [getInstance].
 *
 * The engine is a process-wide singleton: every
 * [net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer] view in the app shares this one instance;
 * use one player view at a time. Apps that embed the
 * player view never create this class themselves; use the engine directly only when building
 * custom player chrome on top of the [BunnyPlayer] interface.
 */
@SuppressLint("UnsafeOptInUsageError")
class DefaultBunnyPlayer private constructor(private val appContext: Context) : BunnyPlayer {

    companion object {
        private const val TAG = "DefaultBunnyPlayer"

        private const val SEEK_SKIP_MILLIS = 10 * 1000
        private const val THUMBNAILS_PER_IMAGE = 36

        @Volatile
        private var instance: BunnyPlayer? = null

        /** Returns the shared playback engine, creating it on first use. */
        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DefaultBunnyPlayer(context.applicationContext).also { instance = it }
            }
        fun isRunningOnTV(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    }

    // Override the context property from BunnyPlayer interface
    override val context: Context get() = this.appContext

    // Speed Variables
    private var speedConfig = PlaybackSpeedConfig()
    private val speedPreferences = PlaybackSpeedPreferences(context)
    private val speedManager = PlaybackSpeedManager()

    // Player Position Variables
    private var currentLibraryId: Long? = null
    private var resumePosition: Long = 0L

    private var localPlayer: Player? = null
    private var castPlayer: Player? = null
    override var currentPlayer: Player? = null

    private var currentVideo: Video? = null
    private var currentVideoId: String? = null
    private var selectedSubtitle: SubtitleInfo? = null
    private var subtitlesEnabled = false

    override var autoPaused = false

    // Resume position functionality
    override var positionManager: PlaybackPositionManager? = null
    private var resumePositionListener: ResumePositionListener? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var autoSaveJob: Job? = null
    private val autoSaveInterval = 10_000L // 10 seconds

    // CMCD (CTA-5004 v2) telemetry — always attached as a ?CMCD= query param (internal SDK logic,
    // no integrator toggle, mirroring iOS's fixed-transport design). Only the stream type varies per
    // playback (VOD -> st=v, live -> st=l/e), set by the caller. The buffer snapshot is refreshed on
    // the main thread by cmcdSnapshotJob and read (via @Volatile) from the ExoPlayer loader threads.
    @Volatile
    private var cmcdStreamType: CmcdStreamType = CmcdStreamType.VOD
    @Volatile
    private var cmcdBufferLengthMs: Long = 0L
    @Volatile
    private var cmcdBufferStarved: Boolean = false
    private var cmcdSnapshotJob: Job? = null
    private val cmcdSnapshotInterval = 1_000L // 1 second
    private var chapters = listOf<Chapter>()
        set(value) {
            field = value
            playerStateListener?.onChaptersUpdated(chapters)
        }

    private var moments = listOf<Moment>()
        set(value) {
            field = value
            playerStateListener?.onMomentsUpdated(moments)
        }

    private var retentionData = listOf<RetentionGraphEntry>()
        set(value) {
            field = value
            playerStateListener?.onRetentionGraphUpdated(retentionData)
        }

    override var playerStateListener: PlayerStateListener? = null
        set(value) {
            field = value
            playerStateListener?.onPlayingChanged(isPlaying())
            playerStateListener?.onMutedChanged(isMuted())
            playerStateListener?.onChaptersUpdated(chapters)
            playerStateListener?.onMomentsUpdated(moments)
            playerStateListener?.onRetentionGraphUpdated(retentionData)
        }

    /**
     * SDK-internal companion to [playerStateListener] for the structured failure report. Fires
     * before [PlayerStateListener.onPlayerError] so the SDK's own surfaces can settle their state
     * first. Not part of [BunnyPlayer]: the public interface only speaks in messages, and a
     * Kotlin interface cannot carry an internal member.
     */
    internal var playbackFailureInfoListener: ((PlaybackFailureInfo) -> Unit)? = null

    private var mediaItem: MediaItem? = null
    private var mediaItemBuilder: MediaItem.Builder? = null

    private var trackSelector: DefaultTrackSelector? = null

    private val httpDataSourceFactory: HttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory: DataSource.Factory = DataSource.Factory {
        val dataSource: HttpDataSource = httpDataSourceFactory.createDataSource()
        // Needed if "Block Direct Url File Access" is enabled on Dashboard
        dataSource.setRequestProperty("Referer", BunnyCdn.REFERER)
        dataSource
    }

    private val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            playerStateListener?.onPlayingChanged(isPlaying)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            Log.d(TAG, "onPlaybackParametersChanged speed: ${playbackParameters.speed}")
            playerStateListener?.onPlaybackSpeedChanged(playbackParameters.speed)
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            Log.d(TAG, "onIsLoadingChanged isLoading: $isLoading")
            playerStateListener?.onLoadingChanged(isLoading)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            Log.d(TAG, "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
            playerStateListener?.onVideoSizeChanged(videoSize.width, videoSize.height)
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            Log.d(TAG, "onTracksChanged tracks: $tracks")
            // The receiver's track list arrives asynchronously after the
            // cast load, so selections made before (or while) casting can't
            // be applied in one shot — reconcile whenever the receiver
            // reports tracks. Both bridge calls no-op once in sync.
            if (isCasting()) {
                reconcileCastSelections()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.e(TAG, "❌ Player error (${error.errorCodeName}): ${error.message}", error)

            error.errorCode.let {
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED ->
                        Log.e(TAG, "DRM unspecified error – possibly malformed license or unknown cause")

                    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED ->
                        Log.e(TAG, "DRM scheme unsupported – device or ExoPlayer doesn't support Widevine")

                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ->
                        Log.e(TAG, "DRM provisioning failed – check internet connection or device provisioning")

                    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR ->
                        Log.e(TAG, "DRM content error – possibly corrupted or tampered content keys")

                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED ->
                        Log.e(TAG, "DRM license acquisition failed – invalid license URL or headers")

                    PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION ->
                        Log.e(TAG, "DRM disallowed operation – action not permitted by DRM policy (e.g. seeking)")

                    PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR ->
                        Log.e(TAG, "DRM system error – device DRM stack failure (e.g. MediaDrm crash)")

                    PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED ->
                        Log.e(TAG, "DRM device revoked – device has been blacklisted for content protection")

                    PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
                        Log.e(TAG, "DRM license expired – request a new license or check expiration settings")

                    else -> Log.w(TAG, "Unhandled DRM error code: ${error.errorCodeName}")
                }
            }

            val info = PlaybackFailureInfo.from(error) {
                context.getString(R.string.error_video_not_available)
            }
            // The real reason always lands in logcat for the integrator. Viewers of a blocked
            // stream (HTTP 403: geo-blocking, hotlink protection, expired token — not told apart)
            // only ever get the generic copy.
            Log.w(
                TAG,
                "playback failure http=${info.httpStatus} sinkhole=${info.sinkholeAddress} ${info.rawMessage}",
            )
            // SDK surfaces get the structured report first so they can settle their own state
            // before the public callback paints the built-in error banner.
            playbackFailureInfoListener?.invoke(info)
            playerStateListener?.onPlayerError(info.userMessage)
        }
    }

    override var seekThumbnail: SeekThumbnail? = null

    override var playerSettings: PlayerSettings? = null

    // Carries the Bunny receiver's LoadRequest shape (metadata, sideloaded
    // captions, DRM + theming customData); refreshed on every playVideo.
    private val castMediaItemConverter = BunnyMediaItemConverter()

    // Mirrors the SessionAvailabilityListener callbacks: true while a cast
    // session is connected, regardless of which player is current.
    private var castSessionAvailable = false

    private fun isCasting(): Boolean =
        castPlayer != null && currentPlayer === castPlayer

    private var preferredAudioTrack: AudioTrackInfo? = null

    /**
     * Mirror the locally selected audio/caption tracks to the receiver.
     * Safe to call repeatedly — the bridge skips tracks that are already
     * active.
     */
    private fun reconcileCastSelections() {
        preferredAudioTrack?.let {
            CastTrackBridge.selectAudioTrack(it.languageCode, it.label)
        }
        if (subtitlesEnabled) {
            selectedSubtitle?.let { CastTrackBridge.selectTextTrack(it.language) }
        }
        // The LoadRequest always starts the receiver at 1x — carry a
        // pre-cast local speed over. CastPlayer mirrors the receiver's
        // rate into its playbackParameters, so this converges.
        val localSpeed = localPlayer?.playbackParameters?.speed ?: 1f
        val remoteSpeed = castPlayer?.playbackParameters?.speed ?: 1f
        if (localSpeed > 0f && localSpeed != remoteSpeed) {
            castPlayer?.setPlaybackSpeed(localSpeed)
        }
    }

    init {
        // Only initialize Cast if it's available
        if (AppCastContext.isAvailable()) {
            try {
                castPlayer = CastPlayer(AppCastContext.get(), castMediaItemConverter).also {
                    it.addListener(playerListener)
                    it.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {
                            Log.d(TAG, "onCastSessionAvailable")
                            castSessionAvailable = true
                            switchCurrentPlayer(it)
                        }

                        override fun onCastSessionUnavailable() {
                            Log.d(TAG, "onCastSessionUnavailable")
                            castSessionAvailable = false
                            localPlayer?.let { local -> switchCurrentPlayer(local) }
                        }
                    })
                }

                AppCastContext.get().addCastStateListener {
                    Log.d(TAG, "onCastStateChanged: $it")
                    when(it) {
                        CastState.CONNECTED -> {}
                        CastState.CONNECTING -> {}
                        CastState.NOT_CONNECTED -> {}
                        CastState.NO_DEVICES_AVAILABLE -> {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize Cast player: ${e.message}")
                castPlayer = null
            }
        } else {
            Log.d(TAG, "Cast framework not available, continuing without Cast support")
            castPlayer = null
        }
    }

    // Resume position methods
    override fun enableResumePosition(config: ResumeConfig) {
        positionManager = DefaultPlaybackPositionManager(context, config)

        // Start auto-save if enabled
        if (config.enableAutoSave) {
            startAutoSavePosition(config.saveInterval)
        }
    }
    override fun disableResumePosition() {
        positionManager = null
        resumePositionListener = null
        stopAutoSavePosition()
    }

    override fun clearSavedPosition(videoId: String) {
        positionManager?.let { manager ->
            coroutineScope.launch {
                manager.clearPosition(videoId)
            }
        }
    }
    override fun setResumePositionListener(listener: ResumePositionListener) {
        resumePositionListener = listener
    }
    override fun clearAllSavedPositions() {
        positionManager?.let { manager ->
            coroutineScope.launch {
                manager.clearAllPositions()
            }
        }
    }

    override fun getAllSavedPositions(callback: (List<PlaybackPosition>) -> Unit) {
        positionManager?.let { manager ->
            coroutineScope.launch {
                val positions = manager.getAllPositions()
                withContext(Dispatchers.Main) {
                    callback(positions)
                }
            }
        } ?: callback(emptyList())
    }

    override fun exportPositions(callback: (String) -> Unit) {
        positionManager?.let { manager ->
            coroutineScope.launch {
                val jsonData = manager.exportPositions()
                withContext(Dispatchers.Main) {
                    callback(jsonData)
                }
            }
        } ?: callback("[]")
    }

    override fun importPositions(jsonData: String, callback: (Boolean) -> Unit) {
        positionManager?.let { manager ->
            coroutineScope.launch {
                val success = manager.importPositions(jsonData)
                withContext(Dispatchers.Main) {
                    callback(success)
                }
            }
        } ?: callback(false)
    }

    override fun cleanupExpiredPositions() {
        positionManager?.let { manager ->
            coroutineScope.launch {
                manager.cleanupExpiredPositions()
            }
        }
    }

    private fun startAutoSavePosition(interval: Long = autoSaveInterval) {
        stopAutoSavePosition()

        autoSaveJob = coroutineScope.launch(Dispatchers.Main) { // <- Use Main dispatcher
            while (isActive) {
                delay(interval)
                if (isPlaying()) { // Now safely on main thread
                    // Move save operation to background
                    launch(Dispatchers.IO) {
                        saveCurrentPosition()
                    }
                }
            }
        }
        Log.d(TAG, "Auto-save position started with interval: ${interval}ms")
        startCmcdSnapshotUpdates()
    }

    private fun stopAutoSavePosition() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        stopCmcdSnapshotUpdates()
        Log.d(TAG, "Auto-save position stopped")
    }

    /** Sets the CMCD `st` for subsequent playback (VOD -> v, live -> l, DVR live -> e). Internal. */
    internal fun setCmcdStreamType(streamType: CmcdStreamType) {
        cmcdStreamType = streamType
        // The cast receiver needs the same live/VOD distinction: a live
        // LoadRequest must carry STREAM_TYPE_LIVE for live UI on the TV.
        castMediaItemConverter.isLiveStream = streamType != CmcdStreamType.VOD
        Log.d(TAG, "CMCD streamType=$streamType (query)")
    }

    /** Refreshes the CMCD buffer snapshot on the main thread so loader threads can read it safely. */
    private fun startCmcdSnapshotUpdates() {
        stopCmcdSnapshotUpdates()
        cmcdSnapshotJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                currentPlayer?.let { player ->
                    cmcdBufferLengthMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                    cmcdBufferStarved = player.playbackState == Player.STATE_BUFFERING
                }
                delay(cmcdSnapshotInterval)
            }
        }
    }

    private fun stopCmcdSnapshotUpdates() {
        cmcdSnapshotJob?.cancel()
        cmcdSnapshotJob = null
    }

    /** Wraps [http] so every media request gets a CMCD v2 `?CMCD=` query param. */
    @SuppressLint("UnsafeOptInUsageError")
    private fun buildCmcdDataSourceFactory(
        http: DataSource.Factory,
        contentId: String,
        streamingFormat: String,
    ): DataSource.Factory {
        val session = CmcdSession(
            contentId = contentId,
            streamType = cmcdStreamType,
            streamingFormat = streamingFormat,
            snapshotProvider = { CmcdPlayerSnapshot(cmcdBufferLengthMs, cmcdBufferStarved) },
        )
        return ResolvingDataSource.Factory(http, CmcdResolver(session))
    }

    private fun checkForSavedPosition(videoId: String) {
        positionManager?.let { manager ->
            coroutineScope.launch {
                val savedPosition = manager.getPosition(videoId)
                if (savedPosition != null) {
                    resumePositionListener?.onResumePositionAvailable(videoId, savedPosition)
                }
            }
        }
    }
    private fun saveCurrentPosition() {
        currentVideoId?.let { videoId ->
            positionManager?.let { manager ->
                coroutineScope.launch {
                    // Get position on main thread
                    val position = withContext(Dispatchers.Main) {
                        getCurrentPosition()
                    }
                    val duration = withContext(Dispatchers.Main) {
                        getDuration()
                    }

                    // Save on background thread
                    withContext(Dispatchers.IO) {
                        if (position > 0 && duration > 0) {
                            manager.savePosition(videoId, position, duration)
                            val savedPosition = PlaybackPosition(
                                videoId = videoId,
                                position = position,
                                duration = duration,
                                timestamp = System.currentTimeMillis(),
                                watchPercentage = position.toFloat() / duration.toFloat()
                            )

                            // Notify listener on main thread
                            withContext(Dispatchers.Main) {
                                resumePositionListener?.onResumePositionSaved(videoId, savedPosition)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add configuration method
    override fun setPlaybackSpeedConfig(config: PlaybackSpeedConfig) {
        this.speedConfig = config
        if (config.rememberLastSpeed) {
            loadSavedSpeed()
        }
    }

    override fun loadSavedSpeed() {
        if (speedConfig.rememberLastSpeed && currentPlayer != null) {
            val savedSpeed = speedPreferences.getLastSpeed(speedConfig.defaultSpeed)
            Log.d(TAG, "Loading saved speed: $savedSpeed")
            if (savedSpeed != speedConfig.defaultSpeed) {
                currentPlayer?.setPlaybackSpeed(savedSpeed)
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun playVideo(
        playerView: PlayerView,
        video: Video,
        retentionData: Map<Int, Int>,
        playerSettings: PlayerSettings,
        licenseBaseApi: String,
        token: String?,
        expires: Long?,
    ) {
        Log.d(TAG, "playVideo(video=$video, retentionData=$retentionData, playerSettings=$playerSettings)")

        // Save position of previous video before switching
        saveCurrentPosition()

        this.playerSettings = playerSettings
        currentVideo = video
        currentVideoId = video.id
        // Per-video state: the local preference dies with the fresh track
        // selector below, so the cast-side preference must not outlive it
        // (it would re-apply video A's language on whatever plays next).
        preferredAudioTrack = null

        currentLibraryId = video.videoLibraryId
        resumePosition = playerSettings.resumePosition

        // Set up TransferListener for debugging
        val transferListener = object : TransferListener {
            override fun onTransferInitializing(
                source: DataSource,
                dataSpec: DataSpec,
                isNetwork: Boolean
            ) {

            }

            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                Log.d(TAG, "HTTP ▶️ ${dataSpec.uri}")
            }

            override fun onBytesTransferred(
                source: DataSource,
                dataSpec: DataSpec,
                isNetwork: Boolean,
                bytesTransferred: Int
            ) {

            }

            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                Log.d(TAG, "HTTP ✅ ${dataSpec.uri}")
            }
        }

        // Create HTTP data source factory with headers
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Referer" to BunnyCdn.REFERER))
            .setUserAgent(Util.getUserAgent(context, "BunnyStreamPlayer"))
            .setTransferListener(transferListener)

        // Create media source factory without setDrmSessionManagerProvider.
        // CMCD (Common Media Client Data, CTA-5004 **v2**) attaches client playback telemetry — session
        // id, buffer length, stream/object type, startup + buffer-starvation flags — to every media
        // request so the CDN receives it. media3's built-in CmcdConfiguration is v1-only, so we inject
        // it ourselves: buildCmcdDataSourceFactory wraps the HTTP data source with a ResolvingDataSource
        // that appends a v2 `?CMCD=` query parameter per request.
        // Manifest container resolved from the URL: HLS (.m3u8 / Bunny's extension-less URLs) or
        // DASH (.mpd). ExoPlayer plays both from the same engine (the media3-exoplayer-dash module
        // supplies the DashMediaSource); DefaultMediaSourceFactory picks HLS vs DASH from the
        // MediaItem MIME below. Drives the CMCD `sf` too.
        val manifestFormat = ManifestFormat.fromUrl(playerSettings.videoUrl)
        val cmcdContentId = video.id
        val cmcdDataSourceFactory =
            buildCmcdDataSourceFactory(httpFactory, cmcdContentId, manifestFormat.cmcdSf)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cmcdDataSourceFactory)

        // Set up subtitle tracks if available
        val subtitleConfigs = video.captions.map { cap ->
            val subUri = Uri.parse("${playerSettings.captionsPath}${cap.languageCode}.vtt?ver=1")
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(cap.languageCode)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        // The Widevine license URL, built from the host the caller passed in — never from a
        // process-wide default, which is what used to crash create()-only apps. Needed even when
        // local playback skips DRM: the cast receiver fetches the license itself, so the URL
        // rides to the TV in the LoadRequest customData. The token/expires pair rides along
        // because the receiver has no Referer header to authenticate with.
        val drmLicenseUri = buildString {
            append("$licenseBaseApi/WidevineLicense/")
            append("${video.videoLibraryId}/${video.id}?contentId=${video.id}")
            // Both or neither: expires is part of the token signature, so a
            // URL with only one of them can never validate.
            if (token != null && expires != null) {
                append("&token=$token&expires=$expires")
            }
        }

        // Title + artwork shown by the Chromecast receiver and the cast/notification UI (the
        // Cast MediaItemConverter reads MediaMetadata). Applies to both VOD and live.
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(video.title?.takeIf { it.isNotBlank() })
            .apply {
                playerSettings.thumbnailUrl
                    .takeIf { it.isNotBlank() }
                    ?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        // Explicit MIME so DefaultMediaSourceFactory builds the right source (HLS vs DASH) even when
        // the URL has no clean extension — Bunny's live/fallback URLs default to HLS.
        val manifestMimeType = when (manifestFormat) {
            ManifestFormat.DASH -> MimeTypes.APPLICATION_MPD
            ManifestFormat.HLS -> MimeTypes.APPLICATION_M3U8
        }
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(playerSettings.videoUrl)
            .setMimeType(manifestMimeType)
            .setMediaMetadata(mediaMetadata)
            .setSubtitleConfigurations(subtitleConfigs)

        // Refresh the cast LoadRequest customData for this video (theming +
        // DRM); the converter sends it with every cast load.
        castMediaItemConverter.castCustomData = CastCustomData.toJson(
            CastCustomData.build(
                playerSettings,
                drmLicenseUri.takeIf { playerSettings.drmEnabled },
            )
        )

        // MediaItem id (used by Cast/analytics). The CMCD content id (`cid`) is set separately by
        // buildCmcdDataSourceFactory from the same guid.
        video.id.takeIf { it.isNotBlank() }?.let { mediaItemBuilder.setMediaId(it) }

        if (playerSettings.drmEnabled) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmLicenseUri)
                    .setLicenseRequestHeaders(mapOf("Referer" to BunnyCdn.REFERER))
                    .setMultiSession(true)
                    .setForceDefaultLicenseUri(true)
                    .build()
            )
        }

        playerSettings.vastTagUrl.toUri()?.let { vastUri ->
            mediaItemBuilder.setAdsConfiguration(
                MediaItem.AdsConfiguration.Builder(vastUri).build()
            )
        }

        // Create new ExoPlayer and assign to PlayerView
        trackSelector = DefaultTrackSelector(context)
        trackSelector?.parameters = trackSelector!!.buildUponParameters()
            .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
            .clearVideoSizeConstraints()
            .build()

        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        // Honour the host's choice of chrome. This used to be pinned to `true`, which quietly
        // re-enabled the controls on every source change for an app driving its own UI.
        playerView.useController = (playerView as? BunnyPlayerView)?.controlsEnabled ?: true
        playerView.keepScreenOn = true
        Log.d(TAG, "PlayerView attached: ${playerView.isAttachedToWindow}, size: ${playerView.width}x${playerView.height}")

        // Release the previous player before building its replacement. Every ExoPlayer holds a
        // hardware decoder until released, and orphaned players are not collected out of the
        // codec — the live surface re-issues playback on every URL flip (trailer -> live ->
        // recording), which used to leak one player per flip and could end a long session with
        // playback failing on "no codec available".
        // Detach before releasing. A released player left attached takes the view's surface down
        // with it, and the replacement then decodes into nothing: playback runs to completion with
        // the picture black and no error, because nothing actually failed. Only the second and
        // later playback in a session hit it, which is why every single-play test passed.
        playerView.player = null
        localPlayer?.release()

        localPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector!!)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also {
                it.addListener(playerListener)
                it.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && speedConfig.rememberLastSpeed) {
                            loadSavedSpeed()
                        }
                    }
                })
                it.addAnalyticsListener(object : AnalyticsListener {
                    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                        Log.d(TAG, "✅ First frame rendered after ${renderTimeMs}ms")
                    }

                    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        Log.d(TAG, "🎥 Video decoder initialized: $decoderName, took ${initializationDurationMs}ms")
                    }

                    override fun onDrmSessionAcquired(eventTime: AnalyticsListener.EventTime) {
                        Log.d(TAG, "🔐 DRM session acquired")
                    }

                    override fun onDrmKeysLoaded(eventTime: AnalyticsListener.EventTime) {
                        Log.d(TAG, "✅ DRM keys loaded successfully")
                    }

                    override fun onDrmSessionManagerError(eventTime: AnalyticsListener.EventTime, error: Exception) {
                        Log.e(TAG, "❌ DRM session manager error", error)
                        // A failed DRM session does not always surface as a PlaybackException, so
                        // without this the picture simply stops after the frames decoded before
                        // the licence was needed: a black view, no error, nothing to report. Say
                        // it out loud on the same channel every other playback failure uses.
                        playerStateListener?.onPlayerError(
                            "DRM licence failed: ${error.message ?: error::class.java.simpleName}"
                        )
                    }

                    override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) {
                        Log.d(TAG, "🎚 Tracks changed:")
                        for (group in tracks.groups) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                Log.d(TAG, "  - Track: ${format.sampleMimeType}, id=${format.id}, lang=${format.language}, selected=${group.isTrackSelected(i)}")
                            }
                        }
                    }
                })
            }

        // While a cast session is connected, new playback must go to the
        // receiver: assigning the local player here would silently drop out
        // of cast (the session stays up, so SessionAvailabilityListener
        // never re-fires) — the phone would play the new video while the TV
        // keeps the old one. The live surface re-issues playback on every
        // URL flip (trailer -> live -> recording), which made this fatal
        // for live casting. The fresh local player stays idle as the
        // handback target for when the session ends.
        currentPlayer = if (castSessionAvailable && castPlayer != null) {
            castPlayer
        } else {
            localPlayer
        }
        playerView.player = currentPlayer
        playerView.keepScreenOn = true
        playerStateListener?.onPlayerTypeChanged(
            currentPlayer!!,
            if (currentPlayer === castPlayer) PlayerType.CAST_PLAYER else PlayerType.DEFAULT_PLAYER,
        )

        // Prepare and play
        val mediaItem = mediaItemBuilder.build()
        this.mediaItem = mediaItem
        currentPlayer!!.setMediaItem(mediaItem)
        currentPlayer!!.prepare()

        // Check for saved position before starting playback
        checkForSavedPosition(video.id)
        currentVideoId?.let { videoId ->
            checkForSavedPosition(videoId)
        }

        // Start playback
        currentPlayer!!.playWhenReady = true

        if (speedConfig.rememberLastSpeed) {
            loadSavedSpeed()
        }

        if (resumePosition > 0) {
            currentPlayer!!.seekTo(resumePosition)
        }


        startAutoSavePosition()
        // Init seek thumbnails and metadata
        initSeekThumbnailPreview(video, playerSettings.seekPath)

        moments = video.moments.map {
            Moment(it.label, it.timestampSeconds?.seconds?.inWholeMilliseconds ?: 0)
        }

        chapters = video.chapters.map {
            Chapter(
                it.startSeconds?.seconds?.inWholeMilliseconds ?: 0,
                it.endSeconds?.seconds?.inWholeMilliseconds ?: 0,
                it.title
            )
        }

        if (playerSettings.showHeatmap) {
            this.retentionData = retentionData.map { (ms, pct) ->
                RetentionGraphEntry(ms, pct)
            }
        }
    }

    override fun setResumePosition(position: Long) {
        resumePosition = position
    }

    override fun skipForward() {
        currentPlayer?.let {
            it.seekTo(it.currentPosition + SEEK_SKIP_MILLIS)
        }
    }

    override fun replay() {
        currentPlayer?.let {
            val current = it.currentPosition
            val target = if(current > SEEK_SKIP_MILLIS) {
                current - SEEK_SKIP_MILLIS
            } else {
                0
            }
            it.seekTo(target)
        }
    }

    private fun initSeekThumbnailPreview(video: Video, seekPath: String) {
        val thumbnailPreviewsList: MutableList<String> = mutableListOf()
        val numberOfPreviews = round(video.thumbnailCount.toFloat() / THUMBNAILS_PER_IMAGE).toInt()
        var i = 0
        do {
            thumbnailPreviewsList.add("$seekPath/_${i}.jpg")
            i++
        } while (i < numberOfPreviews)

        seekThumbnail = SeekThumbnail(
            seekThumbnailUrls = thumbnailPreviewsList,
            frameDurationPerThumbnail = ceil((video.lengthSeconds.toFloat() * 1000) / video.thumbnailCount.coerceAtLeast(1)).toInt(),
            totalThumbnailCount = video.thumbnailCount,
            thumbnailsPerImage = THUMBNAILS_PER_IMAGE,
        )
    }

    override fun setSpeed(speed: Float) {
        Log.d(TAG, "Setting speed to: $speed")
        // One call covers local and cast: media3's CastPlayer supports
        // COMMAND_SET_SPEED_AND_PITCH natively (it sends the standard
        // SET_PLAYBACK_RATE, clamped to the receiver's 0.5–2 range, and
        // mirrors the applied rate back into playbackParameters).
        currentPlayer?.setPlaybackSpeed(speed)
        // Keep the idle local player in sync while casting so playback
        // resumes at the same speed when the session ends.
        if (isCasting()) {
            localPlayer?.setPlaybackSpeed(speed)
        }

        if (speedConfig.rememberLastSpeed) {
            speedPreferences.saveLastSpeed(speed)
            Log.d(TAG, "Saved speed: $speed")
        }

        // Notify listener for UI updates
        playerStateListener?.onPlaybackSpeedChanged(speed)
    }

    override fun getSpeed(): Float {
        return currentPlayer?.playbackParameters?.speed ?: 1F
    }

    override fun getSubtitles(): Subtitles {
        return Subtitles(
            currentVideo?.captions?.map {
                SubtitleInfo(it.label.orEmpty(), it.languageCode.orEmpty())
            } ?: listOf(),
            if(subtitlesEnabled) {
                selectedSubtitle
            } else {
                null
            }
        )
    }

    override fun selectSubtitle(subtitleInfo: SubtitleInfo) {
        Log.d(TAG, "selectSubtitle: $subtitleInfo")
        subtitlesEnabled = subtitleInfo.language != ""

        val lang: String?
        if(subtitlesEnabled){
            selectedSubtitle = subtitleInfo
            lang = subtitleInfo.language
        } else {
            selectedSubtitle = null
            lang = null
        }

        selectSubtitleTrack(lang)
    }

    override fun setSubtitlesEnabled(enabled: Boolean) {
        subtitlesEnabled = enabled

        if(enabled) {
            if(selectedSubtitle != null) {
                selectSubtitle(selectedSubtitle!!)
            } else {
                val caption = currentVideo?.captions?.getOrNull(0)
                if (caption != null) {
                    selectedSubtitle = SubtitleInfo(caption.label.orEmpty(), caption.languageCode.orEmpty())
                    selectSubtitle(selectedSubtitle!!)
                }
            }
        } else {
            selectSubtitleTrack(null)
        }
    }

    override fun areSubtitlesEnabled(): Boolean {
        return subtitlesEnabled
    }

    override fun getVideoQualityOptions(): VideoQualityOptions? {
        // While casting, quality (ABR) is decided by the receiver — the
        // local track selector would silently do nothing, so don't offer
        // the menu at all.
        if (isCasting()) return null
        return getAvailableVideoQualityOptions()
    }

    override fun getAudioTrackOptions(): AudioTrackInfoOptions? {
        return getAvailableAudioTrackOptions()
    }

    override fun selectQuality(quality: VideoQuality) {
        Log.d(TAG, "selectQuality: $quality")
        trackSelector?.let {
            val params = it.buildUponParameters().setMaxVideoSize(quality.width, quality.height)
            it.setParameters(params)
        }
    }

    override fun selectAudioTrack(audioTrackInfo: AudioTrackInfo) {
        Log.d(TAG, "selectAudioTrack: $audioTrackInfo")
        preferredAudioTrack = audioTrackInfo

        // Always record the preference locally so playback continues in the
        // chosen language when a cast session ends.
        trackSelector?.let {
            val params = it.buildUponParameters().setPreferredAudioLanguage(audioTrackInfo.languageCode)
            it.setParameters(params)
        }

        if (isCasting()) {
            // The local track selector has no effect on the receiver; switch
            // the receiver track via the standard media command.
            CastTrackBridge.selectAudioTrack(audioTrackInfo.languageCode, audioTrackInfo.label)
        }
    }

    override fun getPlaybackSpeeds(): List<Float> {
        return speedConfig.allowedSpeeds
            ?: playerSettings?.playbackSpeeds
            ?: PlaybackSpeedManager.DEFAULT_SPEEDS
    }

    override fun release() {
        saveCurrentPosition()
        stopAutoSavePosition()
        currentPlayer?.stop()

        localPlayer?.release()
        localPlayer = null

        castPlayer?.release()
        castPlayer = null

        instance = null
    }

    override fun play() {
        val current = currentPlayer?.currentPosition ?: 0
        val duration = currentPlayer?.duration ?: 0
        // Only replay from the top when we actually know where the top is. An idle player reports
        // C.TIME_UNSET (a large negative), which any position compares "past" — that used to seek a
        // freshly errored player to 0 for no reason.
        //
        // Live playback reports C.TIME_UNSET too, so this deliberately changes it as well: pressing
        // play after a pause used to jump back to the start of the live window instead of resuming
        // where the viewer left off. Replaying a live edge from "the top" was never meaningful.
        if (duration != C.TIME_UNSET && current >= duration) {
            currentPlayer?.seekTo(0)
        }
        // An error leaves media3 in STATE_IDLE, where playWhenReady is remembered but never acted
        // on — play() alone is a silent no-op and the viewer's tap does nothing. Re-preparing is
        // what actually retries the source, so the network coming back (or the recording finally
        // being published) turns into playback again.
        currentPlayer?.let { player ->
            if (player.playbackState == Player.STATE_IDLE && player.playerError != null) {
                player.prepare()
            }
        }
        currentPlayer?.play()

        // Start auto-save when playing starts
        positionManager?.let {
            startAutoSavePosition()
        }
    }

    override fun pause(autoPaused: Boolean) {
        this.autoPaused = autoPaused

        // Save position on background thread, but get current position on main thread
        coroutineScope.launch {
            val position = getCurrentPosition() // Already on main thread
            val duration = getDuration() // Already on main thread

            // Save on background thread
            launch(Dispatchers.IO) {
                currentVideoId?.let { videoId ->
                    positionManager?.savePosition(videoId, position, duration)
                }
            }
        }

        stopAutoSavePosition()
        currentPlayer?.pause()
    }

    override fun stop() {
        saveCurrentPosition()
        stopAutoSavePosition()
        currentPlayer?.stop()
    }

    override fun seekTo(positionMs: Long) {
        currentPlayer?.seekTo(positionMs)
        // Save new position after seek
        coroutineScope.launch {
            delay(1000) // Wait a bit for seek to complete
            saveCurrentPosition()
        }
    }
    override fun setVolume(volume: Float) {
        currentPlayer?.volume = volume
    }

    override fun getVolume(): Float = currentPlayer?.volume ?: 0f

    override fun isMuted(): Boolean {
        return currentPlayer?.volume == 0F
    }

    override fun mute() {
        currentPlayer?.volume = 0F
        playerStateListener?.onMutedChanged(true)
    }

    override fun unmute() {
        currentPlayer?.volume = 1F
        playerStateListener?.onMutedChanged(false)
    }

    override fun isPlaying(): Boolean = currentPlayer?.isPlaying ?: false

    override fun getDuration(): Long = currentPlayer?.duration ?: 0L

    override fun getCurrentPosition(): Long = currentPlayer?.currentPosition ?: 0L

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun switchCurrentPlayer(newPlayer: Player) {
        if (this.currentPlayer === newPlayer) {
            return
        }

        if(newPlayer === castPlayer) {
            playerStateListener?.onPlayerTypeChanged(newPlayer, PlayerType.CAST_PLAYER)
        } else {
            playerStateListener?.onPlayerTypeChanged(newPlayer, PlayerType.DEFAULT_PLAYER)
        }

        currentPlayer?.removeListener(playerListener)

        var newPlaybackPositionMs = C.TIME_UNSET
        var newPlayWhenReady = false
        val previousPlayer: Player? = currentPlayer

        if (previousPlayer != null) {
            val playbackState = previousPlayer.playbackState

            if (playbackState != Player.STATE_ENDED) {
                newPlaybackPositionMs = previousPlayer.currentPosition
                newPlayWhenReady = previousPlayer.playWhenReady
            }

            previousPlayer.removeListener(playerListener)
            previousPlayer.stop()
            previousPlayer.clearMediaItems()
        }

        currentPlayer = newPlayer
        currentPlayer?.addListener(playerListener)

        mediaItem?.let {
            newPlayer.setMediaItem(it, newPlaybackPositionMs)
        }

        newPlayer.playWhenReady = newPlayWhenReady
        newPlayer.prepare()
    }

    private fun getAvailableVideoQualityOptions(): VideoQualityOptions? {
        val trackGroups = currentPlayer?.currentTracks?.groups ?: return null

        val options = mutableSetOf<VideoQuality>() // Use Set to avoid duplicates

        trackGroups.forEach {
            for (trackIndex in 0 until it.length) {
                if (it.isTrackSupported(trackIndex)) {
                    val format = it.getTrackFormat(trackIndex)
                    if (format.width != Format.NO_VALUE || format.height != Format.NO_VALUE) {
                        options.add(VideoQuality(format.width, format.height))
                    }
                }
            }
        }

        // Default option (resolution selected automatically by player)
        var selectedOption = VideoQuality(Int.MAX_VALUE, Int.MAX_VALUE)

        val optionsList = options.sortedByDescending { it.width + it.height }.toMutableList()
        optionsList.add(0, selectedOption)

        trackSelector?.parameters?.let {
            if (it.maxVideoWidth != Int.MAX_VALUE && it.maxVideoHeight != Int.MAX_VALUE) {
                selectedOption = VideoQuality(it.maxVideoWidth, it.maxVideoHeight)
            }
        }

        return VideoQualityOptions(optionsList, selectedOption)
    }

    private fun getAvailableAudioTrackOptions(): AudioTrackInfoOptions? {
        val audioTracks: MutableList<AudioTrackInfo> = mutableListOf()
        var selectedTrack: AudioTrackInfo? = null
        val tracks: Tracks = currentPlayer?.currentTracks ?: return null
        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(i)
                    val isSelected = trackGroup.isTrackSelected(i)

                    val track = AudioTrackInfo(
                        index = i,
                        trackId = format.id,
                        label = format.label,
                        languageCode = format.language,
                    )

                    audioTracks.add(track)
                    if(isSelected) {
                        selectedTrack = track
                    }
                }
            }
        }

        return AudioTrackInfoOptions(audioTracks, selectedTrack)
    }

    private fun selectSubtitleTrack(lang: String?) {
        if (isCasting()) {
            // Track selection parameters don't reach the receiver; switch
            // (or clear) the receiver caption track directly.
            CastTrackBridge.selectTextTrack(lang)
        }
        // Apply to the local player too (even while casting) so the
        // selection carries over when the cast session ends.
        val targets = listOfNotNull(currentPlayer, localPlayer.takeIf { isCasting() })
        for (player in targets.distinct()) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED.inv())
                .setPreferredTextLanguage(lang)
                .build()
        }
    }
}