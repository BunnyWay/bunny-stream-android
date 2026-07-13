package net.bunny.bunnystreamplayer.ui.widget

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.Menu
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import androidx.core.view.isVisible
import androidx.media3.common.Player
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TimeBar
import androidx.mediarouter.app.MediaRouteButton
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import net.bunny.api.BunnyCdn
import com.google.android.gms.cast.framework.CastButtonFactory
import net.bunny.api.settings.capitalizeWords
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.bunnystreamplayer.PlayerStateListener
import net.bunny.bunnystreamplayer.PlayerType
import net.bunny.bunnystreamplayer.common.BunnyPlayer
import net.bunny.bunnystreamplayer.common.I18n
import net.bunny.bunnystreamplayer.model.AudioTrackInfo
import net.bunny.bunnystreamplayer.model.Chapter
import net.bunny.bunnystreamplayer.model.Moment
import net.bunny.bunnystreamplayer.model.PlayerIconSet
import net.bunny.bunnystreamplayer.model.RetentionGraphEntry
import net.bunny.bunnystreamplayer.model.SubtitleInfo
import net.bunny.bunnystreamplayer.model.VideoQuality
import net.bunny.player.R
import kotlin.time.Duration.Companion.seconds
import net.bunny.api.settings.PlaybackSpeedManager
import net.bunny.bunnystreamplayer.config.PlaybackSpeedConfig
import net.bunny.bunnystreamplayer.context.AppCastContext
import kotlin.math.abs

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BunnyPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PlayerView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "BunnyPlayerView"

        /** How often [autoProgressTextColor] re-samples the video while the controller is visible. */
        private const val PROGRESS_COLOR_SAMPLE_INTERVAL_MS = 1_500L

        /** Average sRGB luminance threshold above which we switch to black text. */
        private const val LUMINANCE_THRESHOLD = 0.55

        /** Fraction of the video surface height sampled from the bottom (where the readout sits). */
        private const val SAMPLE_HEIGHT_FRACTION = 0.15f

        /** Cap sample bitmap dimensions so we don't burn CPU on 4K surfaces. */
        private const val MAX_SAMPLE_DIMENSION = 64

        /** How often the live-edge badge re-evaluates the player position. */
        private const val LIVE_EDGE_UPDATE_INTERVAL_MS = 1_000L

        /** System-permitted picture-in-picture aspect-ratio bounds (see PictureInPictureParams). */
        private const val MAX_PIP_ASPECT = 2.39f
        private const val MIN_PIP_ASPECT = 1f / 2.39f

        /**
         * Live offset (ms behind the live edge) below which playback counts as "at the edge".
         * HLS live offset hovers around a few target durations even when fully caught up, so
         * this needs headroom above one segment length.
         */
        private const val LIVE_EDGE_THRESHOLD_MS = 15_000L

        /** Badge tint when playback is at the live edge. */
        private const val LIVE_EDGE_COLOR = 0xFFE53935.toInt()

        /** Badge tint when playback is time-shifted into the DVR window. */
        private const val BEHIND_LIVE_COLOR = 0xFF757575.toInt()
    }

    interface FullscreenListener {
        fun onFullscreenToggleClicked()
    }

    var isFullscreen: Boolean = false
        set(value) {
            field = value
            applyStyle()
        }

    /**
     * Condensed control bar. When `true`, secondary controls (settings, captions, duration readout)
     * are hidden to reduce clutter on small surfaces, leaving the essentials (play, progress, mute,
     * PiP, fullscreen). Driven for live playback by the dashboard's `enableCompactControls` from
     * the live `/play` customization.
     */
    var compactControls: Boolean = false
        set(value) {
            field = value
            updateControlsVisibility()
        }

    private val playStateListener = object : PlayerStateListener {
        override fun onPlayingChanged(isPlaying: Boolean) {
            playPauseButton.state = if (isPlaying) {
                ToggleableImageButton.State.STATE_TOGGLED
            } else {
                ToggleableImageButton.State.STATE_DEFAULT
            }
            errorWrapper.isVisible = false
            if (isPlaying) {
                overlay.removeAllViews()
            }
        }

        override fun onMutedChanged(isMuted: Boolean) {
            muteButton.state = if (isMuted) {
                ToggleableImageButton.State.STATE_TOGGLED
            } else {
                ToggleableImageButton.State.STATE_DEFAULT
            }
        }

        override fun onPlaybackSpeedChanged(speed: Float) {

        }

        override fun onLoadingChanged(isLoading: Boolean) {

        }

        override fun onChaptersUpdated(chapters: List<Chapter>) {
            Log.d(TAG, "onChaptersUpdated: $chapters")
            timeBar?.chapters = chapters
        }

        override fun onMomentsUpdated(moments: List<Moment>) {
            Log.d(TAG, "onMomentsUpdated: $moments")
            timeBar?.moments = moments
        }

        override fun onRetentionGraphUpdated(points: List<RetentionGraphEntry>) {
            timeBar?.retentionGraphData = points
        }

        override fun onPlayerTypeChanged(player: Player, playerType: PlayerType) {
            updatePlayer(player, playerType)
        }

        override fun onPlayerError(message: String) {
            showError(message)
            onPlaybackError?.invoke(message)
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            if (width > 0 && height > 0) lastVideoSize = width to height
            this@BunnyPlayerView.onVideoSizeChanged?.invoke(width, height)
        }
    }

    /** Latest decoded video dimensions — used to size the picture-in-picture window. */
    private var lastVideoSize: Pair<Int, Int>? = null

    var fullscreenListener: FullscreenListener? = null

    /**
     * Invoked with the current video's pixel dimensions (first frame and on change) so the host can
     * size its container to the real aspect ratio — enabling correct display of both 16:9 and 9:16
     * (vertical) content.
     */
    var onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    /**
     * Invoked when the engine reports a playback error (after the built-in error overlay is
     * shown). Lets hosts react — the live player uses it to re-poll the stream status and rebuild
     * playback from the live edge.
     */
    var onPlaybackError: ((message: String) -> Unit)? = null

    var bunnyPlayer: BunnyPlayer? = null
        set(value) {
            field = value
            field?.playerStateListener = playStateListener
            player = bunnyPlayer?.currentPlayer
            playerSettings = value?.playerSettings
            setPlayerControls()
            initTimeBar()
            applyStyle()

            bunnyPlayer?.seekThumbnail?.let {
                previewLoader = PreviewLoader(context, it)
            }
        }

    var iconSet: PlayerIconSet = PlayerIconSet()
        set(value) {
            field = value
            applyStyle()
        }

    /**
     * Text color used for the position counter, total duration, and the `/` divider between
     * them in the bottom controls. Defaults to [Color.WHITE] so the readout stays legible against
     * the dark letterbox. The Bunny dashboard does not expose this setting — set it on the view
     * from your app code to override.
     *
     * When [autoProgressTextColor] is enabled, this value is overridden roughly every
     * [PROGRESS_COLOR_SAMPLE_INTERVAL_MS] ms based on the video pixels behind the readout.
     */
    @ColorInt
    var progressTextColor: Int = Color.WHITE
        set(value) {
            field = value
            applyStyle()
        }

    /**
     * When `true`, the SDK periodically samples the video frame behind the progress/duration
     * text and switches [progressTextColor] between black and white based on average luminance,
     * so the readout stays legible regardless of scene brightness. Sampling only runs while the
     * controller is visible and is suspended when the view detaches from the window.
     */
    var autoProgressTextColor: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) progressColorSampler.start() else progressColorSampler.stop()
        }

    private val progressColorSampler = ProgressTextColorSampler()

    private var playerSettings: PlayerSettings? = null
        set(value) {
            Log.d(TAG, "set playerSettings: $value")
            field = value
            applyStyle()
        }

    private var previewLoader: PreviewLoader? = null

    private val playPauseButton by lazy {
        findViewById<ToggleableImageButton>(R.id.bunny_play_pause)
    }

    private val replyButton by lazy {
        findViewById<ImageButton>(R.id.bunny_replay)
    }

    private val forwardButton by lazy {
        findViewById<ImageButton>(R.id.bunny_forward)
    }

    private val settingsButton by lazy {
        findViewById<ImageButton>(R.id.bunny_settings)
    }

    private val muteButton by lazy {
        findViewById<ToggleableImageButton>(R.id.bunny_mute)
    }

    private val castButton by lazy {
        findViewById<MediaRouteButton>(R.id.bunny_cast)
    }

    private val fullScreenButton by lazy {
        findViewById<ImageButton>(R.id.bunny_fullscreen)
    }

    private val pipButton by lazy {
        findViewById<ImageButton>(R.id.bunny_pip)
    }

    private val progressTextView by lazy {
        findViewById<TextView>(R.id.exo_position)
    }

    private val durationTextView by lazy {
        findViewById<TextView>(R.id.exo_duration)
    }

    private val progressDurationDivider by lazy {
        findViewById<TextView>(R.id.position_duration_divider)
    }

    private val timeBar by lazy {
        findViewById<BunnyTimeBar>(R.id.exo_progress)
    }

    private val timeBarPreview by lazy {
        findViewById<BunnyTimeBarPreview>(R.id.youtubeTimeBarPreview)
    }

    private val subtitles by lazy {
        findViewById<SubtitleView>(R.id.exo_subtitles)
    }

    private val subtitle by lazy {
        findViewById<ToggleableImageButton>(R.id.bunny_subtitle)
    }

    private val errorWrapper by lazy {
        findViewById<ViewGroup>(R.id.errorWrapper)
    }

    private val errorMessage by lazy {
        findViewById<TextView>(R.id.errorMessage)
    }

    private val overlay by lazy {
        findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_overlay)
    }

    private val liveBadge by lazy {
        findViewById<TextView>(R.id.bunny_live_badge)
    }

    private val bottomBar by lazy {
        findViewById<ConstraintLayout>(androidx.media3.ui.R.id.exo_bottom_bar)
    }

    private val i18n = I18n(context)

