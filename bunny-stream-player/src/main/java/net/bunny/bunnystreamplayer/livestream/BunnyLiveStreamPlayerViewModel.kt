package net.bunny.bunnystreamplayer.livestream

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bunny.api.BunnyStreamApi
import net.bunny.api.StreamApi
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.fold
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.model.LiveStreamStatus
import net.bunny.api.video.domain.VideoRepository
import net.bunny.bunnystreamplayer.PlaybackFailureInfo

/**
 * Backing view model for [BunnyLiveStreamPlayer]. Owns the polling loop, the play-data fetches,
 * and the trailer fetch — exposes a single [StateFlow] of [LiveStreamPlayerState] for the UI to
 * consume. Mirrors the web player spec (`project_live_stream_player_behavior`).
 *
 * Responsibilities, in order of execution:
 *
 *  1. [start] kicks off an initial [fetchPlayData] (carries stream snapshot + playable URL) and,
 *     if the snapshot warrants it, an async [fetchTrailerUrl]. Starts the 5s poll loop.
 *
 *  2. The poll loop calls [LiveStreamRepository.pollLiveStream] every 5 s — lightweight, returns
 *     only the stream snapshot, no play-data. In-flight guard prevents overlapping requests.
 *
 *  3. Whenever the snapshot status transitions to a state that *needs* a URL we don't have yet
 *     (Running with no live URL; Ended/VodProcessing+recordVod with no VOD URL), the view model
 *     re-fetches play-data to pick up the new URL. We avoid re-fetching on every poll because
 *     play-data is the heavier endpoint.
 *
 *  4. Lifecycle: [onForeground] fires one immediate poll then resumes the 5s cadence;
 *     [onBackground] cancels the loop. Terminal failure (`401/403/404/410`) stops polling
 *     permanently per the spec.
 *
 * The class deliberately does not own any Android `Context`, `ExoPlayer`, or `View` — those live
 * in the composable. This keeps it unit-testable on the JVM with a fake [LiveStreamRepository].
 */
