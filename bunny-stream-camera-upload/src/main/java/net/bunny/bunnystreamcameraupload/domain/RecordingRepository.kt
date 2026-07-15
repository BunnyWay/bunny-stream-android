package net.bunny.bunnystreamcameraupload.domain

import arrow.core.Either
import net.bunny.api.livestream.domain.model.LiveStreamIngestStatus

internal interface RecordingRepository {
    suspend fun prepareRecording(libraryId: Long): Either<String, String>

    /**
     * Resolves the RTMP publish target(s) for an existing live stream.
     *
     * Fetches the live stream (to obtain its `streamKey`) and builds the publish URL as
     * `"$host/$streamKey"`. The primary host is [ingestEndpoint] when provided, otherwise the
     * stream's `primaryIngestUrl` from the API, otherwise the SDK default
     * ([net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT]). The stream's `backupIngestUrl` (when
     * present) is returned as [ResolvedIngest.backupUrl] for failover.
     *
     * @return [Either.Right] with the resolved [ResolvedIngest], or [Either.Left] with an error
     * message (e.g. stream not found, or the stream has no stream key yet).
     */
    suspend fun prepareLiveBroadcast(
        libraryId: Long,
        streamId: String,
        ingestEndpoint: String? = null,
    ): Either<String, ResolvedIngest>

    /**
     * Marks the live stream as started (PREVIEW → RUNNING) so viewers can watch.
     * Call after the RTMP connection succeeds.
     */
    suspend fun startLiveStream(libraryId: Long, streamId: String): Either<String, Unit>

    /**
     * Stops the live stream server-side (ends it for viewers; converts to VOD if enabled).
     */
    suspend fun stopLiveStream(libraryId: Long, streamId: String): Either<String, Unit>

    /**
     * Fetches the stream's lightweight ingest status (`GET …/live/{streamId}/status`) — whether
     * the primary/backup ingests are receiving data. Polled by the broadcaster to drive the
     * Primary/Backup badges from the server's truth and to proactively fail over when the
     * currently-published ingest goes silent.
     */
    suspend fun getIngestStatus(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStreamIngestStatus>
}
