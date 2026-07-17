package net.bunny.bunnystreamcameraupload.data

import android.util.Log
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BuildConfig
import net.bunny.api.BunnyStreamApi
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.fold
import net.bunny.api.error.map
import net.bunny.api.livestream.domain.model.LiveStreamIngestStatus
import net.bunny.api.model.LiveStreamStatus
import net.bunny.bunnystreamcameraupload.domain.RecordingRepository
import net.bunny.bunnystreamcameraupload.domain.ResolvedIngest
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.models.VideoCreateVideoRequest

class DefaultRecordingRepository(
   private val coroutineDispatcher: CoroutineDispatcher
) : RecordingRepository {

    companion object {
        private const val TAG = "DefaultRecordingRepository"

        /**
         * Builds the VOD ingest URL. The ingest server accepts a publish only as app="ingest"
         * with stream name "?vid=...&accessKey=...&lib=..." (leading '?' required).
         * RootEncoder's UrlParser needs the '/' to split app from stream name and strips
         * exactly one leading '?' from it — so the URL must carry "/??". Without the slash the
         * app name is parsed as "ingest?" and the server rejects the publish
         * ("Invalid stream data"), which used to leave every camera-upload VOD empty (0 bytes).
         */
        internal fun buildVodIngestUrl(
            rtmpEndpoint: String,
            videoGuid: String,
            accessKey: String?,
            libraryId: Long,
        ): String = "${rtmpEndpoint.trimEnd('/')}/??vid=$videoGuid&accessKey=$accessKey&lib=$libraryId"
    }

    override suspend fun prepareRecording(libraryId: Long): Either<String, String> = withContext(coroutineDispatcher) {
        val createVideoRequest = VideoCreateVideoRequest(
            title = "recording-${System.currentTimeMillis()}",
            collectionId = null,
            thumbnailTime = null
        )

        try {
            val result = BunnyStreamApi.getInstance().videosApi.videoCreateVideo(
                libraryId = libraryId,
                videoCreateVideoRequest = createVideoRequest
            )

            val guid = result.guid
                ?: return@withContext Either.Left("Video was created without a guid, cannot publish")

            val endpoint = buildVodIngestUrl(
                rtmpEndpoint = BuildConfig.RTMP_ENDPOINT,
                videoGuid = guid.toString(),
                accessKey = ApiClient.apiKey["AccessKey"],
                libraryId = libraryId,
            )

            Log.d(TAG, "endpoint=$endpoint")

            Either.Right(endpoint)
        } catch (e: Exception) {
            Either.Left(e.message ?: e.toString())
        }
    }

    override suspend fun startLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        BunnyStreamApi.getInstance().liveStreamRepository
            .startLiveStream(libraryId, streamId)
            .map { stream ->
                Log.d(TAG, "startLiveStream ok — status=${stream.status}")
                Unit
            }
            .toEither()
    }

    override suspend fun stopLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        BunnyStreamApi.getInstance().liveStreamRepository
            .stopLiveStream(libraryId, streamId)
            .map { stream ->
                Log.d(TAG, "stopLiveStream ok — status=${stream.status}")
                Unit
            }
            .toEither()
    }

    override suspend fun getIngestStatus(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStreamIngestStatus> = withContext(coroutineDispatcher) {
        BunnyStreamApi.getInstance().liveStreamRepository
            .getLiveStreamStatus(libraryId, streamId)
            .toEither()
    }

    /**
     * Bridges the SDK's [BunnyResult] envelope back to this module's `Either<String, T>` surface.
     * The recording module keeps its message-based contract for now; its own migration to
     * [BunnyResult] is a separate change.
     */
    private fun <T> BunnyResult<T>.toEither(): Either<String, T> = fold(
        onOk = { Either.Right(it) },
        onErr = { Either.Left(it.message) },
    )

    override suspend fun prepareLiveBroadcast(
        libraryId: Long,
        streamId: String,
        ingestEndpoint: String?,
    ): Either<String, ResolvedIngest> = withContext(coroutineDispatcher) {
        when (val result = BunnyStreamApi.getInstance().liveStreamRepository.getLiveStream(libraryId, streamId)) {
            is BunnyResult.Err -> Either.Left(result.message)
            is BunnyResult.Ok -> {
                val stream = result.value
                val streamKey = stream.streamKey
                when {
                    // A terminal stream can't be re-published — Bunny rejects it at the RTMP layer
                    // with an opaque error ("stream ended, publishing not allowed"), and the
                    // failover would burn several retries before giving up. Fail fast with a clear
                    // message so the caller can guide the user to create a new stream.
                    stream.status == LiveStreamStatus.ENDED ||
                        stream.status == LiveStreamStatus.VOD_PROCESSING ->
                        Either.Left(
                            "This live stream has ended and can't be restarted — create a new stream.",
                        )

                    streamKey.isNullOrBlank() ->
                        Either.Left("Live stream $streamId has no stream key yet, cannot publish")

                    else -> {
                        // Publish to the real primary ingest host from the API (overridable via
                        // [ingestEndpoint]); keep the backup host for failover. Fall back to the SDK
                        // default host only when the API omits the primary.
                        val primaryHost = (ingestEndpoint ?: stream.primaryIngestUrl ?: BuildConfig.LIVE_RTMP_ENDPOINT)
                            .trimEnd('/')
                        val primaryUrl = "$primaryHost/$streamKey"
                        val backupUrl = stream.backupIngestUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "${it.trimEnd('/')}/$streamKey" }
                        // Log hosts only — the stream key is a secret and must not leak to logcat.
                        Log.d(TAG, "live ingest primaryHost=$primaryHost hasBackup=${backupUrl != null}")
                        Either.Right(ResolvedIngest(primaryUrl, backupUrl))
                    }
                }
            }
        }
    }
}