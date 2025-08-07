package net.bunnystream.bunnystreamplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bunny.api.BunnyStreamApi
import net.bunny.api.playback.DefaultPlaybackPositionManager
import net.bunny.api.playback.PlaybackPosition
import net.bunny.api.playback.PlaybackPositionManager
import net.bunny.api.playback.ResumeConfig
import net.bunny.api.playback.ResumePositionListener
import net.bunny.api.settings.PlaybackSpeedManager
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.settings.toUri
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
import org.openapitools.client.models.VideoModel
import kotlin.math.ceil
import kotlin.math.round
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@SuppressLint("UnsafeOptInUsageError")
class DefaultBunnyPlayer private constructor(private val context: Context) : BunnyPlayer {

    companion object {
        private const val TAG = "DefaultBunnyPlayer"

        private const val SEEK_SKIP_MILLIS = 10 * 1000
        private const val THUMBNAILS_PER_IMAGE = 36

        @Volatile
        private var instance: BunnyPlayer? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DefaultBunnyPlayer(context.applicationContext).also { instance = it }
            }
    }

    // Speed Variables
    private var speedConfig = PlaybackSpeedConfig()
    private val speedPreferences = PlaybackSpeedPreferences(context)
    private val speedManager = PlaybackSpeedManager()

    // Player Position Variables
    private var currentLibraryId: Long? = null
    private var resumePosition: Long = 0L
    private var progressSaveJob: Job? = null

    private var localPlayer: Player? = null
    private val castContext = AppCastContext.get()
    private var castPlayer: Player? = null
    override var currentPlayer: Player? = null

    private var currentVideo: VideoModel? = null
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

    private var mediaItem: MediaItem? = null
    private var mediaItemBuilder: MediaItem.Builder? = null

    private var trackSelector: DefaultTrackSelector? = null

    private val httpDataSourceFactory: HttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory: DataSource.Factory = DataSource.Factory {
        val dataSource: HttpDataSource = httpDataSourceFactory.createDataSource()
        // Needed if "Block Direct Url File Access" is enabled on Dashboard
        dataSource.setRequestProperty("Referer", "https://iframe.mediadelivery.net/")
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

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            Log.d(TAG, "onTracksChanged tracks: $tracks")
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

            playerStateListener?.onPlayerError("${error.errorCodeName}: ${error.message}")
        }
    }

    override var seekThumbnail: SeekThumbnail? = null

    override var playerSettings: PlayerSettings? = null

    init {
        castPlayer = CastPlayer(castContext).also {
            it.addListener(playerListener)
            it.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    Log.d(TAG, "onCastSessionAvailable")
                    switchCurrentPlayer(it)
                }

                override fun onCastSessionUnavailable() {
                    Log.d(TAG, "onCastSessionUnavailable")
                    switchCurrentPlayer(localPlayer!!)
                }
            })
        }

        castContext.addCastStateListener {
            Log.d(TAG, "onCastStateChanged: $it")
            when(it) {
                CastState.CONNECTED -> {}
                CastState.CONNECTING -> {}
                CastState.NOT_CONNECTED -> {}
                CastState.NO_DEVICES_AVAILABLE -> {}
            }
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

        autoSaveJob = coroutineScope.launch {
            while (isActive) {
                delay(interval)
                if (isPlaying()) {
                    saveCurrentPosition()
                }
            }
        }
        Log.d(TAG, "Auto-save position started with interval: ${interval}ms")
    }

    private fun stopAutoSavePosition() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        Log.d(TAG, "Auto-save position stopped")
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
                    val position = getCurrentPosition()
                    val duration = getDuration()
                    if (position > 0 && duration > 0) {
                        manager.savePosition(videoId, position, duration)
                        val savedPosition = PlaybackPosition(
                            videoId = videoId,
                            position = position,
                            duration = duration,
                            timestamp = System.currentTimeMillis(),
                            watchPercentage = position.toFloat() / duration.toFloat()
                        )
                        resumePositionListener?.onResumePositionSaved(videoId, savedPosition)
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
        video: VideoModel,
        retentionData: Map<Int, Int>,
        playerSettings: PlayerSettings
    ) {
        Log.d(TAG, "playVideo(video=$video, retentionData=$retentionData, playerSettings=$playerSettings)")

        // Save position of previous video before switching
        saveCurrentPosition()

        this.playerSettings = playerSettings
        currentVideo = video
        currentVideoId = video.guid

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
            .setDefaultRequestProperties(mapOf("Referer" to "https://iframe.mediadelivery.net"))
            .setUserAgent(Util.getUserAgent(context, "BunnyStreamPlayer"))
            .setTransferListener(transferListener)

        // Create media source factory without setDrmSessionManagerProvider
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpFactory)

        // Set up subtitle tracks if available
        val subtitleConfigs = video.captions?.map { cap ->
            val subUri = Uri.parse("${playerSettings.captionsPath}${cap.srclang}.vtt?ver=1")
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(cap.srclang)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        } ?: emptyList()

        // Build MediaItem with DRM config (CENC)
        val drmLicenseUri = "${BunnyStreamApi.baseApi}/WidevineLicense/" +
                "${video.videoLibraryId}/${video.guid}?contentId=${video.guid}"

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(playerSettings.videoUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setSubtitleConfigurations(subtitleConfigs)

        if (playerSettings.drmEnabled) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmLicenseUri)
                    .setLicenseRequestHeaders(mapOf("Referer" to "https://iframe.mediadelivery.net"))
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
        playerView.useController = true
        playerView.keepScreenOn = true
        Log.d(TAG, "PlayerView attached: ${playerView.isAttachedToWindow}, size: ${playerView.width}x${playerView.height}")

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

        currentPlayer = localPlayer
        playerView.player = currentPlayer
        playerView.keepScreenOn = true

        // Prepare and play
        val mediaItem = mediaItemBuilder.build()
        this.mediaItem = mediaItem
        currentPlayer!!.setMediaItem(mediaItem)
        currentPlayer!!.prepare()

        // Check for saved position before starting playback
        checkForSavedPosition(video.guid ?: "")
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


        startProgressSaving(playerSettings.saveProgressInterval)
        startAutoSavePosition()
        // Init seek thumbnails and metadata
        initSeekThumbnailPreview(video, playerSettings.seekPath)

        moments = video.moments?.map {
            Moment(it.label, it.timestamp?.seconds?.inWholeMilliseconds ?: 0)
        } ?: emptyList()

        chapters = video.chapters?.map {
            Chapter(
                it.start?.seconds?.inWholeMilliseconds ?: 0,
                it.end?.seconds?.inWholeMilliseconds ?: 0,
                it.title
            )
        } ?: emptyList()

        if (playerSettings.showHeatmap) {
            this.retentionData = retentionData.map { (ms, pct) ->
                RetentionGraphEntry(ms, pct)
            }
        }
    }

    override fun setResumePosition(position: Long) {
        resumePosition = position
    }

    override fun saveCurrentProgress() {
        currentVideoId?.let { videoId ->
            currentLibraryId?.let { libraryId ->
                val position = getCurrentPosition()
                if (position > 0) {
                    // Save progress in background
                    GlobalScope.launch {
                        BunnyStreamApi.getInstance().progressRepository
                            .saveProgress(libraryId, videoId, position)
                    }
                }
            }
        }
    }

    override fun clearProgress() {
        currentVideoId?.let { videoId ->
            currentLibraryId?.let { libraryId ->
                GlobalScope.launch {
                    BunnyStreamApi.getInstance().progressRepository
                        .clearProgress(libraryId, videoId)
                }
            }
        }
    }

    private fun startProgressSaving(intervalMs: Long) {
        progressSaveJob?.cancel()
        progressSaveJob = GlobalScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (isPlaying()) {
                    saveCurrentProgress()
                }
            }
        }
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

    private fun initSeekThumbnailPreview(video: VideoModel, seekPath: String) {
        val thumbnailPreviewsList: MutableList<String> = mutableListOf()
        val numberOfPreviews = round((video.thumbnailCount?.toFloat() ?: 0.0F) / THUMBNAILS_PER_IMAGE).toInt()
        var i = 0
        do {
            thumbnailPreviewsList.add("$seekPath/_${i}.jpg")
            i++
        } while (i < numberOfPreviews)

        seekThumbnail = SeekThumbnail(
            seekThumbnailUrls = thumbnailPreviewsList,
            frameDurationPerThumbnail = ceil((((video.length?.toFloat()) ?: 0.0F) * 1000) / (video.thumbnailCount ?: 1)).toInt(),
            totalThumbnailCount = video.thumbnailCount ?: 0,
            thumbnailsPerImage = THUMBNAILS_PER_IMAGE,
        )
    }

    override fun setSpeed(speed: Float) {
        Log.d(TAG, "Setting speed to: $speed")
        currentPlayer?.setPlaybackSpeed(speed)

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
                SubtitleInfo(it.label!!, it.srclang!!)
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
                    selectedSubtitle = SubtitleInfo(caption.label!!, caption.srclang!!)
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

        trackSelector?.let {
            val params = it.buildUponParameters().setPreferredAudioLanguage(audioTrackInfo.languageCode)
            it.setParameters(params)
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
        progressSaveJob?.cancel()
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
        if(current >= duration) {
            currentPlayer?.seekTo(0)
        }
        currentPlayer?.play()

        // Start auto-save when playing starts
        positionManager?.let {
            startAutoSavePosition()
        }
    }
    override fun pause(autoPaused: Boolean) {
        this.autoPaused = autoPaused
        saveCurrentPosition()
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

        val options = mutableListOf<VideoQuality>()

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

        options.sortByDescending { it.width + it.height }
        options.add(0, selectedOption)

        trackSelector?.parameters?.let {
            if (it.maxVideoWidth != Int.MAX_VALUE && it.maxVideoHeight != Int.MAX_VALUE) {
                selectedOption = VideoQuality(it.maxVideoWidth, it.maxVideoHeight)
            }
        }

        return VideoQualityOptions(options, selectedOption)
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
        val trackSelectionParameters = currentPlayer?.trackSelectionParameters ?: return
        currentPlayer?.trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED.inv())
            .setPreferredTextLanguage(lang)
            .build()
    }
}