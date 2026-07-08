package net.bunny.bunnystreamplayer.livestream

import android.content.res.Configuration
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
// Use the lifecycle-package LocalLifecycleOwner — the one in compose.ui.platform was deprecated
// and removed in newer Compose UI versions in favor of this.
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import net.bunny.api.BunnyCdn
import net.bunny.player.R
import net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Public Compose entry point for live-stream playback. Mirrors Bunny's web player behaviour for
 * every non-running state (offline overlays, countdown, pre-stream trailer) and auto-connects to
 * the live source when polling reports the stream is running — the viewer never needs to
 * refresh.
 *
 * Source of truth for the behaviour spec is `project_live_stream_player_behavior` in memory; the
 * state resolver in [resolveLiveStreamPlayerState] codifies it. This composable is intentionally
 * thin: it observes [BunnyLiveStreamPlayerViewModel.state] and renders one of a handful of
 * branches with no decision logic of its own.
 *
 * @param libraryId the Bunny library id. Must match the [net.bunny.api.BunnyStreamApi] init.
 * @param streamId  GUID of the live stream to play.
 * @param token     optional embed-view token for token-authenticated libraries.
 * @param expires   embed-view token expiration timestamp (epoch seconds).
 * @param modifier  Compose modifier for the root container.
 * @param viewModel injected for testability; defaults to the lifecycle-scoped instance.
 */
@OptIn(UnstableApi::class)
@Composable
public fun BunnyLiveStreamPlayer(
    libraryId: Long,
    streamId: String,
    token: String? = null,
    expires: Long? = null,
    config: LivePlayerConfig = LivePlayerConfig(),
    modifier: Modifier = Modifier,
    onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null,
    viewModel: BunnyLiveStreamPlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val terminalError by viewModel.terminalError.collectAsStateWithLifecycle()

    LaunchedEffect(libraryId, streamId) {
        Log.d(TAG_UI, "BunnyLiveStreamPlayer entered — libraryId=$libraryId streamId=$streamId")
        viewModel.start(libraryId, streamId, token, expires)
    }

    // Bridge the host activity/fragment's lifecycle into the VM so polling pauses when
    // backgrounded and fires an immediate poll on return, per the web spec.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onBackground()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Clip every state to the player box. RESIZE_MODE_ZOOM enlarges the trailer's surface to
            // crop-fill the box, and without this the excess would paint outside the player (behind
            // the host's surrounding content). TextureView surfaces honour this clip.
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            LiveStreamPlayerState.Loading -> {
                Log.v(TAG_UI, "render: Loading")
                CircularProgressIndicator(color = Color.White)
            }

            is LiveStreamPlayerState.Offline -> {
                Log.d(TAG_UI, "render: Offline(${s.reason})")
                OfflineOverlay(reason = s.reason, posterUrl = s.posterUrl, config = config)
            }

            is LiveStreamPlayerState.Countdown -> {
                Log.d(TAG_UI, "render: Countdown(target=${s.targetEpochMs})")
                CountdownOverlay(
                    targetEpochMs = s.targetEpochMs,
                    title = s.title,
                    posterUrl = s.posterUrl,
                    trailerUrl = s.trailerUrl,
                    config = config,
                    onTick = { viewModel.tickCountdown() },
                )
            }

            is LiveStreamPlayerState.Trailer -> {
                Log.d(TAG_UI, "render: Trailer")
                // Trailer uses the same Bunny player chrome so the look is identical to a live
                // / VOD playback. We can't loop through this entry point yet (the engine doesn't
                // expose repeat mode); revisit if publishers ask for it.
                BunnyPlayerSurface(
                    libraryId = libraryId,
                    streamId = "trailer-${streamId}",
                    title = "",
                    hlsUrl = s.hlsUrl,
                    config = config,
                )
            }

            is LiveStreamPlayerState.LivePlay -> {
                Log.d(TAG_UI, "render: LivePlay")
                BunnyPlayerSurface(
                    libraryId = libraryId,
                    streamId = streamId,
                    title = "",
                    hlsUrl = s.hlsUrl,
                    config = config,
                    dvrEnabled = s.dvrEnabled,
                    onVideoSizeChanged = onVideoSizeChanged,
                )
                LiveBadge(
                    primaryColor = config.primaryColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
            }

            is LiveStreamPlayerState.VodPlay -> {
                Log.d(TAG_UI, "render: VodPlay")
                BunnyPlayerSurface(
                    libraryId = libraryId,
                    streamId = "vod-${streamId}",
                    title = "",
                    hlsUrl = s.hlsUrl,
                    config = config,
                    onVideoSizeChanged = onVideoSizeChanged,
                )
            }
        }

        terminalError?.let { msg ->
            TerminalErrorPanel(message = msg)
        }
    }
}

// region — Offline / countdown overlays

