package net.bunny.bunnystreamplayer

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.net.HttpURLConnection
import java.util.Collections
import java.util.IdentityHashMap

/**
 * What the engine knows about a playback failure, for the SDK's own surfaces.
 *
 * The public [PlayerStateListener.onPlayerError] carries a single String; this keeps what that
 * string flattens away — above all the HTTP status the CDN answered with. Any HTTP 403
 * (geo-blocking, hotlink protection or an expired token; deliberately not told apart) means the
 * stream is blocked for this viewer and no retry will help. [rawMessage] is the developer-facing
 * text that always goes to logcat; [userMessage] is what the viewer sees — the generic "Video is
 * not available" copy for a blocked stream, the raw message for everything else.
 */
internal data class PlaybackFailureInfo(
    val errorCode: Int,
    val errorCodeName: String,
    val httpStatus: Int?,
    val rawMessage: String,
    val userMessage: String,
) {
    /**
     * True whenever the failure carries an HTTP 403, wherever media3 files it in the cause chain
     * (a blocked media request, or a refused Widevine license). Terminal — never retried. Per
     * Bunny's decision the viewer is never told which flavour of 403 it was.
     */
    val isBlocked: Boolean
        get() = httpStatus == HttpURLConnection.HTTP_FORBIDDEN

    companion object {
        /**
         * Builds the report for [error]. [blockedMessage] resolves the viewer-facing copy and is
         * only called for a blocked stream, so ordinary failures never touch resources.
         */
        fun from(error: PlaybackException, blockedMessage: () -> String): PlaybackFailureInfo {
            val rawMessage = "${error.errorCodeName}: ${error.message}"
            val info = PlaybackFailureInfo(
                errorCode = error.errorCode,
                errorCodeName = error.errorCodeName,
                httpStatus = error.httpStatusCode(),
                rawMessage = rawMessage,
                userMessage = rawMessage,
            )
            return if (info.isBlocked) info.copy(userMessage = blockedMessage()) else info
        }
    }
}

/**
 * The HTTP status behind this failure, or null when no HTTP response is involved (DRM, decoder,
 * parser or socket-level errors). media3 wraps the data-source exception a few levels deep, so
 * this walks the cause chain from the receiver down — at most [MAX_CAUSE_DEPTH] links and never
 * twice through the same throwable — and returns the first response code it meets.
 */
@OptIn(UnstableApi::class)
internal fun Throwable.httpStatusCode(): Int? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
        depth++
    }
    return null
}

private const val MAX_CAUSE_DEPTH = 10
