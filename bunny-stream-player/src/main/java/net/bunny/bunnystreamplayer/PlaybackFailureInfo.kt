package net.bunny.bunnystreamplayer

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.net.ConnectException
import java.net.HttpURLConnection
import java.util.Collections
import java.util.IdentityHashMap

/**
 * What the engine knows about a playback failure, for the SDK's own surfaces.
 *
 * The public [PlayerStateListener.onPlayerError] carries a single String; this keeps what that
 * string flattens away - above all the HTTP status the CDN answered with. Any HTTP 403
 * (geo-blocking, hotlink protection or an expired token; deliberately not told apart) means the
 * stream is blocked for this viewer and no retry will help. Bunny's "Blocked countries" geo-block
 * works one layer lower: the CDN host is rejected at the DNS level and resolves to a loopback
 * sinkhole, so the request never reaches a server and the only trace is a refused connection to
 * 127.0.0.1 - [sinkholeAddress] captures that so it counts as blocked as well. [rawMessage] is
 * the developer-facing text that always goes to logcat; [userMessage] is what the viewer sees -
 * the generic "Video is not available" copy for a blocked stream, the raw message for everything
 * else.
 */
internal data class PlaybackFailureInfo(
    val errorCode: Int,
    val errorCodeName: String,
    val httpStatus: Int?,
    val rawMessage: String,
    val userMessage: String,
    val sinkholeAddress: String? = null,
) {
    /**
     * True whenever the failure carries an HTTP 403, wherever media3 files it in the cause chain
     * (a blocked media request, or a refused Widevine license), or the CDN host resolved to a DNS
     * sinkhole ([sinkholeAddress]). Terminal - never retried. Per Bunny's decision the viewer is
     * never told which flavour of block it was.
     */
    val isBlocked: Boolean
        get() = httpStatus == HttpURLConnection.HTTP_FORBIDDEN || sinkholeAddress != null

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
                sinkholeAddress = error.sinkholeAddress(),
            )
            return if (info.isBlocked) info.copy(userMessage = blockedMessage()) else info
        }
    }
}

/**
 * The HTTP status behind this failure, or null when no HTTP response is involved (DRM, decoder,
 * parser or socket-level errors). media3 wraps the data-source exception a few levels deep, so
 * this walks the cause chain from the receiver down - at most [MAX_CAUSE_DEPTH] links and never
 * twice through the same throwable - and returns the first response code it meets.
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

/**
 * The loopback or unspecified address a refused connection was aimed at, or null.
 *
 * A DNS-level geo-block (Bunny's "Blocked countries") makes the CDN host resolve to a sinkhole
 * such as 127.0.0.1, so the socket connect fails with a [ConnectException] whose message names
 * that address ("Failed to connect to <host>/127.0.0.1:443"). A genuine outage never looks like
 * this - it fails to resolve at all, or times out against a real public address - so the
 * loopback / unspecified check is the whole discriminator. Only the literal in the message is
 * inspected; nothing is resolved here, so this is safe on the main thread.
 */
internal fun Throwable.sinkholeAddress(): String? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
        if (current is ConnectException) sinkholeAddressIn(current.message)?.let { return it }
        current = current.cause
        depth++
    }
    return null
}

/**
 * Pulls the target out of an OkHttp-style connect message. The socket address prints as
 * `host/ip:port` (`host/[::1]:port` or `host/::1:port` for IPv6), so take what follows the last
 * `/`, drop the port and the IPv6 brackets, and keep it only if it is a sinkhole address.
 */
private fun sinkholeAddressIn(message: String?): String? {
    val target = message?.substringAfterLast('/', "")?.takeIf { it.isNotEmpty() } ?: return null
    val literal = target.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
    return literal.takeIf { isLoopbackOrUnspecified(it) }
}

private fun isLoopbackOrUnspecified(address: String): Boolean =
    address == "0.0.0.0" || address.startsWith("127.") ||
        address == "::" || address == "::1" ||
        address == "0:0:0:0:0:0:0:0" || address == "0:0:0:0:0:0:0:1"

private const val MAX_CAUSE_DEPTH = 10