@Composable
private fun OfflineOverlay(
    reason: LiveStreamPlayerState.OfflineReason,
    posterUrl: String? = null,
    config: LivePlayerConfig = LivePlayerConfig(),
) {
    val messageRes = when (reason) {
        LiveStreamPlayerState.OfflineReason.NotActive -> R.string.live_status_not_active
        LiveStreamPlayerState.OfflineReason.Ended -> R.string.live_status_ended
        LiveStreamPlayerState.OfflineReason.Error -> R.string.live_status_error
    }
    val message = localizedString(messageRes, config.uiLanguage)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Stream thumbnail (when set) stays visible behind the status — e.g. before a live stream
        // starts — matching the web player's poster. A dark scrim keeps the message legible.
        if (!posterUrl.isNullOrBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    // Referer for the CDN's block-direct-url (hotlink) protection — see the main
                    // player's data source and PreviewLoader.
                    val glideUrl = GlideUrl(posterUrl) {
                        mapOf("Referer" to BunnyCdn.REFERER)
                    }
                    Glide.with(imageView).load(glideUrl).into(imageView)
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Countdown to [targetEpochMs]. Rendered to mirror Bunny's web player for a Scheduled stream: the
 * stream's [posterUrl] thumbnail blurred behind a dark scrim, with "{title} will start in" above a
 * large `D days HH:MM:SS` timer (matching the web copy and format exactly).
 *
 * Calls [onTick] every second so the ViewModel can re-resolve the display state when the target
 * time passes (the spec says transition to playback is driven by the poll, not the timer — but we
 * still want "Starting soon…" to render when the timer hits zero before the next poll).
 */
@Composable
private fun CountdownOverlay(
    targetEpochMs: Long,
    title: String,
    posterUrl: String?,
    trailerUrl: String?,
    config: LivePlayerConfig = LivePlayerConfig(),
    onTick: () -> Unit,
) {
    // [mutableLongStateOf] returns a primitive-specialized state whose `by`-delegate operator
    // isn't available without an explicit setValue import that older Compose runtimes don't ship.
    // Holding the state directly and reading `.longValue` sidesteps the delegate entirely.
    val remainingState = remember(targetEpochMs) {
        mutableLongStateOf((targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    val remainingMs = remainingState.longValue
    LaunchedEffect(targetEpochMs) {
        while (true) {
            delay(1_000L)
            val r = (targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            remainingState.longValue = r
            onTick()
            if (r == 0L) break
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Background, in web-player priority order:
        //   1. the pre-stream trailer, looped and muted, when one is configured + resolved;
        //   2. otherwise the stream's thumbnail, blurred (blur renders on API 31+; older devices
        //      show the un-blurred poster behind the scrim, which still reads well).
        when {
            !trailerUrl.isNullOrBlank() -> TrailerBackground(hlsUrl = trailerUrl)

            !posterUrl.isNullOrBlank() -> AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    // Referer for the CDN's block-direct-url (hotlink) protection — see the main
                    // player's data source and PreviewLoader.
                    val glideUrl = GlideUrl(posterUrl) {
                        mapOf("Referer" to BunnyCdn.REFERER)
                    }
                    Glide.with(imageView).load(glideUrl).into(imageView)
                },
            )
        }

        // Dark scrim over the poster so the countdown text stays legible — matches the web player.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            val headline = if (title.isNotBlank()) {
                localizedString(R.string.live_label_will_start_in, config.uiLanguage, title)
            } else {
                localizedString(R.string.live_label_will_start_in_generic, config.uiLanguage)
            }
            // Tint the timer with the configured primary colour (web-player parity); fall back to
            // white when no colour is set.
            val timerColor = config.primaryColor
                ?.let { Color(it) }
                ?.takeIf { it.alpha > 0f }
                ?: Color.White
            Text(
                text = headline,
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (remainingMs > 0) {
                    formatCountdown(remainingMs)
                } else {
                    localizedString(R.string.live_label_starting_soon, config.uiLanguage)
                },
                color = timerColor,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Formats remaining time the way the web player does: `D days HH:MM:SS` once there's a day or more
 * left (the word "days" stays plural to match the web copy verbatim), otherwise `HH:MM:SS`.
 */
private fun formatCountdown(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val days = totalSeconds / (24 * 3600)
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (days > 0) {
        "%d days %02d:%02d:%02d".format(days, hours, minutes, seconds)
    } else {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

/**
 * Loops the pre-stream trailer [hlsUrl] as a silent, controlless background behind the countdown,
 * matching the web player. Uses a dedicated lightweight [ExoPlayer] (not the full
 * [BunnyStreamPlayer] chrome) so there are no playback controls competing with the timer; it's
 * muted, set to repeat, and centre-cropped to fill. The player is created/released with the
 * composition and re-created when [hlsUrl] changes.
 *
 * The [PlayerView] is inflated from [R.layout.view_trailer_background] so it uses a **TextureView**
 * surface. RESIZE_MODE_ZOOM enlarges the surface to crop-fill the portrait trailer into the
 * landscape box; a SurfaceView's separate hardware layer would bleed past the box (showing behind
 * the metadata cards), whereas a TextureView composites in-hierarchy and clips to its bounds.
 * `surface_type` can only be set via the XML attribute, hence the layout.
 */
@OptIn(UnstableApi::class)
@Composable
private fun TrailerBackground(hlsUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(hlsUrl) {
        // Send the CDN Referer so the trailer keeps loading when the library has "Block direct url
        // file access" on (referer-based hotlink protection) — mirrors the main player's data source.
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Referer" to BunnyCdn.REFERER)),
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(hlsUrl))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(hlsUrl) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        factory = { ctx ->
            (LayoutInflater.from(ctx)
                .inflate(R.layout.view_trailer_background, null) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                player = exoPlayer
            }
        },
    )
}

// endregion

// region — Bunny player surface (matches VOD look)

/**
 * Hosts the SDK's [BunnyStreamPlayer] view (the FrameLayout the VOD path already uses), driven
 * by [BunnyStreamPlayer.playLiveUrl] with the pre-resolved HLS URL. Using the same view —
 * including its custom controller layout, time-bar widget, and auto-contrast progress text —
 * is what makes live look identical to VOD; we deliberately do not use a stock Media3
 * `PlayerView` here.
 *
 * The view is keyed by [hlsUrl] so a transition (Trailer → Live, Live → VOD recording, or a
 * URL refresh) tears down the previous player instance and rebuilds against the new URL,
 * matching what the engine expects.
 */
@OptIn(UnstableApi::class)
@Composable
private fun BunnyPlayerSurface(
    libraryId: Long,
    streamId: String,
    title: String,
    hlsUrl: String,
    config: LivePlayerConfig = LivePlayerConfig(),
    dvrEnabled: Boolean = false,
    onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null,
) {
    // [AndroidView.update] runs on every recomposition, but `playLiveUrl` tears down the engine
    // and rebuilds — calling it on a no-op recompose would interrupt playback. Track the last
    // URL + config we asked the view to load and only re-issue when they actually change.
    val lastUrlState = remember { mutableStateOf<String?>(null) }
    val lastConfigState = remember { mutableStateOf<LivePlayerConfig?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Log.i(TAG_PLAYER, "factory: building BunnyStreamPlayer for ${hlsUrl.take(60)}")
            BunnyStreamPlayer(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.onVideoSizeChanged = onVideoSizeChanged
                // BunnyStreamPlayer queues the play call until it's attached to the window; safe
                // to invoke from factory.
                playLiveUrl(
                    libraryId = libraryId,
                    streamId = streamId,
                    videoTitle = title,
                    hlsUrl = hlsUrl,
                    config = config,
                    dvrEnabled = dvrEnabled,
                )
                lastUrlState.value = hlsUrl
                lastConfigState.value = config
            }
        },
        update = { view ->
            view.onVideoSizeChanged = onVideoSizeChanged
            if (lastUrlState.value == hlsUrl && lastConfigState.value == config) return@AndroidView
            // URL flipped (Trailer → Live, Live → VOD recording, or a URL refresh from play-data
            // because the stream's status changed) or the config changed. Re-issue so the new
            // source / customization takes effect.
            Log.d(TAG_PLAYER, "update: switching BunnyStreamPlayer to ${hlsUrl.take(60)}")
            view.playLiveUrl(
                libraryId = libraryId,
                streamId = streamId,
                videoTitle = title,
                hlsUrl = hlsUrl,
                config = config,
                dvrEnabled = dvrEnabled,
            )
            lastUrlState.value = hlsUrl
            lastConfigState.value = config
        },
    )
}

// endregion

// region — Localization helper

/**
 * Resolves [resId] in the [lang] locale (ISO-639) so the overlay copy honours
 * [LivePlayerConfig.uiLanguage], mirroring the transport bar's `I18n`. When [lang] is null/blank we
 * use the device locale; an unsupported language gracefully falls back to the default resource.
 */
@Composable
private fun localizedString(resId: Int, lang: String?, vararg formatArgs: Any): String {
    val context = LocalContext.current
    return remember(resId, lang, formatArgs.toList()) {
        val base = if (lang.isNullOrBlank()) {
            context
        } else {
            val cfg = Configuration(context.resources.configuration).apply { setLocale(Locale(lang)) }
            context.createConfigurationContext(cfg)
        }
        if (formatArgs.isEmpty()) base.getString(resId) else base.getString(resId, *formatArgs)
    }
}

// endregion

// region — Badges + terminal error panel

@Composable
private fun LiveBadge(
    modifier: Modifier = Modifier,
    primaryColor: Int? = null,
) {
    val pulse = rememberInfiniteTransition(label = "live-badge-pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-badge-pulse-alpha",
    )
    // Default to the live red; honour the configured primary colour when provided.
    val dotColor = primaryColor
        ?.let { Color(it) }
        ?.takeIf { it.alpha > 0f }
        ?: Color(0xFFE53935)

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Live broadcast" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor.copy(alpha = dotAlpha), CircleShape),
        )
        Text(
            text = "LIVE",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        )
    }
}

@Composable
private fun TerminalErrorPanel(message: String) {
    Surface(
        modifier = Modifier.padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Couldn't load live stream", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// endregion

private const val TAG_UI = "BunnyLive/UI"
private const val TAG_PLAYER = "BunnyLive/Player"
