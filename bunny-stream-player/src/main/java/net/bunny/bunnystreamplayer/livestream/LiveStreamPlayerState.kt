package net.bunny.bunnystreamplayer.livestream

import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.model.LiveStreamStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Pure description of what the live player should display *right now*. Computed from the latest
 * [LiveStream] + [LiveStreamPlayData] snapshot plus the wall clock — no Android types, no
 * coroutines, no mutable state. Lives here so we can unit-test the entire web-player decision
 * tree without spinning up a player.
 *
 * The branches mirror the web player spec (`project_live_stream_player_behavior`):
 *
 *  * [Loading]   — first poll hasn't returned yet.
 *  * [Offline]   — one of "Live stream not active", "Live stream ended", "Live stream error",
 *                  with a [reason] tag so the UI can render the right copy.
 *  * [Countdown] — Scheduled stream with `enableCountdown == true` and a future
 *                  `scheduledStartTime`. UI ticks the timer locally; transition to playback is
 *                  driven by the poll, not by the timer hitting zero.
 *  * [Trailer]   — Pre-stream trailer loop. Only when `preStreamTrailerVideoId` is set, the
 *                  stream hasn't started (`startedAt == null`, status in {Created, Scheduled,
 *                  Error}), and we've already resolved the trailer's HLS URL.
 *  * [LivePlay]  — Stream is Running. Play [hlsUrl].
 *  * [VodPlay]   — Stream is Ended or VodProcessing AND `recordVod == true` AND a playable URL
 *                  is available.
 *
 * Note that the state resolver does *not* read [LiveStreamPlayData.videoPlaylistUrl] directly —
 * the caller is responsible for picking the right URL (videoPlaylistUrl > fallbackUrl >
 * playbackUrlHls) and passing it in. This keeps the resolver decoupled from URL-priority bugs.
 */
public sealed interface LiveStreamPlayerState {

    public data object Loading : LiveStreamPlayerState

    public data class Offline(val reason: OfflineReason) : LiveStreamPlayerState

    /**
     * Scheduled stream with a live countdown. Carries the [title] and the background to render
     * behind the timer, mirroring the web player: when a pre-stream trailer is configured and
     * resolved, [trailerUrl] is set and the overlay loops that video (muted) behind the countdown;
     * otherwise it falls back to the blurred [posterUrl] thumbnail. The overlay shows
     * "{title} will start in" above a large day/hour/minute/second timer in both cases.
     */
    public data class Countdown(
        val targetEpochMs: Long,
        val title: String = "",
        val posterUrl: String? = null,
        val trailerUrl: String? = null,
    ) : LiveStreamPlayerState

    public data class Trailer(val hlsUrl: String) : LiveStreamPlayerState

    public data class LivePlay(val hlsUrl: String) : LiveStreamPlayerState

    public data class VodPlay(val hlsUrl: String) : LiveStreamPlayerState

    public enum class OfflineReason {
        /** Created / Scheduled-no-countdown / Preview / Unknown — stream hasn't started yet. */
        NotActive,

        /** Ended-no-VOD / VodProcessing-no-VOD — stream is over and there's no recording. */
        Ended,

        /** Status == Error. */
        Error,
    }
}

/**
 * Resolves the display state for a live stream.
 *
 * @param stream the latest live-stream snapshot from the server. `null` means we haven't fetched
 *               anything yet — caller should render [LiveStreamPlayerState.Loading].
 * @param playableUrl best HLS URL we could resolve for this stream (videoPlaylistUrl >
 *                    fallbackUrl > playbackUrlHls). `null` if we couldn't get one. Live and VOD
 *                    both come through this same field — Bunny's play-data endpoint returns the
 *                    correct URL based on the stream's current status.
 * @param trailerUrl  resolved HLS URL of the pre-stream trailer video, if any. `null` while it's
 *                    being fetched or if there's no trailer configured. The resolver only uses
 *                    this when the stream is in a pre-start state with `preStreamTrailerVideoId`
 *                    set; in any other case [trailerUrl] is ignored.
 * @param nowEpochMs  current wall-clock time, passed in so tests can pin it.
 * @param posterUrl   the stream's thumbnail URL (from play-data), used only as the blurred
 *                    background behind the countdown overlay so the look matches the web player.
 *                    Ignored for every non-countdown state.
 */