public open class BunnyLiveStreamPlayerViewModel internal constructor(
    private val repositoryProvider: () -> LiveStreamRepository,
    private val videoRepositoryProvider: () -> VideoRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowEpochMs: () -> Long,
    private val pollIntervalMs: Long,
) : ViewModel() {

    internal constructor(
        repository: LiveStreamRepository,
        ioDispatcher: CoroutineDispatcher,
        nowEpochMs: () -> Long,
        pollIntervalMs: Long,
    ) : this(
        { repository },
        { BunnyStreamApi.getInstance().videoRepository },
        ioDispatcher,
        nowEpochMs,
        pollIntervalMs,
    )

    public constructor() : this(
        repositoryProvider = { BunnyStreamApi.getInstance().liveStreamRepository },
        videoRepositoryProvider = { BunnyStreamApi.getInstance().videoRepository },
        ioDispatcher = Dispatchers.IO,
        nowEpochMs = { System.currentTimeMillis() },
        pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    )

    /**
     * Resolved on first use rather than at construction.
     *
     * Compose builds this view model during composition, which can run before the host app has
     * initialised the SDK. Reaching for the instance in the constructor turned that ordering into
     * a crash the app had no chance to catch; now [start] reports it through [terminalError] and
     * the player shows a message instead.
     */
    private val repository: LiveStreamRepository by lazy(repositoryProvider)

    /**
     * The video repository the trailer's play-data is fetched through. Comes from the same
     * provider set as [repository], so a player given its own instance fetches the trailer with
     * that instance's key and host — not whichever instance happens to be the default.
     */
    private val videoRepository: VideoRepository by lazy(videoRepositoryProvider)

    private val mutableState = MutableStateFlow<LiveStreamPlayerState>(LiveStreamPlayerState.Loading)
    public val state: StateFlow<LiveStreamPlayerState> = mutableState.asStateFlow()

    /**
     * Latest [LiveStream] snapshot the poll loop has observed. Exposed so host UIs (e.g., the
     * demo's info panel under the player) can render stream metadata — title, status, started-at,
     * DVR/record flags, resolution, viewers — alongside the player. `null` until the first
     * successful fetch (either poll or play-data).
     *
     * Updates flow whenever the poll loop or play-data fetch lands a new snapshot, so consumers
     * that bind via `collectAsStateWithLifecycle` always see the freshest values without needing
     * their own polling.
     */
    private val mutableLiveStream = MutableStateFlow<LiveStream?>(null)
    public val liveStream: StateFlow<LiveStream?> = mutableLiveStream.asStateFlow()

    /**
     * Latest live `/play` response. Carries the dashboard-configured player customization
     * (accent colour, font family, UI language, control tokens, compact mode, heatmap) that the
     * player surfaces consume — the live player is server-driven, mirroring the iOS SDK. `null`
     * until the first successful play-data fetch; consumers fall back to SDK defaults.
     */
    private val mutablePlayData = MutableStateFlow<LiveStreamPlayData?>(null)
    public val playData: StateFlow<LiveStreamPlayData?> = mutablePlayData.asStateFlow()

    /**
     * Terminal failure message — populated when polling has hit `401/403/404/410` and we've given
     * up. UI uses this to flip to an error panel that doesn't suggest the stream is just offline.
     */
    private val mutableTerminalError = MutableStateFlow<String?>(null)
    public val terminalError: StateFlow<String?> = mutableTerminalError.asStateFlow()

    /**
     * Monotonic token the playback surface uses to force a player rebuild after a mid-live
     * playback failure ([onPlaybackFailure]) when the refreshed play-data yields the *same* URL —
     * e.g. a transient network drop or falling behind the live window. Bumped only while the
     * stream is still live; a status change (offline/ended) re-routes through [state] instead.
     */
    private val mutableRebuildToken = MutableStateFlow(0)
    public val playerRebuildToken: StateFlow<Int> = mutableRebuildToken.asStateFlow()

    // Inputs frozen at start() — we don't support "restart this VM with a different stream".
    private var libraryId: Long = -1L
    private var streamId: String = ""
    private var token: String? = null
    private var expires: Long? = null

    // Latest observed values. We re-resolve [state] from these on every change.
    // [currentStream] is exposed as [liveStream] StateFlow above — this property delegates so
    // every internal read sees the single source of truth without callers needing to know.
    private var currentStream: LiveStream?
        get() = mutableLiveStream.value
        set(value) { mutableLiveStream.value = value }
    private var currentPlayData: LiveStreamPlayData?
        get() = mutablePlayData.value
        set(value) { mutablePlayData.value = value }
    private var currentTrailerUrl: String? = null

    private var pollJob: Job? = null
    private var pollInFlight: Boolean = false
    private var playDataInFlight: Boolean = false
    private var trailerInFlight: Boolean = false
    private var trailerVideoIdRequested: String? = null
    private var terminated: Boolean = false
    private var started: Boolean = false
    private var lastRecoveryAtMs: Long? = null
    private var deferredRecoveryJob: Job? = null

    /**
     * Initialise the view model. Idempotent; subsequent calls with the same [streamId] are
     * ignored. Must be called before [onForeground] / [onBackground].
     */
    public fun start(
        libraryId: Long,
        streamId: String,
        token: String? = null,
        expires: Long? = null,
    ) {
        if (started) {
            Log.d(TAG, "start() ignored — already started for streamId=$streamId")
            return
        }
        started = true

        // First touch of the SDK instance. If the host never initialised it, say so here rather
        // than letting the failure escape from whatever coroutine happens to reach it first.
        val unavailable = runCatching { repository }.exceptionOrNull()
        if (unavailable != null) {
            Log.e(TAG, "cannot start — the SDK has no instance", unavailable)
            terminated = true
            mutableTerminalError.value =
                "The Bunny SDK is not initialised. Call BunnyStreamApi.initialize(...) before " +
                    "showing the player."
            return
        }

        this.libraryId = libraryId
        this.streamId = streamId
        this.token = token
        this.expires = expires
        Log.d(TAG, "start — libraryId=$libraryId streamId=$streamId hasToken=${token != null}")
        viewModelScope.launch { fetchPlayData(reason = "start") }
    }

    /**
     * Called by the UI on lifecycle resume. Per spec: fire one immediate poll, then resume the
     * 5 s cadence. If polling has already been permanently stopped (terminal failure), this is a
     * no-op.
     */
    public fun onForeground() {
        if (terminated) {
            Log.d(TAG, "onForeground — polling terminated; ignoring")
            return
        }
        if (!started) {
            Log.w(TAG, "onForeground before start() — ignoring")
            return
        }
        Log.d(TAG, "onForeground — restarting poll loop with immediate tick")
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            pollOnce(reason = "foreground-immediate")
            while (isActive && !terminated) {
                delay(pollIntervalMs)
                pollOnce(reason = "interval")
            }
        }
    }

    /**
     * Called by the UI on lifecycle pause. Cancels the poll loop. The current [state] is
     * preserved so the UI keeps rendering what it had.
     */
    public fun onBackground() {
        Log.d(TAG, "onBackground — cancelling poll loop")
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Single poll tick. Respects the in-flight guard: if a previous fetch is still pending we
     * skip this tick rather than queue a second request behind it. Updates the snapshot, kicks
     * off a play-data re-fetch if the new status needs a URL we don't have, and kicks off a
     * trailer fetch if the new snapshot warrants it.
     */
    private suspend fun pollOnce(reason: String) {
        if (terminated) return
        if (pollInFlight) {
            Log.d(TAG, "pollOnce[$reason] — skipped (previous still in flight)")
            return
        }
        pollInFlight = true
        try {
            Log.v(TAG, "pollOnce[$reason] — calling pollLiveStream")
            val result = withContext(ioDispatcher) {
                repository.pollLiveStream(libraryId, streamId)
            }
            when (result) {
                is BunnyResult.Ok -> handleStreamUpdate(result.value)
                is BunnyResult.Err -> handlePollFailure(result.error)
            }
        } finally {
            pollInFlight = false
        }
    }

    private fun handlePollFailure(error: BunnyError) {
        // A poll already in flight when playback went terminal must not overwrite that verdict.
        if (terminated) return
        if (error.isTerminal) {
            Log.w(TAG, "poll failed with terminal status ${error.httpStatus} — stopping polling")
            terminated = true
            pollJob?.cancel()
            pollJob = null
            mutableTerminalError.value = error.message
        } else {
            // 5xx/network/transient — keep polling, no UI change. Spec: "Treat them as transient;
            // back off if you want, but do not stop."
            Log.w(TAG, "poll failed with transient status ${error.httpStatus}: ${error.message}")
        }
    }

    private suspend fun handleStreamUpdate(stream: LiveStream) {
        // A poll already in flight when playback went terminal must not flip the state back.
        if (terminated) return
        val previousStatus = currentStream?.status
        currentStream = stream
        Log.d(TAG, "stream snapshot — status=${stream.status} startedAt=${stream.startedAt}")

        // If the new status calls for a URL we don't (or shouldn't) keep, kick off play-data again:
        //   - we just observed Running for the first time (live URL needed),
        //   - we entered Ended/VodProcessing+recordVod — the play-data URL flips from the live edge
        //     to the recorded VOD, so we MUST re-fetch on that transition even though we still hold
        //     the (now stale) live URL; otherwise VOD playback would replay the live playlist.
        //   - we're sitting in Ended/VodProcessing+recordVod still missing a URL (recording not
        //     ready yet at the last fetch) — keep trying until the VOD playlist appears.
        val statusChanged = previousStatus != stream.status
        val isVodState = (stream.status == LiveStreamStatus.ENDED ||
            stream.status == LiveStreamStatus.VOD_PROCESSING) &&
            stream.recordVod
        val needsLiveUrl = stream.status == LiveStreamStatus.RUNNING &&
            resolvePlayableUrl(stream, currentPlayData).isNullOrBlank()
        val needsVodUrl = isVodState &&
            (statusChanged || resolvePlayableUrl(stream, currentPlayData).isNullOrBlank())
        if (needsLiveUrl || needsVodUrl) {
            Log.d(TAG, "status transitioned to ${stream.status} — re-fetching play-data for URL")
            // Don't await — let the poll loop continue. recomputeState() will run once play-data
            // lands and the [state] flow will flip then.
            viewModelScope.launch {
                fetchPlayData(reason = "status-change-from-${previousStatus}-to-${stream.status}")
            }
        }

        // Trailer needs evaluating regardless of status change — even a "no change" poll might be
        // the first time we've seen a `preStreamTrailerVideoId` (e.g., publisher set it after we
        // already started).
        maybeFetchTrailer(stream)

        recomputeState()
    }

    /**
     * Fetches play-data and updates [currentStream] + [currentPlayData] from the response. The
     * play-data endpoint conveniently returns its own embedded stream snapshot which we accept
     * as fresher than whatever [pollLiveStream] last returned.
     */
    private suspend fun fetchPlayData(reason: String) {
        if (terminated) return
        if (playDataInFlight) {
            Log.d(TAG, "fetchPlayData[$reason] — skipped (previous still in flight)")
            return
        }
        playDataInFlight = true
        try {
            Log.d(TAG, "fetchPlayData[$reason] — calling fetchLiveStreamPlayData")
            val result = withContext(ioDispatcher) {
                repository.fetchLiveStreamPlayData(libraryId, streamId, token, expires)
            }
            result.fold(
                onErr = { error ->
                    // Play-data errors now carry the HTTP status too, but the polling loop stays
                    // the single authoritative terminal-status detector — one decision path, one
                    // place that flips [terminalError]. Treat all play-data errors as transient
                    // here. Worst case: we sit on the loading spinner until polling either
                    // succeeds or hits a terminal status, matching the poll-driven model.
                    Log.w(TAG, "fetchPlayData[$reason] failed (will rely on poll loop): ${error.message}")
                },
                onOk = { playData ->
                    Log.d(
                        TAG,
                        "fetchPlayData[$reason] OK — " +
                            "status=${playData.liveStream?.status} " +
                            "videoPlaylistUrl=${playData.videoPlaylistUrl?.take(60)} " +
                            "fallbackUrl=${playData.fallbackUrl?.take(60)}",
                    )
                    currentPlayData = playData
                    playData.liveStream?.let { currentStream = it }
                    currentStream?.let { maybeFetchTrailer(it) }
                    recomputeState()
                },
            )
        } finally {
            playDataInFlight = false
        }
    }

    /**
     * Decides whether to fetch the trailer's HLS URL for [stream]. Triggers only when the stream
     * is in a pre-start state with `preStreamTrailerVideoId` set, and we haven't already fetched
     * (or started fetching) that exact video id. Caches the URL so a long pre-start period
     * doesn't hammer the videos API.
     */
    private fun maybeFetchTrailer(stream: LiveStream) {
        val trailerVideoId = stream.preStreamTrailerVideoId?.takeIf { it.isNotBlank() } ?: return
        val hasStarted = !stream.startedAt.isNullOrBlank()
        val inPreStart = stream.status == LiveStreamStatus.CREATED ||
            stream.status == LiveStreamStatus.SCHEDULED ||
            stream.status == LiveStreamStatus.PREVIEW ||
            stream.status == LiveStreamStatus.ERROR
        if (hasStarted || !inPreStart) return
        if (trailerInFlight) return
        if (trailerVideoIdRequested == trailerVideoId && currentTrailerUrl != null) return

        trailerInFlight = true
        trailerVideoIdRequested = trailerVideoId
        viewModelScope.launch {
            try {
                Log.d(TAG, "fetching trailer play-data — videoId=$trailerVideoId")
                val url = videoRepository
                    .fetchVideoPlayData(libraryId, trailerVideoId, token, expires)
                    .fold(
                        // Bunny's video play-data returns a similar shape to live play-data —
                        // videoPlaylistUrl first, fallbackUrl second. Treat blanks as "no URL".
                        onOk = { playData ->
                            playData.videoPlaylistUrl?.takeIf { it.isNotBlank() }
                                ?: playData.fallbackUrl?.takeIf { it.isNotBlank() }
                        },
                        onErr = { error ->
                            Log.w(TAG, "trailer play-data failed: ${error.message}")
                            null
                        },
                    )
                if (url.isNullOrBlank()) {
                    Log.w(TAG, "trailer play-data returned no playable URL — skipping trailer")
                } else {
                    Log.i(TAG, "trailer URL resolved: ${url.take(80)}")
                    currentTrailerUrl = url
                    recomputeState()
                }
            } catch (e: Exception) {
                // Trailer is optional UX — log and fall through to the static offline overlay.
                Log.w(TAG, "trailer fetch failed (non-fatal): ${e.message}", e)
            } finally {
                trailerInFlight = false
            }
        }
    }

    /**
     * Re-runs the state resolver against the latest inputs and publishes the result. Centralised
     * here so every input mutation (poll, play-data, trailer) flows through the same path.
     */
    private fun recomputeState() {
        val resolved = resolveLiveStreamPlayerState(
            stream = currentStream,
            playableUrl = resolvePlayableUrl(currentStream, currentPlayData),
            trailerUrl = currentTrailerUrl,
            nowEpochMs = nowEpochMs(),
            posterUrl = currentPlayData?.thumbnailUrl,
        )
        mutableState.update { resolved }

        // Once the ENDED stream's recording is playing there is nothing left to poll for — an
        // ended stream can't restart, and the recording URL is final. Stop the loop permanently
        // (matching the iOS player) instead of pinging the API every 5 s for the rest of the
        // session. VOD_PROCESSING keeps polling: its play-data can still change until it settles
        // into ENDED.
        if (resolved is LiveStreamPlayerState.VodPlay &&
            currentStream?.status == LiveStreamStatus.ENDED &&
            !terminated
        ) {
            Log.d(TAG, "recording is playing and stream is ENDED — stopping polling permanently")
            terminated = true
            pollJob?.cancel()
            pollJob = null
        }
    }

    /**
     * Called by the playback surface when the live player errors mid-play (network drop, falling
     * behind the live window, stale segment URLs). Mirrors the iOS player's recovery: re-poll the
     * status and refresh play-data immediately; if the stream is still live afterwards, bump
     * [playerRebuildToken] so the surface rebuilds the player from the live edge even when the
     * URL didn't change. If the status flipped (offline/ended), [state] re-routes the UI instead.
     *
     * Throttled to one recovery per poll interval so a persistently failing stream doesn't spin
     * in a tight rebuild loop. A failure inside the throttle window is NOT dropped: it schedules
     * one deferred recovery for when the window closes — an errored ExoPlayer never re-raises,
     * so without this a rebuild that fails immediately (segments still missing) would strand the
     * viewer on a frozen frame forever. The result is one recovery attempt per interval until
     * playback sticks or the stream stops being live.
     */
    public fun onPlaybackFailure(message: String? = null) {
        if (terminated || !started) return
        val now = nowEpochMs()
        val last = lastRecoveryAtMs
        if (last != null && now - last < pollIntervalMs) {
            if (deferredRecoveryJob?.isActive != true) {
                val remaining = pollIntervalMs - (now - last)
                Log.d(TAG, "onPlaybackFailure inside throttle window — retrying in ${remaining}ms")
                deferredRecoveryJob = viewModelScope.launch {
                    delay(remaining)
                    performRecovery(reason = "deferred-retry after: $message")
                }
            } else {
                Log.d(TAG, "onPlaybackFailure — deferred retry already scheduled")
            }
            return
        }
        viewModelScope.launch { performRecovery(reason = message ?: "playback failure") }
    }

    /**
     * Structured counterpart of [onPlaybackFailure] fed by the SDK's own playback surface. A
     * blocked stream — the CDN answered 403 (geo-blocking, hotlink protection or an expired token;
     * deliberately not told apart) — is terminal: polling and any pending recovery stop, and
     * [terminalError] carries the viewer copy ("Video is not available") instead of the raw
     * error. Every other failure keeps the recovery loop above — a 404 on the manifest is routine
     * while the stream is RUNNING but the playlist isn't published yet.
     */
    internal fun onPlaybackFailure(info: PlaybackFailureInfo) {
        if (terminated || !started) return
        if (!info.isBlocked) {
            onPlaybackFailure(info.rawMessage)
            return
        }
        Log.w(TAG, "playback blocked with HTTP ${info.httpStatus} — stopping: ${info.rawMessage}")
        terminated = true
        deferredRecoveryJob?.cancel()
        pollJob?.cancel()
        pollJob = null
        mutableTerminalError.value = info.userMessage
    }

    private suspend fun performRecovery(reason: String) {
        if (terminated) return
        lastRecoveryAtMs = nowEpochMs()
        Log.w(TAG, "playback failure — re-polling and refreshing play-data: $reason")
        pollOnce(reason = "playback-failure")
        fetchPlayData(reason = "playback-failure")
        if (!terminated && mutableState.value is LiveStreamPlayerState.LivePlay) {
            mutableRebuildToken.update { it + 1 }
        }
    }

    /**
     * Forces a state recomputation. Used by the countdown UI on each tick so an expiring
     * scheduledStartTime flips to "Starting soon…" without waiting for a poll.
     */
    public fun tickCountdown() {
        if (currentStream?.status == LiveStreamStatus.SCHEDULED) recomputeState()
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared — cancelling all jobs")
        pollJob?.cancel()
        super.onCleared()
    }

    public companion object {
        private const val TAG = "BunnyLive/VM"

        /** Production poll interval, matched to the web player. Don't lower without sign-off. */
        public const val DEFAULT_POLL_INTERVAL_MS: Long = 5_000L

        /**
         * Builds a view model bound to [bunny], or to the default instance when it is null.
         *
         * [BunnyLiveStreamPlayer] uses this so an app addressing more than one library can point
         * the player at the right one. The instance is resolved when the stream starts, not here.
         */
        internal fun factory(bunny: StreamApi?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BunnyLiveStreamPlayerViewModel(
                    repositoryProvider = {
                        (bunny ?: BunnyStreamApi.getInstance()).liveStreamRepository
                    },
                    videoRepositoryProvider = {
                        (bunny ?: BunnyStreamApi.getInstance()).videoRepository
                    },
                    ioDispatcher = Dispatchers.IO,
                    nowEpochMs = { System.currentTimeMillis() },
                    pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
                )
            }
        }
    }
}
