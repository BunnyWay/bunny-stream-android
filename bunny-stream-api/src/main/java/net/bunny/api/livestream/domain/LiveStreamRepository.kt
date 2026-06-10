package net.bunny.api.livestream.domain

import arrow.core.Either
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.LiveStreamList
import net.bunny.api.livestream.domain.model.LiveStreamPlayData

/**
 * High-level access to the Manage Live Streams API.
 *
 * Wraps the generated [net.bunny.api.api.ManageLiveStreamsApi] and exposes a coroutine-friendly
 * surface that returns [Either] (mirrors [net.bunny.api.settings.domain.SettingsRepository]).
 */
interface LiveStreamRepository {

    /**
     * Lists live streams in the given library. Pagination and filtering parameters are optional;
     * pass `null` to use the server defaults documented in the OpenAPI spec.
     */
    suspend fun listLiveStreams(
        libraryId: Long,
        page: Int? = null,
        itemsPerPage: Int? = null,
        search: String? = null,
        orderBy: String? = null,
        collectionId: String? = null,
    ): Either<String, LiveStreamList>

    /**
     * Fetches details of a single live stream by its GUID.
     */
    suspend fun getLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStream>

    /**
     * Polling-friendly variant of [getLiveStream] that preserves the HTTP status code on failure.
     *
     * The web player's polling rules treat `401`/`403`/`404`/`410` as **permanent** (stop polling)
     * and `5xx`/network errors as **transient** (keep polling). The existing [Either]-string
     * surface flattens errors to human-readable messages, which is the wrong shape for that
     * decision. Use this method from the live-stream player ViewModel.
     *
     * Returns [LiveStreamPollResult.Success] on 2xx, [LiveStreamPollResult.Failure] otherwise.
     */
    suspend fun pollLiveStream(
        libraryId: Long,
        streamId: String,
    ): LiveStreamPollResult

    /**
     * Fetches playback data (HLS URL, controls, DRM, etc.) for a live stream. Optional
     * `token`/`expires` pair is forwarded for token-authenticated libraries.
     */
    suspend fun fetchLiveStreamPlayData(
        libraryId: Long,
        streamId: String,
        token: String? = null,
        expires: Long? = null,
    ): Either<String, LiveStreamPlayData>

    /**
     * Creates a new live stream in the given library. Returns the freshly-created stream so
     * callers can immediately surface the assigned `id`/`streamKey` without a follow-up `get`.
     */
    suspend fun createLiveStream(
        libraryId: Long,
        request: LiveStreamCreateRequest,
    ): Either<String, LiveStream>

    /**
     * Updates an existing live stream. Only non-null fields in [request] are sent to the server;
     * the API treats missing fields as "leave unchanged".
     */
    suspend fun updateLiveStream(
        libraryId: Long,
        streamId: String,
        request: LiveStreamCreateRequest,
    ): Either<String, Unit>

    /**
     * Permanently deletes a live stream. Once deleted the stream cannot be recovered, but any
     * recorded VOD remains in the library.
     */
    suspend fun deleteLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, Unit>

    /**
     * Marks the stream as started so viewers can watch. Call once the RTMP encoder is
     * connected (stream in `PREVIEW`); transitions the stream to `RUNNING`.
     * Returns the updated stream.
     */
    suspend fun startLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStream>

    /**
     * Stops the stream. Ongoing publishing is cut by the ingest server, and if `recordVod`
     * was enabled the stream is converted to a VOD video. Cannot be undone.
     * Returns the updated stream.
     */
    suspend fun stopLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStream>

    /**
     * Sets the live stream thumbnail from a remote image URL.
     */
    suspend fun setLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        thumbnailUrl: String,
    ): Either<String, Unit>

    /**
     * Uploads a local image as the live stream thumbnail.
     *
     * The generated client only exposes the URL-based [setLiveStreamThumbnail]; the Set Thumbnail
     * endpoint also accepts the raw image bytes as the POST body, which this method uses.
     *
     * @param imageBytes the encoded image (JPEG/PNG/WebP) bytes.
     * @param contentType the image MIME type, e.g. `image/jpeg`.
     */
    suspend fun uploadLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        imageBytes: ByteArray,
        contentType: String = "image/jpeg",
    ): Either<String, Unit>
}
