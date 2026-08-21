package net.bunny.bunnystreamcameraupload.data

import android.util.Log
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BuildConfig
import net.bunny.api.BunnyStreamApi
import net.bunny.bunnystreamcameraupload.domain.RecordingRepository
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
         *
         * RootEncoder's UrlParser splits the app name from the stream name on the '/' and strips
         * exactly one leading '?' from the stream name - so the URL has to carry "/??". Without
         * the slash the app name parses as "ingest?" and the stream name loses its leading '?',
         * and the server rejects the publish with "Invalid stream data", which leaves every
         * camera-upload recording empty.
         */
        internal fun buildVodIngestUrl(
            rtmpEndpoint: String,
            videoGuid: String?,
            accessKey: String?,
            libraryId: Long,
        ): String =
            "${rtmpEndpoint.trimEnd('/')}/??vid=$videoGuid&accessKey=$accessKey&lib=$libraryId"
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

            val endpoint = buildVodIngestUrl(
                rtmpEndpoint = BuildConfig.RTMP_ENDPOINT,
                videoGuid = result.guid,
                accessKey = ApiClient.apiKey["AccessKey"],
                libraryId = libraryId,
            )

            Log.d(TAG, "endpoint=$endpoint")

            Either.Right(endpoint)
        } catch (e: Exception) {
            Either.Left(e.message ?: e.toString())
        }
    }
}