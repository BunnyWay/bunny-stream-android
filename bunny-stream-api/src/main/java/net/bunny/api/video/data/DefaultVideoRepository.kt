package net.bunny.api.video.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.http.postExpectingSuccess
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.bunnyCatching
import net.bunny.api.error.requireSuccess
import net.bunny.api.video.domain.VideoRepository
import net.bunny.api.video.domain.model.AddCaptionRequest
import net.bunny.api.video.domain.model.CreateVideoRequest
import net.bunny.api.video.domain.model.FetchVideoRequest
import net.bunny.api.video.domain.model.SmartGenerateRequest
import net.bunny.api.video.domain.model.TranscribeVideoRequest
import net.bunny.api.video.domain.model.UpdateVideoRequest
import net.bunny.api.video.domain.model.Video
import net.bunny.api.video.domain.model.VideoCodec
import net.bunny.api.video.domain.model.VideoList
import net.bunny.api.video.domain.model.VideoPlayData
import net.bunny.api.video.domain.model.VideoResolutionsInfo
import net.bunny.api.video.domain.model.VideoStatistics
import net.bunny.api.video.domain.model.VideoStorageSize
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.EncoderOutputCodec
import java.io.File
import java.net.URLEncoder

/**
 * Adapts the generated [ManageVideosApi] into the domain surface.
 *
 * Every call runs on [coroutineDispatcher] and goes through
 * [bunnyCatching][net.bunny.api.error.bunnyCatching], so whatever the HTTP stack throws — generated
 * client exceptions, transport failures, malformed bodies — comes back as a typed
 * [net.bunny.api.error.BunnyError] rather than an exception the caller has to know about.
 */
