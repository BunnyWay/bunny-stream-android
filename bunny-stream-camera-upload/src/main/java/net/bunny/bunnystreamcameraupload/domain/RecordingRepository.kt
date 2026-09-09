package net.bunny.bunnystreamcameraupload.domain

import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.model.LiveStreamIngestStatus

/**
 * Server-side operations the camera broadcaster needs: creating the video a recording publishes
 * into, resolving RTMP publish targets, and driving a live stream's server-side lifecycle.
 *
 * Every call returns [BunnyResult], the same envelope the rest of the SDK uses, so a caller
 * branches on one typed [net.bunny.api.error.BunnyError] rather than on a message string.
 *
 * Internal: the broadcaster is driven through [net.bunny.bunnystreamcameraupload.BunnyStreamCameraUpload],
 * which owns its own instance. Nothing in the documented API takes one of these.
 */
internal interface RecordingRepository {

    /**
     * Creates a video and returns the RTMP URL to publish the recording into.
     *
     * @return [BunnyResult.Ok] with the publish URL, or [BunnyResult.Err] — most usefully
     * [net.bunny.api.error.BunnyError.Auth] for a bad access key, or
     * [net.bunny.api.error.BunnyError.Decode] when the video was created without a guid.
     */
    suspend fun prepareRecording(libraryId: Long): BunnyResult<String>

    /**
     * Resolves the RTMP publish target(s) for an existing live stream.
     *
     * Fetches the live stream (to obtain its `streamKey`) and builds the publish URL as
     * `"$host/$streamKey"`. The primary host is [ingestEndpoint] when provided, otherwise the
     * stream's `primaryIngestUrl` from the API, otherwise the SDK default
     * ([net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT]). The stream's `backupIngestUrl` (when
     * present) is returned as [ResolvedIngest.backupUrl] for failover.
     *
     * @return [BunnyResult.Ok] with the resolved [ResolvedIngest], or [BunnyResult.Err]. Two
     * failures are decided here rather than by the server and arrive as
     * [net.bunny.api.error.BunnyError.InvalidState]: a stream that has already ended (terminal —
     * it can never be published to again) and a stream whose key has not been issued yet
     * (transient — retrying shortly usually succeeds).
     */
    suspend fun prepareLiveBroadcast(
        libraryId: Long,
        streamId: String,
        ingestEndpoint: String? = null,
    ): BunnyResult<ResolvedIngest>

    /**
     * Marks the live stream as started (PREVIEW → RUNNING) so viewers can watch.
     * Call after the RTMP connection succeeds.
     */
    suspend fun startLiveStream(libraryId: Long, streamId: String): BunnyResult<Unit>

    /**
     * Stops the live stream server-side (ends it for viewers; converts to VOD if enabled).
     */
    suspend fun stopLiveStream(libraryId: Long, streamId: String): BunnyResult<Unit>

    /**
     * Fetches the stream's lightweight ingest status (`GET …/live/{streamId}/status`) — whether
     * the primary/backup ingests are receiving data. Polled by the broadcaster to drive the
     * Primary/Backup badges from the server's truth and to proactively fail over when the
     * currently-published ingest goes silent.
     */
    suspend fun getIngestStatus(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStreamIngestStatus>
}
