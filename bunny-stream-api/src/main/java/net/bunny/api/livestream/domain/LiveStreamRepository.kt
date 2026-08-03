package net.bunny.api.livestream.domain

import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.LiveStreamIngestStatus
import net.bunny.api.livestream.domain.model.LiveStreamList
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.livestream.domain.model.LiveStreamThumbnail

/**
 * High-level access to the Manage Live Streams API.
 *
 * Wraps the generated [net.bunny.api.api.ManageLiveStreamsApi] and exposes a coroutine-friendly
 * surface returning the [BunnyResult] envelope (mirrors
 * [net.bunny.api.settings.domain.SettingsRepository]). Failures carry the typed
 * [net.bunny.api.error.BunnyError] taxonomy: the HTTP status code, a message, and whether the
 * failure is terminal (retrying can never succeed) or transient.
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
    ): BunnyResult<LiveStreamList>

    /**
     * Fetches details of a single live stream by its GUID.
     */
    suspend fun getLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream>

    /**
     * The polling entry point of [getLiveStream] — same call, kept as a named method because the
     * polling loop's contract is documented here.
     *
     * The web player's polling rules branch on the error's terminality:
     * `401`/`403`/`404`/`410` ([net.bunny.api.error.BunnyError.isTerminal]) mean **stop polling**
     * — retrying can never succeed; `5xx`, network and decode failures are **transient** — keep
     * polling, the next attempt may recover. Use this method from the live-stream player
     * ViewModel.
     */
    suspend fun pollLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream>

    /**
     * Fetches playback data (HLS URL, controls, DRM, etc.) for a live stream. Optional
     * `token`/`expires` pair is forwarded for token-authenticated libraries.
     */
    suspend fun fetchLiveStreamPlayData(
        libraryId: Long,
        streamId: String,
        token: String? = null,
        expires: Long? = null,
    ): BunnyResult<LiveStreamPlayData>

    /**
     * Creates a new live stream in the given library. Returns the freshly-created stream so
     * callers can immediately surface the assigned `id`/`streamKey` without a follow-up `get`.
     */
    suspend fun createLiveStream(
        libraryId: Long,
        request: LiveStreamCreateRequest,
    ): BunnyResult<LiveStream>

    /**
     * Updates an existing live stream. Only non-null fields in [request] are sent to the server;
     * the API treats missing fields as "leave unchanged".
     */
    suspend fun updateLiveStream(
        libraryId: Long,
        streamId: String,
        request: LiveStreamCreateRequest,
    ): BunnyResult<Unit>

    /**
     * Permanently deletes a live stream. Once deleted the stream cannot be recovered, but any
     * recorded VOD remains in the library.
     */
    suspend fun deleteLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<Unit>

    /**
     * Marks the stream as started so viewers can watch. Call once the RTMP encoder is
     * connected (stream in `PREVIEW`); transitions the stream to `RUNNING`.
     * Returns the updated stream.
     */
    suspend fun startLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream>

    /**
     * Stops the stream. Ongoing publishing is cut by the ingest server, and if `recordVod`
     * was enabled the stream is converted to a VOD video. Cannot be undone.
     * Returns the updated stream.
     */
    suspend fun stopLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream>

    /**
     * Sets the live stream thumbnail from a remote image URL.
     */
    suspend fun setLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        thumbnailUrl: String,
    ): BunnyResult<Unit>

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
    ): BunnyResult<Unit>

    /**
     * Lists the stream's recently generated thumbnails (most recent first), each as a loadable URL
     * plus capture timestamp.
     *
     * @param limit max number of thumbnails to return (server default 5 when null).
     * @param from optional ISO-8601 lower bound (inclusive) on the capture timestamp.
     * @param to optional ISO-8601 upper bound (inclusive) on the capture timestamp.
     */
    suspend fun listLiveStreamThumbnails(
        libraryId: Long,
        streamId: String,
        limit: Int? = null,
        from: String? = null,
        to: String? = null,
    ): BunnyResult<List<LiveStreamThumbnail>>

    /**
     * Removes the stream's custom thumbnail.
     *
     * @param restoreLibraryDefault when true and the library has a default live thumbnail, the
     *        stream falls back to it; otherwise the stream is left with no thumbnail.
     */
    suspend fun deleteLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        restoreLibraryDefault: Boolean = false,
    ): BunnyResult<Unit>

    /**
     * Fetches the stream's lightweight ingest status (`GET …/live/{streamId}/status`) — whether
     * the primary/backup ingests are currently receiving data, whether the stream is ready to
     * start, and how long ago the last ping was seen.
     *
     * This is the endpoint suited for frequent polling (the full [getLiveStream] model does not
     * expose per-ingest liveness). The broadcaster uses it to drive the Primary/Backup badges
     * from the server's truth and to proactively fail over when the ingest it publishes to goes
     * silent — mirroring the iOS SDK.
     */
    suspend fun getLiveStreamStatus(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStreamIngestStatus>
}
