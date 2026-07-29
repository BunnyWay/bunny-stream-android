package net.bunny.bunnystreamcameraupload.data

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BuildConfig
import net.bunny.api.BunnyStreamApi
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.map
import net.bunny.api.livestream.domain.model.LiveStreamIngestStatus
import net.bunny.api.model.LiveStreamStatus
import net.bunny.api.video.domain.model.CreateVideoRequest
import net.bunny.bunnystreamcameraupload.domain.RecordingRepository
import net.bunny.bunnystreamcameraupload.domain.ResolvedIngest
import net.bunny.bunnystreamcameraupload.util.redactSecrets
import org.openapitools.client.infrastructure.ApiClient

internal class DefaultRecordingRepository(
    private val coroutineDispatcher: CoroutineDispatcher,
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

    override suspend fun prepareRecording(libraryId: Long): BunnyResult<String> =
        withContext(coroutineDispatcher) {
            val created = BunnyStreamApi.getInstance().videoRepository.createVideo(
                libraryId = libraryId,
                request = CreateVideoRequest(title = "recording-${System.currentTimeMillis()}"),
            )

            when (created) {
                is BunnyResult.Err -> created
                is BunnyResult.Ok -> {
                    // The repository already turns a 2xx without a guid into BunnyError.Decode,
                    // so an Ok here always carries a usable id.
                    val endpoint = buildVodIngestUrl(
                        rtmpEndpoint = BuildConfig.RTMP_ENDPOINT,
                        videoGuid = created.value.id,
                        accessKey = ApiClient.apiKey["AccessKey"],
                        libraryId = libraryId,
                    )
                    Log.d(TAG, "endpoint=${endpoint.redactSecrets()}")
                    BunnyResult.Ok(endpoint)
                }
            }
        }

    override suspend fun startLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        BunnyStreamApi.getInstance().liveStreamRepository
            .startLiveStream(libraryId, streamId)
            .map { stream ->
                Log.d(TAG, "startLiveStream ok — status=${stream.status}")
                Unit
            }
    }

    override suspend fun stopLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        BunnyStreamApi.getInstance().liveStreamRepository
            .stopLiveStream(libraryId, streamId)
            .map { stream ->
                Log.d(TAG, "stopLiveStream ok — status=${stream.status}")
                Unit
            }
    }

    override suspend fun getIngestStatus(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStreamIngestStatus> = withContext(coroutineDispatcher) {
        BunnyStreamApi.getInstance().liveStreamRepository
            .getLiveStreamStatus(libraryId, streamId)
    }

    override suspend fun prepareLiveBroadcast(
        libraryId: Long,
        streamId: String,
        ingestEndpoint: String?,
    ): BunnyResult<ResolvedIngest> = withContext(coroutineDispatcher) {
        when (val result = BunnyStreamApi.getInstance().liveStreamRepository.getLiveStream(libraryId, streamId)) {
            is BunnyResult.Err -> result
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
                        BunnyResult.Err(
                            BunnyError.InvalidState(
                                "This live stream has ended and can't be restarted — create a new stream.",
                            ),
                        )

                    streamKey.isNullOrBlank() ->
                        BunnyResult.Err(
                            BunnyError.InvalidState(
                                message = "Live stream $streamId has no stream key yet, cannot publish",
                                // The key is issued moments after creation, so this one is worth
                                // retrying — unlike an ended stream.
                                isTerminal = false,
                            ),
                        )

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
                        BunnyResult.Ok(ResolvedIngest(primaryUrl, backupUrl))
                    }
                }
            }
        }
    }
}