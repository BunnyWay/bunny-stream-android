package net.bunny.api.livestream.domain

import net.bunny.api.livestream.domain.model.LiveStream

/**
 * Outcome of a single poll of [LiveStreamRepository.pollLiveStream]. Unlike the [arrow.core.Either]
 * surface used elsewhere on this repository, this type preserves the HTTP status code on failure
 * so the live-stream player ViewModel can implement the web player's polling rules:
 *
 *  * `401`, `403`, `404`, `410` -> permanent failure; stop polling.
 *  * `5xx` and network errors    -> transient; keep polling.
 *
 * [Failure.statusCode] is `0` for network/IO errors (i.e. no HTTP response was received at all).
 */
public sealed interface LiveStreamPollResult {
    public data class Success(val stream: LiveStream) : LiveStreamPollResult

    /**
     * @property statusCode HTTP status code, or `0` for a transport-level error (DNS, socket,
     *                      timeout, no connection). Polling rules treat 0 the same as 5xx —
     *                      transient.
     * @property message friendly error string (the same vocabulary the [arrow.core.Either]-string
     *                   surface uses) so callers that want to surface a message don't have to map
     *                   the status code themselves.
     */
    public data class Failure(val statusCode: Int, val message: String) : LiveStreamPollResult
}

/**
 * The web player's terminal-status set. `410 Gone` is included because Bunny returns it when a
 * stream's library has been deleted; once we see that we'll never recover.
 */
public fun LiveStreamPollResult.Failure.isTerminal(): Boolean =
    statusCode == 401 || statusCode == 403 || statusCode == 404 || statusCode == 410
