package net.bunny.api.http

import java.io.IOException

/**
 * The endpoints a playback-only instance (one created without an access key) may call.
 *
 * Bunny serves everything a player needs from public `/play` endpoints that authenticate with the
 * library id, the video or stream id and, on a token-authenticated library, a signed token:
 *
 *  * `/library/{libraryId}/videos/{videoId}/play` (video play data and player settings),
 *  * `/library/{libraryId}/videos/{videoId}/play/heatmap` (retention heatmap),
 *  * `/library/{libraryId}/live/{streamId}/play` (live play data, including the stream's status).
 *
 * Everything else is the management API and answers 401 without the key, so a playback-only
 * instance refuses it locally instead (see [MissingAccessKeyException]).
 *
 * Matched against the end of the path so a base URL with a path prefix still works.
 */
private val PUBLIC_PLAYBACK_PATH = Regex(
    "/library/[^/]+/(videos/[^/]+/play(/heatmap)?|live/[^/]+/play)/?$",
)

/** True when [encodedPath] is one of the public playback endpoints listed on [PUBLIC_PLAYBACK_PATH]. */
internal fun isPublicPlaybackPath(encodedPath: String): Boolean =
    PUBLIC_PLAYBACK_PATH.containsMatchIn(encodedPath)

/**
 * Thrown in place of a request that needs the library's access key when the SDK instance was
 * created without one.
 *
 * It never reaches the caller as an exception: [net.bunny.api.error.BunnyErrorMapper] turns it
 * into a terminal [net.bunny.api.error.BunnyError.InvalidState]. It extends [IOException] only
 * because that is what OkHttp interceptors and the TUS client are allowed to throw; the mapper
 * matches it before the generic [IOException] case, which would otherwise make it a transient
 * network error that polling loops retry forever.
 */
internal class MissingAccessKeyException(what: String) : IOException(
    "$what needs the library's access key, and this SDK instance was created without one " +
        "(playback only). Pass the key to BunnyStreamApi.initialize(context, accessKey, " +
        "libraryId), or set BunnyStreamConfig.accessKey. Playback works without it.",
)