public fun resolveLiveStreamPlayerState(
    stream: LiveStream?,
    playableUrl: String?,
    trailerUrl: String?,
    nowEpochMs: Long,
    posterUrl: String? = null,
): LiveStreamPlayerState {
    if (stream == null) return LiveStreamPlayerState.Loading

    val status = stream.status
    val hasStarted = !stream.startedAt.isNullOrBlank()

    // 1) Running -> live playback if we have a URL; otherwise treat as Loading (the URL fetch is
    //    in flight — we'd rather show the spinner than flash an offline overlay).
    if (status == LiveStreamStatus.RUNNING) {
        return playableUrl?.let { LiveStreamPlayerState.LivePlay(it) }
            ?: LiveStreamPlayerState.Loading
    }

    // 2) Ended / VodProcessing — VOD playback if recordVod is on AND we have a URL; otherwise
    //    Offline(Ended). Note: web spec calls out that VodProcessing might not have a URL yet
    //    ("when available") — so without a URL we fall through to the Ended overlay, which is
    //    the same copy.
    if (status == LiveStreamStatus.ENDED || status == LiveStreamStatus.VOD_PROCESSING) {
        return if (stream.recordVod && !playableUrl.isNullOrBlank()) {
            LiveStreamPlayerState.VodPlay(playableUrl)
        } else {
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.Ended)
        }
    }

    // 3) Error -> Error overlay (or trailer if configured).
    if (status == LiveStreamStatus.ERROR) {
        return preStartStateOrOffline(
            stream = stream,
            trailerUrl = trailerUrl,
            hasStarted = hasStarted,
            fallback = LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.Error),
        )
    }

    // 4) Scheduled with countdown opt-in AND future start time -> Countdown.
    //    Scheduled otherwise -> NotActive overlay (or trailer).
    if (status == LiveStreamStatus.SCHEDULED) {
        val scheduledStartMs = parseEpochMs(stream.scheduledStartTime)
        val countdownOptedIn = stream.enableCountdown == true
        if (countdownOptedIn && scheduledStartMs != null && scheduledStartMs > nowEpochMs) {
            return LiveStreamPlayerState.Countdown(
                targetEpochMs = scheduledStartMs,
                title = stream.title,
                posterUrl = posterUrl,
                // If a pre-stream trailer is configured and resolved, the overlay loops it behind
                // the timer (web-player parity); otherwise the overlay uses the blurred poster.
                trailerUrl = playableTrailerUrl(stream, trailerUrl, hasStarted),
            )
        }
        return preStartStateOrOffline(
            stream = stream,
            trailerUrl = trailerUrl,
            hasStarted = hasStarted,
            fallback = LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
        )
    }

    // 5) Created / Preview / Unknown -> NotActive overlay (or trailer).
    return preStartStateOrOffline(
        stream = stream,
        trailerUrl = trailerUrl,
        hasStarted = hasStarted,
        fallback = LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
    )
}

/**
 * When the stream is in a pre-start state (Created / Scheduled / Error, no startedAt) and the
 * publisher configured a `preStreamTrailerVideoId`, the web player loops that VOD in place of
 * the static offline overlay. Returns [LiveStreamPlayerState.Trailer] only when both the trailer
 * is configured AND we've already resolved its URL; otherwise returns [fallback].
 */
private fun preStartStateOrOffline(
    stream: LiveStream,
    trailerUrl: String?,
    hasStarted: Boolean,
    fallback: LiveStreamPlayerState,
): LiveStreamPlayerState {
    val playable = playableTrailerUrl(stream, trailerUrl, hasStarted)
    return if (playable != null) LiveStreamPlayerState.Trailer(playable) else fallback
}

/**
 * Returns the trailer's HLS URL when the pre-stream trailer should play, else null. The trailer is
 * playable only when `preStreamTrailerVideoId` is configured, the stream hasn't started, and we've
 * already resolved the trailer's URL. Shared by the [LiveStreamPlayerState.Trailer] branch and the
 * [LiveStreamPlayerState.Countdown] branch (which uses it as the countdown's background video).
 */
private fun playableTrailerUrl(
    stream: LiveStream,
    trailerUrl: String?,
    hasStarted: Boolean,
): String? {
    val trailerConfigured = !stream.preStreamTrailerVideoId.isNullOrBlank()
    val canPlay = trailerConfigured && !hasStarted && !trailerUrl.isNullOrBlank()
    return if (canPlay) trailerUrl else null
}

/**
 * Parses Bunny's ISO-8601 timestamps into epoch millis. Handles two shapes the Stream API returns:
 *
 *  1. Zoned / offset timestamps — `2026-05-29T17:00:00Z`, `...+02:00`, with or without
 *     milliseconds — via [Instant.parse].
 *  2. Zone-less local timestamps — `2026-06-10T10:00:00` (and the millisecond variant). The
 *     Manage Live Streams API frequently omits the trailing `Z`, which [Instant.parse] rejects.
 *     We treat these as UTC (the API stores them in UTC), matching the web player.
 *
 * Returns null on parse failure so the caller can fall back to the offline overlay — we'd rather
 * hide the countdown than crash on a malformed timestamp.
 */
internal fun parseEpochMs(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    // 1) Zoned/offset form.
    try {
        return Instant.parse(iso).toEpochMilli()
    } catch (_: DateTimeParseException) {
    } catch (_: IllegalArgumentException) {
    }
    // 2) Zone-less local form — assume UTC.
    return try {
        LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Resolves the best HLS URL from a [LiveStreamPlayData] in the same priority order the demo
 * stopgap used: [LiveStreamPlayData.videoPlaylistUrl] (token-aware, what the iframe uses) ->
 * [LiveStreamPlayData.fallbackUrl] (CDN edge alternative) -> the list-endpoint
 * [LiveStream.playbackUrlHls] (often 403s on token-auth libraries; last resort).
 *
 * Exposed at module level so the ViewModel and tests can call the same function — and so anyone
 * reading the resolver can verify the priority by reading one place.
 */
public fun resolvePlayableUrl(
    stream: LiveStream?,
    playData: LiveStreamPlayData?,
): String? {
    val fromPlayData = playData?.videoPlaylistUrl?.takeIf { it.isNotBlank() }
        ?: playData?.fallbackUrl?.takeIf { it.isNotBlank() }
    if (fromPlayData != null) return fromPlayData
    return stream?.playbackUrlHls?.takeIf { it.isNotBlank() }
}