// Add this method to BunnyPlayerView.kt in the setPlayerControls() method

    private fun setPlayerControls() {
        playPauseButton.setOnClickListener {
            if (bunnyPlayer?.isPlaying() == true) {
                bunnyPlayer?.pause()
            } else {
                bunnyPlayer?.play()
            }
        }

        muteButton.setOnClickListener {
            if (bunnyPlayer?.isMuted() == true) {
                bunnyPlayer?.unmute()
            } else {
                bunnyPlayer?.mute()
            }
        }

        subtitle.setOnClickListener { view ->
            bunnyPlayer?.let {
                it.setSubtitlesEnabled(!it.areSubtitlesEnabled())
            }
            subtitle.state = if (bunnyPlayer?.areSubtitlesEnabled() == true) {
                ToggleableImageButton.State.STATE_TOGGLED
            } else {
                ToggleableImageButton.State.STATE_DEFAULT
            }
        }

        settingsButton.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            popupMenu.inflate(R.menu.video_settings)

            val subtitleMenuIds = setupSubtitlesPopupMenu(popupMenu)
            val qualityMenuIds = setupQualityPopupMenu(popupMenu)
            val audioTracksMenuIds = setupAudioTracksPopupMenu(popupMenu)
            val speedMenuIds = setupSpeedPopupMenu(popupMenu)

            popupMenu.setOnMenuItemClickListener { item ->
                Log.d(TAG, "setOnMenuItemClickListener")

                val subtitleOption = subtitleMenuIds[item.itemId]

                if (subtitleOption != null) {
                    bunnyPlayer?.selectSubtitle(subtitleOption)
                    subtitle.state = if (subtitleOption.language == "") {
                        ToggleableImageButton.State.STATE_DEFAULT
                    } else {
                        ToggleableImageButton.State.STATE_TOGGLED
                    }
                    controllerShowTimeoutMs = 2.seconds.inWholeMilliseconds.toInt()
                    return@setOnMenuItemClickListener true
                }

                val qualityOption = qualityMenuIds[item.itemId]

                if (qualityOption != null) {
                    bunnyPlayer?.selectQuality(qualityOption)
                    controllerShowTimeoutMs = 2.seconds.inWholeMilliseconds.toInt()
                    return@setOnMenuItemClickListener true
                }

                val audioTrackOption = audioTracksMenuIds[item.itemId]

                if (audioTrackOption != null) {
                    bunnyPlayer?.selectAudioTrack(audioTrackOption)
                    controllerShowTimeoutMs = 2.seconds.inWholeMilliseconds.toInt()
                    return@setOnMenuItemClickListener true
                }

                val speedOption = speedMenuIds[item.itemId]

                if (speedOption != null) {
                    bunnyPlayer?.setSpeed(speedOption)
                    controllerShowTimeoutMs = 2.seconds.inWholeMilliseconds.toInt()
                    return@setOnMenuItemClickListener true
                }

                true
            }

            controllerHideOnTouch = true
            controllerShowTimeoutMs = -1

            popupMenu.show()
        }

        replyButton.setOnClickListener {
            bunnyPlayer?.replay()
        }

        forwardButton.setOnClickListener {
            bunnyPlayer?.skipForward()
        }

        fullScreenButton.setOnClickListener {
            fullscreenListener?.onFullscreenToggleClicked()
        }

        pipButton.setOnClickListener {
            enterPip()
        }

        // DVR: tapping the badge while time-shifted snaps back to the live edge
        // (mirrors the web player's LIVE pill behavior).
        liveBadge.setOnClickListener {
            player?.let {
                it.seekToDefaultPosition()
                it.play()
            }
        }

        subtitle.isVisible = bunnyPlayer?.getSubtitles()?.subtitles?.isNotEmpty() == true

        // Only set up cast button if Cast is available
        try {
            if (AppCastContext.isAvailable()) {
                CastButtonFactory.setUpMediaRouteButton(context, castButton)
            } else {
                castButton.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set up Cast button: ${e.message}")
            castButton.visibility = View.GONE
        }
    }

    private fun setupSubtitlesPopupMenu(popupMenu: PopupMenu): Map<Int, SubtitleInfo> {
        val subtitleMenuIds: MutableMap<Int, SubtitleInfo> = mutableMapOf()
        val subtitlesConfig = bunnyPlayer?.getSubtitles()
        val subtitlesEnabled = playerSettings?.subtitlesEnabled == true

        val subtitleMenuId = View.generateViewId()

        if (subtitlesEnabled && subtitlesConfig != null && subtitlesConfig.subtitles.isNotEmpty()) {
            val translation = i18n.getTranslation(R.string.label_video_settings_captions)
            val subtitlesMenu = popupMenu.menu.addSubMenu(
                Menu.NONE,
                subtitleMenuId,
                Menu.NONE,
                translation
            )

            val disabledTitle = i18n.getTranslation(R.string.label_video_settings_captions_disabled)

            val disabled = SubtitleInfo(disabledTitle, "")

            val disabledId = View.generateViewId()
            subtitleMenuIds[disabledId] = disabled
            val item = subtitlesMenu.add(
                Menu.NONE,
                disabledId,
                Menu.NONE,
                disabledTitle
            )
            item.isCheckable = true
            item.isChecked =
                subtitlesConfig.selectedSubtitle == null || subtitlesConfig.selectedSubtitle == disabled

            subtitlesConfig.subtitles.forEach { info ->
                val id = View.generateViewId()
                subtitleMenuIds[id] = info
                val item = subtitlesMenu.add(
                    Menu.NONE,
                    id,
                    Menu.NONE,
                    "${info.title} (${info.language})"
                )
                item.isCheckable = true
                item.isChecked = subtitlesConfig.selectedSubtitle == info
            }

            subtitlesMenu.setGroupCheckable(Menu.NONE, true, true)
        }

        return subtitleMenuIds
    }

    private fun setupQualityPopupMenu(popupMenu: PopupMenu): Map<Int, VideoQuality> {
        val qualityMenuIds: MutableMap<Int, VideoQuality> = mutableMapOf()
        val videoQualityOptions = bunnyPlayer?.getVideoQualityOptions()
        val qualityMenuId = View.generateViewId()

        if (videoQualityOptions != null) {
            val translation = i18n.getTranslation(R.string.label_video_settings_quality)
            val qualityOptionsMenu = popupMenu.menu.addSubMenu(
                Menu.NONE,
                qualityMenuId,
                Menu.NONE,
                translation
            )

            videoQualityOptions.options.forEach { option ->
                val id = generateViewId()
                qualityMenuIds[id] = option

                val title = if (option.width == Int.MAX_VALUE) {
                    context.getString(R.string.label_video_quality_auto)
                } else {
                    "${option.width} x ${option.height}"
                }

                val item = qualityOptionsMenu.add(
                    Menu.NONE,
                    id,
                    Menu.NONE,
                    title
                )

                item.isChecked = videoQualityOptions.selectedOption == option
            }

            qualityOptionsMenu.setGroupCheckable(Menu.NONE, true, true)
        }

        return qualityMenuIds
    }

    private fun setupAudioTracksPopupMenu(popupMenu: PopupMenu): Map<Int, AudioTrackInfo> {
        val audioTracksMenuIds: MutableMap<Int, AudioTrackInfo> = mutableMapOf()
        val audioTrackOptions = bunnyPlayer?.getAudioTrackOptions()
        val audioTracksMenuId = View.generateViewId()

        if (audioTrackOptions != null && audioTrackOptions.options.size > 1) {
            val qualityOptionsMenu = popupMenu.menu.addSubMenu(
                Menu.NONE,
                audioTracksMenuId,
                Menu.NONE,
                i18n.getTranslation(R.string.label_video_settings_audio_track)
            )

            audioTrackOptions.options.forEach { option ->
                val id = generateViewId()
                audioTracksMenuIds[id] = option

                val item = qualityOptionsMenu.add(
                    Menu.NONE,
                    id,
                    Menu.NONE,
                    option.label ?: "N/A"
                )

                item.isChecked = audioTrackOptions.selectedOption == option
            }

            qualityOptionsMenu.setGroupCheckable(Menu.NONE, true, true)
        }

        return audioTracksMenuIds
    }

    private fun setupSpeedPopupMenu(popupMenu: PopupMenu): Map<Int, Float> {
        val speedMenuIds: MutableMap<Int, Float> = mutableMapOf()
        val currentSpeed = bunnyPlayer?.getSpeed() ?: 1.0f
        val speeds = bunnyPlayer?.getPlaybackSpeeds()
        val speedManager = PlaybackSpeedManager()

        val speedMenuId = View.generateViewId()

        if (!speeds.isNullOrEmpty()) {
            val speedOptionsMenu = popupMenu.menu.addSubMenu(
                Menu.NONE,
                speedMenuId,
                Menu.NONE,
                i18n.getTranslation(R.string.label_video_settings_speed)
            )

            speeds.forEach { speed ->
                val id = generateViewId()
                speedMenuIds[id] = speed

                val item = speedOptionsMenu.add(
                    Menu.NONE,
                    id,
                    Menu.NONE,
                    speedManager.getSpeedDisplayText(speed)
                )
                item.isCheckable = true
                item.isChecked = abs(currentSpeed - speed) < 0.01f // Float comparison

                // Add visual indicator for current speed
                if (item.isChecked) {
                    item.setIcon(R.drawable.ic_check) // You'll need to add this icon
                }
            }

            speedOptionsMenu.setGroupCheckable(Menu.NONE, true, true)
        }
        return speedMenuIds
    }

    private fun showSpeedBadge(speed: Float) {
        if (speed == 1.0f) {
            hideSpeedBadge()
            return
        }

        val speedBadge = findViewById<TextView>(R.id.speed_badge) ?: createSpeedBadge()
        speedBadge.text = PlaybackSpeedManager().getSpeedDisplayText(speed).replace("×", "x")
        speedBadge.isVisible = true

        // Auto-hide after 2 seconds
        speedBadge.animate()
            .alpha(1.0f)
            .setDuration(200)
            .withEndAction {
                speedBadge.postDelayed({
                    speedBadge.animate()
                        .alpha(0.0f)
                        .setDuration(1000)
                        .withEndAction { speedBadge.isVisible = false }
                }, 2000)
            }
    }

    private fun createSpeedBadge(): TextView {
        val speedBadge = TextView(context).apply {
            id = R.id.speed_badge
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.speed_badge_background) // You'll need to create this
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            textSize = 14f
            alpha = 0.0f
            isVisible = false
        }

        // Add to overlay
        val overlay = findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_overlay)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dpToPx(60), dpToPx(16), 0)
        }

        overlay.addView(speedBadge, layoutParams)
        return speedBadge
    }

    private fun hideSpeedBadge() {
        findViewById<TextView>(R.id.speed_badge)?.isVisible = false
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun updatePlayer(player: Player, playerType: PlayerType) {
        Log.d(TAG, "updatePlayer player=$player playerTpe=$playerType")
        this.player = player

        when (playerType) {
            PlayerType.DEFAULT_PLAYER -> {
                controllerShowTimeoutMs = PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS
                defaultArtwork = null
                controllerHideOnTouch = true
                muteButton.isVisible = true
            }

            PlayerType.CAST_PLAYER -> {
                controllerShowTimeoutMs = 0
                showController()
                defaultArtwork = ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_cast_connected_400,
                    null
                )
                controllerHideOnTouch = false
                muteButton.isVisible = false
            }
        }
    }

    private fun applyStyle() {
        playPauseButton.setStateIcons(iconSet.playIcon, iconSet.pauseIcon)
        replyButton.setImageResource(iconSet.rewindIcon)
        forwardButton.setImageResource(iconSet.forwardIcon)
        settingsButton.setImageResource(iconSet.settingsIcon)
        muteButton.setStateIcons(iconSet.volumeOnIcon, iconSet.volumeOffIcon)

        progressTextView.setTextColor(progressTextColor)
        durationTextView.setTextColor(progressTextColor)
        progressDurationDivider.setTextColor(progressTextColor)

        val fullScreenIcon = if (isFullscreen) {
            iconSet.fullscreenOffIcon
        } else {
            iconSet.fullscreenOnIcon
        }

        fullScreenButton.setImageResource(fullScreenIcon)

        val settings = playerSettings

        if (settings == null) {
            timeBar.tintColor = Color.WHITE
            subtitles.setStyle(CaptionStyleCompat.DEFAULT)
        } else {
            // A fully-transparent keyColor (alpha 0) means "no colour set" — the live path passes
            // keyColor = config.primaryColor ?: 0, so the default live config would otherwise tint
            // the scrub bar transparent (invisible). Fall back to the same WHITE default used above.
            timeBar.tintColor = settings.keyColor.takeIf { Color.alpha(it) != 0 } ?: Color.WHITE

            subtitles.setStyle(
                getSubtitleStyle(
                    settings.captionsFontColor,
                    settings.captionsBackgroundColor
                )
            )
            subtitles.setFixedTextSize(Dimension.SP, settings.captionsFontSize.toFloat())

            // Google fonts are usually capitalized, e.g. "rubik" is not found but "Rubik" is
            fetchFont(settings.fontFamily.capitalizeWords())

            updateControlsVisibility()

            i18n.load(settings.uiLanguage)
        }

        invalidate()
    }

    private fun initTimeBar() {
        timeBar.timeBarPreview(timeBarPreview)

        timeBarPreview.previewListener(object : BunnyTimeBarPreview.PreviewListener {
            override fun loadThumbnail(imageView: ImageView, position: Long) {
                previewLoader?.loadPreview(position, imageView)
            }
        })

        timeBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                showController()
                controllerShowTimeoutMs = 120.seconds.inWholeMilliseconds.toInt()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                /* no-op, seek will be done in onScrubStop */
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                player?.seekTo(position)
                timeBar.setPosition(position)
                controllerShowTimeoutMs = 2.seconds.inWholeMilliseconds.toInt()
            }
        })

        setControllerVisibilityListener(ControllerVisibilityListener {
            if (playerSettings?.rewindEnabled == true) {
                replyButton.visibility = it
            }
            if (playerSettings?.fastForwardEnabled == true) {
                forwardButton.visibility = it
            }
            bottomBar.visibility = it
            if (it == View.VISIBLE) {
                timeBar.showScrubber()
            } else {
                timeBar.hideScrubber()
            }
        })
    }

    private fun fetchFont(fontFamily: String) {
        Log.d(TAG, "fetchFont: $fontFamily")
        val handlerThread = HandlerThread("fonts").apply { start() }
        val handler = Handler(handlerThread.looper)

        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            fontFamily,
            R.array.com_google_android_gms_fonts_certs
        )
        val callback = object : FontsContractCompat.FontRequestCallback() {

            override fun onTypefaceRetrieved(typeface: Typeface) {
                Log.d(TAG, "onTypefaceRetrieved: $typeface")
                updateFonts(typeface)
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                Log.d(TAG, "onTypefaceRequestFailed: $reason")
            }
        }
        FontsContractCompat.requestFont(context, request, callback, handler)
    }

    private fun updateFonts(typeface: Typeface) {
        timeBarPreview.updateTypeface(typeface)
        progressTextView.typeface = typeface
        durationTextView.typeface = typeface

        playerSettings?.let {
            subtitles.setStyle(
                getSubtitleStyle(
                    it.captionsFontColor,
                    it.captionsBackgroundColor,
                    typeface
                )
            )
        }
    }

    private fun getSubtitleStyle(
        fontColor: Int? = null,
        backgroundColor: Int? = null,
        typeface: Typeface? = null
    ): CaptionStyleCompat {
        return CaptionStyleCompat(
            /* foregroundColor = */ fontColor ?: CaptionStyleCompat.DEFAULT.foregroundColor,
            /* backgroundColor = */ backgroundColor ?: CaptionStyleCompat.DEFAULT.backgroundColor,
            /* windowColor = */ CaptionStyleCompat.DEFAULT.windowColor,
            /* edgeType = */ CaptionStyleCompat.DEFAULT.edgeType,
            /* edgeColor = */ CaptionStyleCompat.DEFAULT.edgeColor,
            /* typeface = */ typeface
        )
    }

    private fun updateControlsVisibility() {
        // In compact mode the secondary controls (settings, captions, duration readout) are hidden
        // to declutter; the essentials (play, progress, mute, PiP, fullscreen) stay.
        val compact = compactControls

        replyButton.isVisible = playerSettings?.rewindEnabled == true && !compact
        forwardButton.isVisible = playerSettings?.fastForwardEnabled == true && !compact
        progressTextView.isVisible = playerSettings?.currentTimeEnabled == true
        durationTextView.isVisible = playerSettings?.durationEnabled == true && !compact
        fullScreenButton.isVisible = playerSettings?.fullScreenEnabled == true
        // The mute button doubles as the volume affordance on mobile, so show it when either is on.
        muteButton.isVisible =
            (playerSettings?.muteEnabled == true || playerSettings?.volumeEnabled == true)
        settingsButton.isVisible = playerSettings?.settingsEnabled == true && !compact
        // The dashboard's control list can include `captions` even when the current video has no
        // subtitle tracks (always true for live streams, whose synthetic VideoModel carries none)
        // — a captions button with nothing to select is dead weight, so require actual tracks.
        val hasSubtitleTracks = bunnyPlayer?.getSubtitles()?.subtitles?.isNotEmpty() == true
        subtitle.isVisible = playerSettings?.subtitlesEnabled == true && !compact && hasSubtitleTracks
        timeBar.isVisible = playerSettings?.progressEnabled == true
        playPauseButton.isVisible = playerSettings?.playButtonEnabled == true
        castButton.isVisible = playerSettings?.castButtonEnabled == true
        // PiP is hidden in compact mode and on devices/contexts that can't enter PiP.
        pipButton.isVisible = playerSettings?.pipEnabled == true && !compact && canEnterPip()

        progressDurationDivider.isVisible = progressTextView.isVisible && durationTextView.isVisible
    }

    /** Walks the context chain to find the hosting [Activity], or null if there isn't one. */
    private fun hostActivity(): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** True when the device + host support entering picture-in-picture. */
    private fun canEnterPip(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val activity = hostActivity() ?: return false
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /** Enters picture-in-picture via the host activity. No-op when unsupported. */
    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val activity = hostActivity() ?: run {
            Log.w(TAG, "Cannot enter PiP — no host Activity")
            return
        }
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(pipAspectRatio())
                // Where the video currently is on screen — the system animates the shrink from
                // this rect instead of the whole Activity.
                .setSourceRectHint(Rect().also(::getGlobalVisibleRect))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enter PiP: ${e.message}")
        }
    }

    /**
     * Aspect ratio for the PiP window from the latest decoded video size, clamped to the
     * system-permitted range (~2.39:1 down to 1:2.39); 16:9 until the first frame is known.
     */
    private fun pipAspectRatio(): Rational {
        val (w, h) = lastVideoSize ?: return Rational(16, 9)
        val ratio = w.toFloat() / h.toFloat()
        return when {
            ratio > MAX_PIP_ASPECT -> Rational(239, 100)
            ratio < MIN_PIP_ASPECT -> Rational(100, 239)
            else -> Rational(w, h)
        }
    }

    fun showPreviewThumbnail(url: String) {
        Log.d(TAG, "onShowPreviewThumbnail: $url")
        overlay.removeAllViews()
        // Live streams (and still-processing uploads) carry no preview thumbnail — the synthetic
        // live PlayerSettings pass an empty URL here. GlideUrl's constructor throws on a null/empty
        // string, so skip rendering rather than crash the player.
        if (url.isBlank()) return
        val thumbnail = ImageView(context)
        overlay.addView(thumbnail)
        // Referer for the CDN's block-direct-url (hotlink) protection — see the player data source.
        val glideUrl = GlideUrl(url) {
            mapOf("Referer" to BunnyCdn.REFERER)
        }
        Glide.with(context).load(glideUrl).into(thumbnail)
    }

    fun showError(message: String) {
        errorWrapper.isVisible = true
        errorMessage.text = message
    }

    /**
     * Hides the error overlay. Called when a new source load starts so a late-arriving error from
     * the previous player instance (e.g. the stale live URL during a live→VOD hand-off) doesn't
     * stay painted over working playback.
     */
    fun hideError() {
        errorWrapper.isVisible = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoProgressTextColor) progressColorSampler.start()
        liveEdgeUpdater.start()
    }

    override fun onDetachedFromWindow() {
        progressColorSampler.stop()
        liveEdgeUpdater.stop()
        super.onDetachedFromWindow()
    }

    private val liveEdgeUpdater = LiveEdgeUpdater()

    /**
     * Keeps the LIVE badge in sync with playback: hidden for VOD, red at the live edge, gray
     * when the viewer has paused/rewound into the DVR window. Ticks once a second while the
     * view is attached — the work is a couple of player getters, so this is negligible.
     */
    private inner class LiveEdgeUpdater {

        private val handler = Handler(Looper.getMainLooper())
        private var running = false

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                update()
                handler.postDelayed(this, LIVE_EDGE_UPDATE_INTERVAL_MS)
            }
        }

        fun start() {
            if (running) return
            running = true
            handler.post(tick)
        }

        fun stop() {
            running = false
            handler.removeCallbacks(tick)
        }

        private fun update() {
            val p = player
            val isLive = p != null && p.isCurrentMediaItemLive
            if (liveBadge.isVisible != isLive) liveBadge.isVisible = isLive
            if (!isLive || p == null) return

            val offset = p.currentLiveOffset
            val atEdge = p.playWhenReady &&
                offset != androidx.media3.common.C.TIME_UNSET &&
                offset <= LIVE_EDGE_THRESHOLD_MS
            liveBadge.background.setTint(if (atEdge) LIVE_EDGE_COLOR else BEHIND_LIVE_COLOR)
            liveBadge.alpha = if (atEdge) 1f else 0.9f
        }
    }

    /**
     * Releases background resources held by the auto-contrast sampler. Call from your
     * Activity/Fragment teardown if you toggled [autoProgressTextColor] — otherwise the sampler's
     * worker thread persists until the view is garbage collected.
     */
    fun releaseAutoProgressTextColorResources() {
        progressColorSampler.release()
    }

    /**
     * Samples a small bottom strip of the video surface on a background thread, computes the
     * average WCAG relative luminance, and posts back to the main thread to flip
     * [progressTextColor] between black and white. Only ticks while the controller is visible.
     */
    private inner class ProgressTextColorSampler {

        private val workerThread = HandlerThread("bunny-progress-color-sampler").apply { start() }
        private val workerHandler = Handler(workerThread.looper)
        private val mainHandler = Handler(Looper.getMainLooper())

        private var running = false
        private var released = false

        private val tick = Runnable {
            if (!running) return@Runnable
            sample()
            scheduleNext()
        }

        fun start() {
            if (released || running) return
            running = true
            mainHandler.post(tick)
        }

        fun stop() {
            running = false
            mainHandler.removeCallbacks(tick)
        }

        /**
         * Permanently shuts down the worker thread. After this, calling [start] is a no-op until
         * the enclosing view is recreated.
         */
        fun release() {
            if (released) return
            released = true
            stop()
            workerThread.quitSafely()
        }

        private fun scheduleNext() {
            if (!running) return
            mainHandler.postDelayed(tick, PROGRESS_COLOR_SAMPLE_INTERVAL_MS)
        }

        private fun sample() {
            if (!isControllerFullyVisible) return
            val surface = videoSurfaceView ?: return
            val w = surface.width
            val h = surface.height
            if (w <= 0 || h <= 0) return

            val sampleH = (h * SAMPLE_HEIGHT_FRACTION).toInt().coerceAtLeast(8)
            // PixelCopy doesn't scale, so the destination must match the source rect size.
            // We compensate by reading with a stride in [applyLuminance].
            val srcRect = Rect(0, h - sampleH, w, h)
            val bitmap = Bitmap.createBitmap(w, sampleH, Bitmap.Config.ARGB_8888)

            when (surface) {
                is SurfaceView -> {
                    try {
                        PixelCopy.request(surface, srcRect, bitmap, { result ->
                            if (result == PixelCopy.SUCCESS) applyLuminance(bitmap)
                            bitmap.recycle()
                        }, workerHandler)
                    } catch (e: IllegalArgumentException) {
                        // Surface not yet ready (no underlying buffer); try again next tick.
                        Log.v(TAG, "PixelCopy not ready: ${e.message}")
                        bitmap.recycle()
                    }
                }
                is TextureView -> {
                    val full = surface.getBitmap(w, h)
                    if (full != null) {
                        val cropped = Bitmap.createBitmap(full, 0, h - sampleH, w, sampleH)
                        full.recycle()
                        applyLuminance(cropped)
                        cropped.recycle()
                    }
                    bitmap.recycle()
                }
                else -> bitmap.recycle()
            }
        }

        /**
         * Computes the average sRGB relative luminance of the sampled bitmap by reading every
         * `stride`th pixel — that keeps the work to ~MAX_SAMPLE_DIMENSION^2 reads regardless of
         * surface resolution, instead of allocating a full IntArray of the source on every tick.
         */
        private fun applyLuminance(bitmap: Bitmap) {
            val width = bitmap.width
            val height = bitmap.height
            val stride = maxOf(1, minOf(width, height) / MAX_SAMPLE_DIMENSION)

            var lumSum = 0.0
            var count = 0
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val px = bitmap.getPixel(x, y)
                    val r = Color.red(px) / 255.0
                    val g = Color.green(px) / 255.0
                    val b = Color.blue(px) / 255.0
                    // sRGB relative luminance (per WCAG 2.x).
                    lumSum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                    count++
                    x += stride
                }
                y += stride
            }
            if (count == 0) return
            val avgLum = lumSum / count
            val pick = if (avgLum > LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE

            mainHandler.post {
                if (running && progressTextColor != pick) {
                    progressTextColor = pick
                }
            }
        }
    }
}