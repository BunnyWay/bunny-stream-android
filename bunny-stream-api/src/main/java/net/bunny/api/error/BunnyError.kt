package net.bunny.api.error

/**
 * The error half of the SDK's result envelope ([BunnyResult]).
 *
 * Every failure the SDK surfaces — management calls and uploads alike — collapses into one of seven
 * cases. The taxonomy is deliberately small: callers almost always branch on two questions only —
 * "was it the network or the server" and "is it worth retrying" — and both answers are derivable
 * from every variant:
 *
 *  * [httpStatus] carries the numeric HTTP status of the failed call, or `0` when no usable HTTP
 *    response existed at all ([Network], [Decode], [LocalFile], [InvalidState]).
 *  * [isTerminal] tells whether retrying can ever succeed. It is `true` for `401`, `403`, `404`
 *    and `410` — the same set the live-stream polling loop has always used (`410 Gone` is what
 *    Bunny returns for a deleted library; once seen, it never recovers) — for [LocalFile], where
 *    the failure is on the device and no retry against the server can change it, and for
 *    [InvalidState], which decides for itself. Everything else — `5xx`, transport failures,
 *    undecodable bodies — is transient: retrying, or the next poll, may succeed.
 *
 * The dedicated [Auth] and [NotFound] variants exist because those are the failures integrators
 * handle specially (wrong or expired key, wrong id); all other HTTP failures stay in the generic
 * [Http] case with their status code.
 */
public sealed class BunnyError {

    /** Human-readable description of the failure, safe to log or surface in debug UI. */
    public abstract val message: String

    /**
     * HTTP status of the failed call, or [NO_HTTP_STATUS] (`0`) when the failure happened before
     * a usable HTTP response existed ([Network], [Decode], [LocalFile], [InvalidState]).
     */
    public abstract val httpStatus: Int

    /**
     * `true` when retrying the same call can never succeed: `401`, `403`, `404`, `410`,
     * [LocalFile] always, and [InvalidState] when it says so. Transient failures — `5xx`,
     * [Network], [Decode] — return `false`.
     */
    public open val isTerminal: Boolean
        get() = httpStatus in TERMINAL_STATUSES

    /**
     * The request never produced a usable HTTP response: DNS failure, unreachable host, socket
     * timeout, dropped connection, TLS problems. Always transient — the polling loops treat it
     * like a `5xx`.
     *
     * @property cause the underlying transport exception, when one exists, for logging.
     */
    public data class Network(
        override val message: String,
        val cause: Throwable? = null,
    ) : BunnyError() {
        override val httpStatus: Int get() = NO_HTTP_STATUS
    }

    /**
     * The server answered with a non-success status that has no dedicated variant: `5xx`, rate
     * limiting, validation failures and so on. `401`/`403` map to [Auth] and `404` to [NotFound]
     * instead; constructing an [Http] with one of those codes is legal but the SDK's own mapping
     * never does it.
     */
    public data class Http(
        override val httpStatus: Int,
        override val message: String,
    ) : BunnyError()

    /**
     * The server rejected the credentials: `401` (missing/expired key or token) or `403`
     * (key valid but not allowed for this library). Always terminal — retrying with the same
     * credentials cannot succeed.
     */
    public data class Auth(
        override val httpStatus: Int,
        override val message: String,
    ) : BunnyError() {
        init {
            require(httpStatus == HTTP_UNAUTHORIZED || httpStatus == HTTP_FORBIDDEN) {
                "Auth is only for $HTTP_UNAUTHORIZED/$HTTP_FORBIDDEN, got $httpStatus"
            }
        }
    }

    /** The requested resource does not exist (`404`): wrong video/stream id or library. Terminal. */
    public data class NotFound(
        override val message: String,
    ) : BunnyError() {
        override val httpStatus: Int get() = HTTP_NOT_FOUND
    }

    /**
     * The server answered successfully but the body could not be decoded into the expected shape.
     * Carries no HTTP status ([NO_HTTP_STATUS]) and is treated as transient: a later call may
     * return a well-formed body (the web player behaves the same way).
     *
     * @property cause the underlying parse exception, for logging.
     */
    public data class Decode(
        override val message: String,
        val cause: Throwable? = null,
    ) : BunnyError() {
        override val httpStatus: Int get() = NO_HTTP_STATUS
    }

    /**
     * The failure happened on the device, before or instead of any network call: the picked file
     * could not be opened or read, its metadata was unavailable, or the content resolver handed
     * back nothing. Raised by the upload path ([net.bunny.api.upload.VideoUploader]); the
     * management surface never produces it.
     *
     * Terminal by definition — the server was never the problem, so retrying the same call with
     * the same file cannot succeed. The user has to pick a different file.
     *
     * @property cause the underlying IO exception, when one exists, for logging.
     */
    public data class LocalFile(
        override val message: String,
        val cause: Throwable? = null,
    ) : BunnyError() {
        override val httpStatus: Int get() = NO_HTTP_STATUS
        override val isTerminal: Boolean get() = true
    }

    /**
     * The call succeeded, but the resource is in a state that forbids what was asked. Raised
     * where the SDK checks a precondition itself rather than letting the server reject it — for
     * example publishing to a live stream that has already ended, or one whose stream key has not
     * been issued yet.
     *
     * Terminality is explicit here because it genuinely varies and cannot be derived: an ended
     * stream never becomes publishable again, while a stream key that has not appeared yet
     * usually appears moments later.
     */
    public data class InvalidState(
        override val message: String,
        override val isTerminal: Boolean = true,
    ) : BunnyError() {
        override val httpStatus: Int get() = NO_HTTP_STATUS
    }

    public companion object {
        /**
         * [httpStatus] value meaning "no usable HTTP response existed"
         * ([Network], [Decode], [LocalFile], [InvalidState]).
         */
        public const val NO_HTTP_STATUS: Int = 0

        internal const val HTTP_UNAUTHORIZED: Int = 401
        internal const val HTTP_FORBIDDEN: Int = 403
        internal const val HTTP_NOT_FOUND: Int = 404
        internal const val HTTP_GONE: Int = 410

        private val TERMINAL_STATUSES: Set<Int> =
            setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND, HTTP_GONE)
    }
}
