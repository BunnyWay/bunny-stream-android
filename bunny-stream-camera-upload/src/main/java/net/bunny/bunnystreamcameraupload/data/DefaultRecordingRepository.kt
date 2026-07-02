package net.bunny.bunnystreamcameraupload.data

import android.util.Log
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BuildConfig
import net.bunny.api.BunnyStreamApi
import net.bunny.bunnystreamcameraupload.domain.RecordingRepository
import net.bunny.bunnystreamcameraupload.domain.ResolvedIngest
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.models.VideoCreateVideoRequest

class DefaultRecordingRepository(
   private val coroutineDispatcher: CoroutineDispatcher
) : RecordingRepository {

    companion object {
        private const val TAG = "DefaultRecordingRepository"
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

            val endpoint = "${BuildConfig.RTMP_ENDPOINT}??vid=${result.guid}&accessKey=${ApiClient.apiKey["AccessKey"]}&lib=$libraryId"

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
    }

    override suspend fun prepareLiveBroadcast(
        libraryId: Long,
        streamId: String,
        ingestEndpoint: String?,
    ): Either<String, ResolvedIngest> = withContext(coroutineDispatcher) {
        when (val result = BunnyStreamApi.getInstance().liveStreamRepository.getLiveStream(libraryId, streamId)) {
            is Either.Left -> Either.Left(result.value)
            is Either.Right -> {
                val stream = result.value
                val streamKey = stream.streamKey
                if (streamKey.isNullOrBlank()) {
                    Either.Left("Live stream $streamId has no stream key yet, cannot publish")
                } else {
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