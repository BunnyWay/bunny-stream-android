package net.bunny.bunnystreamplayer.livestream

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import net.bunny.api.livestream.domain.LiveStreamPollResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.livestream.domain.isTerminal
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.model.LiveStreamStatus

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
    private val repository: LiveStreamRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowEpochMs: () -> Long,
    private val pollIntervalMs: Long,
) : ViewModel() {

    public constructor() : this(
        repository = BunnyStreamApi.getInstance().liveStreamRepository,
        ioDispatcher = Dispatchers.IO,
        nowEpochMs = { System.currentTimeMillis() },
        pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    )

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
                is LiveStreamPollResult.Success -> handleStreamUpdate(result.stream)
                is LiveStreamPollResult.Failure -> handlePollFailure(result)
            }
        } finally {
            pollInFlight = false
        }
    }

    private fun handlePollFailure(failure: LiveStreamPollResult.Failure) {
        if (failure.isTerminal()) {
            Log.w(TAG, "poll failed with terminal status ${failure.statusCode} — stopping polling")
            terminated = true
            pollJob?.cancel()
            pollJob = null
            mutableTerminalError.value = failure.message
        } else {
            // 5xx/network/transient — keep polling, no UI change. Spec: "Treat them as transient;
            // back off if you want, but do not stop."
            Log.w(TAG, "poll failed with transient status ${failure.statusCode}: ${failure.message}")
        }
    }

    private suspend fun handleStreamUpdate(stream: LiveStream) {
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
                ifLeft = { msg ->
                    // Play-data errors don't carry an HTTP status code, so we can't decide
                    // terminal vs transient from here. Treat all play-data errors as transient:
                    // the polling loop is the authoritative terminal-status detector (it gets
                    // back a typed status code) and will set [terminalError] if needed. Worst
                    // case: we sit on the loading spinner until polling either succeeds or hits
                    // a terminal status, which matches the spec's poll-driven transition model.
                    Log.w(TAG, "fetchPlayData[$reason] failed (will rely on poll loop): $msg")
                },
                ifRight = { playData ->
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
                val url = withContext(ioDispatcher) {
                    val playData = BunnyStreamApi.getInstance().videosApi.videoGetVideoPlayData(
                        libraryId,
                        trailerVideoId,
                        token,
                        expires,
                    )
                    // Bunny's video play-data returns a similar shape to live play-data —
                    // videoPlaylistUrl first, fallbackUrl second. Treat blanks as "no URL".
                    playData.videoPlaylistUrl?.takeIf { it.isNotBlank() }
                        ?: playData.fallbackUrl?.takeIf { it.isNotBlank() }
                }
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
    }
}