internal class DefaultVideoRepository(
    private val videosApi: ManageVideosApi,
    private val coroutineDispatcher: CoroutineDispatcher,
) : VideoRepository {

    // region — reading

    override suspend fun listVideos(
        libraryId: Long,
        page: Int,
        itemsPerPage: Int,
        search: String?,
        orderBy: String,
        collectionId: String?,
    ): BunnyResult<VideoList> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoList(
                libraryId = libraryId,
                page = page,
                itemsPerPage = itemsPerPage,
                search = search.orEmpty(),
                collection = collectionId.orEmpty(),
                orderBy = orderBy,
            ).toDomain()
        }
    }

    override suspend fun getVideo(libraryId: Long, videoId: String): BunnyResult<Video> =
        withContext(coroutineDispatcher) {
            bunnyCatching { videosApi.videoGetVideo(libraryId, videoId).toDomain() }
        }

    override suspend fun fetchVideoPlayData(
        libraryId: Long,
        videoId: String,
        token: String?,
        expires: Long?,
    ): BunnyResult<VideoPlayData> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoGetVideoPlayData(libraryId, videoId, token, expires).toDomain()
        }
    }

    override suspend fun fetchVideoHeatmap(
        libraryId: Long,
        videoId: String,
    ): BunnyResult<Map<String, Int>> = withContext(coroutineDispatcher) {
        bunnyCatching {
            // The DTO wraps a single map; the wrapper carries nothing else worth surfacing.
            videosApi.videoGetVideoHeatmap(libraryId, videoId).heatmap.orEmpty()
        }
    }

    override suspend fun fetchVideoHeatmapData(
        libraryId: Long,
        videoId: String,
        token: String?,
        expires: Long?,
    ): BunnyResult<VideoPlayData> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoGetVideoHeatmapData(libraryId, videoId, token, expires).toDomain()
        }
    }

    override suspend fun fetchVideoStatistics(
        libraryId: Long,
        videoId: String?,
        dateFrom: String?,
        dateTo: String?,
        hourly: Boolean,
    ): BunnyResult<VideoStatistics> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoGetVideoStatistics(
                libraryId = libraryId,
                dateFrom = dateFrom,
                dateTo = dateTo,
                hourly = hourly,
                videoGuid = videoId,
            ).toDomain()
        }
    }

    override suspend fun fetchVideoResolutions(
        libraryId: Long,
        videoId: String,
    ): BunnyResult<VideoResolutionsInfo> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoGetVideoResolutions(libraryId, videoId)
                .also { requireEnvelopeSuccess(it.success, it.message, it.statusCode) }
        }.requireData { it.data?.toDomain() }
    }

    override suspend fun fetchVideoStorageSize(
        libraryId: Long,
        videoId: String,
    ): BunnyResult<VideoStorageSize> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoGetVideoStorageSize(libraryId, videoId)
                .also { requireEnvelopeSuccess(it.success, it.message, it.statusCode) }
        }.requireData { it.data?.toDomain() }
    }

    // endregion

    // region — creating and changing

    override suspend fun createVideo(
        libraryId: Long,
        request: CreateVideoRequest,
    ): BunnyResult<Video> = withContext(coroutineDispatcher) {
        bunnyCatching {
            // Everything downstream keys on the id — the camera builds its ingest URL from it, an
            // upload targets it. A 2xx that carries no guid would otherwise become
            // Ok(Video(id = "")) and publish into nothing.
            videosApi.videoCreateVideo(libraryId, request.toGenerated()).toDomain()
        }.requireVideoId()
    }

    override suspend fun updateVideo(
        libraryId: Long,
        videoId: String,
        request: UpdateVideoRequest,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoUpdateVideo(libraryId, videoId, request.toGenerated()).requireSuccess()
        }
    }

    override suspend fun deleteVideo(libraryId: Long, videoId: String): BunnyResult<Unit> =
        withContext(coroutineDispatcher) {
            bunnyCatching {
                videosApi.videoDeleteVideo(libraryId, videoId).requireSuccess()
            }
        }

    override suspend fun setThumbnail(
        libraryId: Long,
        videoId: String,
        thumbnailUrl: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            // videoSetThumbnail models the endpoint's optional octet-stream body as a nullable
            // File but still sends Content-Type: application/octet-stream, so calling it with just
            // the query parameter throws inside the generated client before any request is made.
            // Issue the POST directly — same workaround the live-stream layer needs.
            videosApi.postExpectingSuccess(
                url = "${videosApi.baseUrl}/library/$libraryId/videos/$videoId/thumbnail" +
                    "?thumbnailUrl=${URLEncoder.encode(thumbnailUrl, Charsets.UTF_8.name())}",
                failureMessage = "Failed to set the thumbnail",
            )
        }
    }

    override suspend fun uploadThumbnail(
        libraryId: Long,
        videoId: String,
        thumbnailFile: File,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoSetThumbnail(libraryId, videoId, body = thumbnailFile).requireSuccess()
        }
    }

    override suspend fun fetchNewVideo(
        libraryId: Long,
        request: FetchVideoRequest,
        collectionId: String?,
        thumbnailTime: Int?,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoFetchNewVideo(
                libraryId = libraryId,
                fetchVideoRequest = request.toGenerated(),
                collectionId = collectionId,
                thumbnailTime = thumbnailTime,
            ).requireSuccess()
        }
    }

    override suspend fun refetchVideo(
        libraryId: Long,
        videoId: String,
        request: FetchVideoRequest,
        collectionId: String?,
        enabledResolutions: List<String>?,
        lowPriority: Boolean,
        thumbnailTime: Int?,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoFetchVideo(
                libraryId = libraryId,
                videoId = videoId,
                fetchVideoRequest = request.toGenerated(),
                collectionId = collectionId,
                enabledResolutions = enabledResolutions?.joinToString(",").orEmpty(),
                lowPriority = lowPriority,
                thumbnailTime = thumbnailTime,
            ).requireSuccess()
        }
    }

    // endregion

    // region — captions

    override suspend fun addCaption(
        libraryId: Long,
        videoId: String,
        request: AddCaptionRequest,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoAddCaption(
                libraryId = libraryId,
                videoId = videoId,
                srclang = request.languageCode,
                captionModelAdd = request.toGenerated(),
            ).requireSuccess()
        }
    }

    override suspend fun deleteCaption(
        libraryId: Long,
        videoId: String,
        languageCode: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoDeleteCaption(libraryId, videoId, languageCode).requireSuccess()
        }
    }

    // endregion

    // region — encoding

    override suspend fun reencodeVideo(libraryId: Long, videoId: String): BunnyResult<Video> =
        withContext(coroutineDispatcher) {
            bunnyCatching { videosApi.videoReencodeVideo(libraryId, videoId).toDomain() }
        }

    override suspend fun reencodeUsingCodec(
        libraryId: Long,
        videoId: String,
        codec: VideoCodec,
    ): BunnyResult<Video> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoReencodeUsingCodec(libraryId, videoId, codec.toGenerated()).toDomain()
        }
    }

    override suspend fun repackageVideo(
        libraryId: Long,
        videoId: String,
        keepOriginalFiles: Boolean,
    ): BunnyResult<Video> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoRepackage(libraryId, videoId, keepOriginalFiles).toDomain()
        }
    }

    override suspend fun deleteResolutions(
        libraryId: Long,
        videoId: String,
        resolutions: List<String>,
        deleteNonConfiguredResolutions: Boolean,
        deleteMp4Files: Boolean,
        deleteOriginal: Boolean,
        deleteAllResolutions: Boolean,
        dryRun: Boolean,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoDeleteResolutions(
                libraryId = libraryId,
                videoId = videoId,
                resolutionsToDelete = resolutions.joinToString(","),
                deleteNonConfiguredResolutions = deleteNonConfiguredResolutions,
                allResolutions = deleteAllResolutions,
                deleteOriginal = deleteOriginal,
                deleteMp4Files = deleteMp4Files,
                dryRun = dryRun,
            ).requireSuccess()
        }
    }

    // endregion

    // region — AI features

    override suspend fun smartGenerate(
        libraryId: Long,
        videoId: String,
        request: SmartGenerateRequest,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoSmartGenerate(libraryId, videoId, request.toGenerated()).requireSuccess()
        }
    }

    override suspend fun transcribeVideo(
        libraryId: Long,
        videoId: String,
        request: TranscribeVideoRequest,
        force: Boolean,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            videosApi.videoTranscribeVideo(
                libraryId = libraryId,
                videoId = videoId,
                force = force,
                transcribeSettings = request.toGenerated(),
            ).requireSuccess()
        }
    }

    // endregion

    /**
     * Unwraps the API's `{ success, message, statusCode, data }` envelope.
     *
     * Two different failures arrive the same way here. When the envelope itself reports
     * `success = false`, the server has said why and with what status, so that is mapped through
     * the shared taxonomy — reporting it as a decode problem would strip a 404 down to a
     * transient error and send a polling caller round forever. A `2xx` that genuinely succeeded
     * but carries no `data` is what [BunnyError.Decode] is for — better than handing the caller an
     * empty object that looks like real data.
     */
    private inline fun <T, R> BunnyResult<T>.requireData(extract: (T) -> R?): BunnyResult<R> =
        when (this) {
            is BunnyResult.Err -> this
            is BunnyResult.Ok -> extract(value)?.let { BunnyResult.Ok(it) }
                ?: BunnyResult.Err(
                    BunnyError.Decode("The response carried no data for this video"),
                )
        }

    /**
     * The data-carrying endpoints wrap their payload in the same `{ success, ... }` envelope as
     * the bare mutations, but the generator emits a separate type per payload, so there is no
     * shared `StatusModel` to hang [net.bunny.api.error.requireSuccess] on.
     */
    private fun requireEnvelopeSuccess(success: Boolean?, message: String?, statusCode: Int?) {
        if (success != true) {
            throw ClientException(
                message = message?.takeIf { it.isNotBlank() } ?: "The request was rejected",
                statusCode = statusCode ?: 0,
            )
        }
    }

    /**
     * A created video is only useful if it has an id: the camera builds its RTMP ingest URL from
     * it and an upload targets it. `guid` is optional in the generated model, so a response
     * missing it would map to an empty id and silently publish into nothing.
     */
    private fun BunnyResult<Video>.requireVideoId(): BunnyResult<Video> = when (this) {
        is BunnyResult.Err -> this
        is BunnyResult.Ok -> if (value.id.isNotBlank()) {
            this
        } else {
            BunnyResult.Err(
                BunnyError.Decode("The video was created but the response carried no video id"),
            )
        }
    }
}

/** Maps the domain codec onto the generated enum, whose entries are named `_0`.. `_3`. */
private fun VideoCodec.toGenerated(): EncoderOutputCodec =
    EncoderOutputCodec.entries.first { it.value == value }
