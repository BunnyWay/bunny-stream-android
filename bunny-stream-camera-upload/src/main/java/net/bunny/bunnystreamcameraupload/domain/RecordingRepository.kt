package net.bunny.bunnystreamcameraupload.domain

import arrow.core.Either

interface RecordingRepository {
    suspend fun prepareRecording(libraryId: Long): Either<String, String>

    /**
     * Resolves the RTMP publish URL for an existing live stream.
     *
     * Fetches the live stream (to obtain its `streamKey`) and builds the publish URL as
     * `"$ingestEndpoint/$streamKey"`. Pass `null` for [ingestEndpoint] to use the SDK default
     * ([net.bunny.api.BuildConfig.LIVE_RTMP_ENDPOINT]).
     *
     * @return [Either.Right] with the RTMP publish URL, or [Either.Left] with an error message
     * (e.g. stream not found, or the stream has no stream key yet).
     */
    suspend fun prepareLiveBroadcast(
        libraryId: Long,
        streamId: String,
        ingestEndpoint: String? = null,
    ): Either<String, String>

    /**
     * Marks the live stream as started (PREVIEW → RUNNING) so viewers can watch.
     * Call after the RTMP connection succeeds.
     */
    suspend fun startLiveStream(libraryId: Long, streamId: String): Either<String, Unit>

    /**
     * Stops the live stream server-side (ends it for viewers; converts to VOD if enabled).
     */
    suspend fun stopLiveStream(libraryId: Long, streamId: String): Either<String, Unit>
}
