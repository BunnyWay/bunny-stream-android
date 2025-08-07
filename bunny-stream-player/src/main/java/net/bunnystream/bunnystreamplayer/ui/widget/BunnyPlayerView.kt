package net.bunnystream.bunnystreamplayer.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
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
import kotlin.math.abs

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BunnyPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PlayerView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "BunnyPlayerView"
    }

    interface FullscreenListener {
        fun onFullscreenToggleClicked()
    }

    var isFullscreen: Boolean = false
        set(value) {
            field = value
            applyStyle()
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
        }
    }

    var fullscreenListener: FullscreenListener? = null

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

    private val progressTextView by lazy {
        findViewById<TextView>(R.id.exo_position)
    }

    private val durationTextView by lazy {
        findViewById<TextView>(R.id.exo_duration)
    }

    private val progressDurationDivider by lazy {
        findViewById<View>(R.id.position_duration_divider)
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

    private val bottomBar by lazy {
        findViewById<ConstraintLayout>(androidx.media3.ui.R.id.exo_bottom_bar)
    }

    private val i18n = I18n(context)

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

        subtitle.isVisible = bunnyPlayer?.getSubtitles()?.subtitles?.isNotEmpty() == true

        CastButtonFactory.setUpMediaRouteButton(context, castButton)
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
            timeBar.tintColor = settings.keyColor

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
        replyButton.isVisible = playerSettings?.rewindEnabled == true
        forwardButton.isVisible = playerSettings?.fastForwardEnabled == true
        progressTextView.isVisible = playerSettings?.currentTimeEnabled == true
        durationTextView.isVisible = playerSettings?.durationEnabled == true
        fullScreenButton.isVisible = playerSettings?.fullScreenEnabled == true
        muteButton.isVisible = playerSettings?.muteEnabled == true
        settingsButton.isVisible = playerSettings?.settingsEnabled == true
        subtitle.isVisible = playerSettings?.subtitlesEnabled == true
        timeBar.isVisible = playerSettings?.progressEnabled == true
        playPauseButton.isVisible = playerSettings?.playButtonEnabled == true
        castButton.isVisible = playerSettings?.castButtonEnabled == true

        progressDurationDivider.isVisible = progressTextView.isVisible && durationTextView.isVisible
    }

    fun showPreviewThumbnail(url: String) {
        Log.d(TAG, "onShowPreviewThumbnail: $url")
        val thumbnail = ImageView(context)
        overlay.removeAllViews()
        overlay.addView(thumbnail)
        Glide.with(context).load(url).into(thumbnail)
    }

    fun showError(message: String) {
        errorWrapper.isVisible = true
        errorMessage.text = message
    }
}