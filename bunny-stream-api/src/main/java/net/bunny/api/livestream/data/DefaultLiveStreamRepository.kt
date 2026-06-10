package net.bunny.api.livestream.data

import android.graphics.Color
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.api.ManageLiveStreamsApi
import net.bunny.api.livestream.domain.LiveStreamPollResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.LiveStreamList
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.livestream.domain.model.RtmpOutput
import net.bunny.api.model.LiveStreamStatus
import net.bunny.api.settings.PlaybackSpeedManager
import net.bunny.api.settings.toColorOrDefault
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException
import org.openapitools.client.models.LiveStreamModel
import org.openapitools.client.models.LiveStreamPlayDataModel
import org.openapitools.client.models.LiveStreamPlayDataModelLiveStream
import org.openapitools.client.models.PaginationListOfLiveStreamModel
import org.openapitools.client.models.StatusModel
import org.openapitools.client.models.RtmpOutput as GeneratedRtmpOutput
// Generated request wrappers — aliased to avoid clashing with the domain
// [LiveStreamCreateRequest] above.
import org.openapitools.client.models.LiveStreamCreateRequest as GeneratedLiveStreamCreateRequest
import org.openapitools.client.models.LiveStreamUpdateRequest as GeneratedLiveStreamUpdateRequest

/**
 * Default [LiveStreamRepository] backed by the generated [ManageLiveStreamsApi].
 *
 * Error mapping mirrors [net.bunny.api.settings.data.DefaultSettingsRepository]: any non-2xx
 * response is translated into a human-readable [Either.Left] string keyed by HTTP status code.
 */
class DefaultLiveStreamRepository(
    private val liveStreamsApi: ManageLiveStreamsApi,
    private val coroutineDispatcher: CoroutineDispatcher,
) : LiveStreamRepository {

    override suspend fun listLiveStreams(
        libraryId: Long,
        page: Int?,
        itemsPerPage: Int?,
        search: String?,
        orderBy: String?,
        collectionId: String?,
    ): Either<String, LiveStreamList> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamList(
                libraryId = libraryId,
                page = page,
                itemsPerPage = itemsPerPage,
                search = search,
                orderBy = orderBy,
                collectionId = collectionId,
            ).toDomain()
        }
    }

    override suspend fun getLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStream> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamGetByStreamId(libraryId, streamId).toDomain()
        }
    }

    override suspend fun pollLiveStream(
        libraryId: Long,
        streamId: String,
    ): LiveStreamPollResult = withContext(coroutineDispatcher) {
        // Bypass [runApi]'s String collapse so we can preserve the HTTP status code — the polling
        // loop needs to distinguish terminal (401/403/404/410) from transient (5xx/network) before
        // deciding whether to keep polling. Generated [ClientException]/[ServerException] both
        // carry [statusCode]; transport errors get [statusCode] = 0 so the caller can treat them
        // the same as 5xx.
        try {
            LiveStreamPollResult.Success(
                liveStreamsApi.liveStreamGetByStreamId(libraryId, streamId).toDomain()
            )
        } catch (e: ClientException) {
            LiveStreamPollResult.Failure(
                statusCode = e.statusCode,
                message = httpErrorMessage(e.statusCode, e.message),
            )
        } catch (e: ServerException) {
            LiveStreamPollResult.Failure(
                statusCode = e.statusCode,
                message = httpErrorMessage(e.statusCode, e.message),
            )
        } catch (e: Exception) {
            LiveStreamPollResult.Failure(
                statusCode = 0,
                message = "Network error: ${e.message ?: e::class.simpleName}",
            )
        }
    }

    override suspend fun fetchLiveStreamPlayData(
        libraryId: Long,
        streamId: String,
        token: String?,
        expires: Long?,
    ): Either<String, LiveStreamPlayData> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamGetStreamPlayData(
                libraryId = libraryId,
                streamId = streamId,
                token = token,
                expires = expires,
            ).toDomain()
        }
    }

    override suspend fun createLiveStream(
        libraryId: Long,
        request: LiveStreamCreateRequest,
    ): Either<String, LiveStream> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamCreate(
                libraryId = libraryId,
                liveStreamCreateRequest = request.toCreateDto(),
            ).toDomain()
        }
    }

    override suspend fun updateLiveStream(
        libraryId: Long,
        streamId: String,
        request: LiveStreamCreateRequest,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        runApi {
            // PUT — the official preview API echoes the updated LiveStreamModel back; callers
            // that need it can re-fetch, so the result is intentionally discarded here.
            liveStreamsApi.liveStreamUpdate(
                libraryId = libraryId,
                streamId = streamId,
                liveStreamUpdateRequest = request.toUpdateDto(),
            )
            Unit
        }
    }

    override suspend fun startLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStream> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamStartStream(libraryId, streamId).toDomain()
        }
    }

    override suspend fun stopLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, LiveStream> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamStopStream(libraryId, streamId).toDomain()
        }
    }

    override suspend fun setLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        thumbnailUrl: String,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamSetThumbnail(
                libraryId = libraryId,
                streamId = streamId,
                thumbnailUrl = thumbnailUrl,
            ).requireSuccess()
        }
    }

    override suspend fun uploadLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        imageBytes: ByteArray,
        contentType: String,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        runApi {
            // The generated client only models the `thumbnailUrl` query variant, so issue the
            // binary upload directly — reusing the same OkHttp client, base URL and AccessKey the
            // generated [ManageLiveStreamsApi] is configured with.
            val url = "${liveStreamsApi.baseUrl}/library/$libraryId/live/$streamId/thumbnail"
            val requestBuilder = Request.Builder()
                .url(url)
                .post(imageBytes.toRequestBody(contentType.toMediaTypeOrNull()))
                .header("Accept", "application/json")
            ApiClient.apiKey["AccessKey"]?.let { requestBuilder.header("AccessKey", it) }

            liveStreamsApi.client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    // Mirror the generated client's exception shape so [runApi] maps it to the
                    // shared error vocabulary.
                    throw ClientException(
                        message = response.body?.string()?.takeIf { it.isNotBlank() }
                            ?: "Thumbnail upload failed",
                        statusCode = response.code,
                    )
                }
            }
            Unit
        }
    }

    override suspend fun deleteLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        runApi {
            // Official preview API echoes the deleted LiveStreamModel back; discard it.
            liveStreamsApi.liveStreamDelete(
                libraryId = libraryId,
                streamId = streamId,
            )
            Unit
        }
    }

    /**
     * The Manage Live Streams API returns HTTP 200 with `success=false` when something like a
     * validation error happens, so a successful HTTP exchange is not the same as a successful
     * mutation. Throw [ClientException] so [runApi] can translate it to [Either.Left] using the
     * existing error-message vocabulary.
     */
    private fun StatusModel.requireSuccess() {
        if (success != true) {
            throw ClientException(
                message = message ?: "Live stream operation failed",
                statusCode = statusCode ?: 0,
            )
        }
    }

    private fun LiveStreamCreateRequest.toCreateDto(): GeneratedLiveStreamCreateRequest =
        GeneratedLiveStreamCreateRequest(
            title = title,
            description = description,
            collectionId = collectionId,
            `public` = isPublic,
            scheduledStartTime = scheduledStartTime,
            scheduledEndTime = scheduledEndTime,
            dvrEnabled = dvrEnabled,
            dvrWindowSeconds = dvrWindowSeconds,
            recordVod = recordVod,
            enableCountdown = enableCountdown,
            preStreamTrailerVideoId = preStreamTrailerVideoId,
        )

    private fun LiveStreamCreateRequest.toUpdateDto(): GeneratedLiveStreamUpdateRequest =
        GeneratedLiveStreamUpdateRequest(
            title = title,
            description = description,
            collectionId = collectionId,
            `public` = isPublic,
            scheduledStartTime = scheduledStartTime,
            scheduledEndTime = scheduledEndTime,
            dvrEnabled = dvrEnabled,
            dvrWindowSeconds = dvrWindowSeconds,
            recordVod = recordVod,
            enableCountdown = enableCountdown,
            preStreamTrailerVideoId = preStreamTrailerVideoId,
        )

    /**
     * Centralised mapper for the OpenAPI generator's exceptions. Matches the status-code-to-message
     * mapping used in `DefaultSettingsRepository` so the SDK has a single error vocabulary.
     */
    private inline fun <T> runApi(block: () -> T): Either<String, T> = try {
        Either.Right(block())
    } catch (e: ClientException) {
        Either.Left(httpErrorMessage(e.statusCode, e.message))
    } catch (e: ServerException) {
        Either.Left(httpErrorMessage(e.statusCode, e.message))
    } catch (e: Exception) {
        e.printStackTrace()
        Either.Left("Unknown exception: ${e.message}")
    }

    private fun httpErrorMessage(statusCode: Int, fallback: String?): String = when (statusCode) {
        HTTP_UNAUTHORIZED -> "Authorization required Unauthorized"
        HTTP_FORBIDDEN -> "Forbidden"
        HTTP_NOT_FOUND -> "Not Found"
        else -> fallback ?: "Error: $statusCode"
    }

    // region — DTO -> domain mapping

    private fun PaginationListOfLiveStreamModel.toDomain(): LiveStreamList = LiveStreamList(
        totalItems = totalItems ?: 0L,
        currentPage = currentPage ?: 0L,
        itemsPerPage = itemsPerPage ?: 0,
        items = items.orEmpty().map { it.toDomain() },
    )

    private fun LiveStreamModel.toDomain(): LiveStream = LiveStream(
        id = guid.orEmpty(),
        videoLibraryId = videoLibraryId ?: 0L,
        title = title.orEmpty(),
        description = description,
        category = category,
        collectionId = collectionId,
        isPublic = `public` ?: false,
        status = status ?: LiveStreamStatus.UNKNOWN,
        dateCreated = dateCreated.orEmpty(),
        scheduledStartTime = scheduledStartTime,
        scheduledEndTime = scheduledEndTime,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        streamKey = streamKey,
        playbackUrlHls = playbackUrlHls,
        dvrEnabled = dvrEnabled ?: false,
        dvrWindowSeconds = dvrWindowSeconds,
        recordVod = recordVod ?: false,
        availableResolutions = availableResolutions,
        width = width,
        height = height,
        framerate = framerate,
        ingestRegion = ingestRegion,
        peakConcurrentViewers = peakConcurrentViewers,
        totalViewerSeconds = totalViewerSeconds,
        thumbnailFileName = thumbnailFileName,
        thumbnailUpdatedAt = thumbnailUpdatedAt,
        enableCountdown = enableCountdown,
        rtmpOutputs = rtmpOutputs.orEmpty().map { it.toDomain() },
        preStreamTrailerVideoId = preStreamTrailerVideoId,
    )

    private fun LiveStreamPlayDataModelLiveStream.toDomain(): LiveStream = LiveStream(
        id = guid.orEmpty(),
        videoLibraryId = videoLibraryId ?: 0L,
        title = title.orEmpty(),
        description = description,
        category = category,
        collectionId = collectionId,
        isPublic = `public` ?: false,
        status = status ?: LiveStreamStatus.UNKNOWN,
        dateCreated = dateCreated.orEmpty(),
        scheduledStartTime = scheduledStartTime,
        scheduledEndTime = scheduledEndTime,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        streamKey = streamKey,
        playbackUrlHls = playbackUrlHls,
        dvrEnabled = dvrEnabled ?: false,
        dvrWindowSeconds = dvrWindowSeconds,
        recordVod = recordVod ?: false,
        availableResolutions = availableResolutions,
        width = width,
        height = height,
        framerate = framerate,
        ingestRegion = ingestRegion,
        peakConcurrentViewers = peakConcurrentViewers,
        totalViewerSeconds = totalViewerSeconds,
        thumbnailFileName = thumbnailFileName,
        thumbnailUpdatedAt = thumbnailUpdatedAt,
        enableCountdown = enableCountdown,
        rtmpOutputs = rtmpOutputs.orEmpty().map { it.toDomain() },
        preStreamTrailerVideoId = preStreamTrailerVideoId,
    )

    private fun GeneratedRtmpOutput.toDomain(): RtmpOutput = RtmpOutput(
        // The OpenAPI generator emits `java.net.URI` for `format: uri` — flatten back to String
        // so the domain model stays Android-friendly and Java-net-agnostic.
        endpoint = endpoint?.toString(),
        streamKey = streamKey,
    )

    private fun LiveStreamPlayDataModel.toDomain(): LiveStreamPlayData = LiveStreamPlayData(
        liveStream = liveStream?.toDomain(),
        libraryName = libraryName,
        captionsPath = captionsPath,
        seekPath = seekPath,
        thumbnailUrl = thumbnailUrl,
        fallbackUrl = fallbackUrl,
        videoPlaylistUrl = videoPlaylistUrl,
        originalUrl = originalUrl,
        previewUrl = previewUrl,
        controls = controls.orEmpty(),
        enableDRM = enableDRM ?: false,
        drmVersion = drmVersion ?: 0,
        keyColor = playerKeyColor?.toColorOrDefault(Color.WHITE) ?: Color.WHITE,
        vastTagUrl = vastTagUrl,
        captionsFontSize = captionsFontSize ?: 0,
        captionsFontColor = captionsFontColor?.toColorOrDefault(null),
        captionsBackgroundColor = captionsBackground?.toColorOrDefault(null),
        uiLanguage = uiLanguage,
        allowEarlyPlay = allowEarlyPlay ?: false,
        tokenAuthEnabled = tokenAuthEnabled ?: false,
        enableMP4Fallback = enableMP4Fallback ?: false,
        showHeatmap = showHeatmap ?: false,
        fontFamily = fontFamily,
        playbackSpeeds = PlaybackSpeedManager().parsePlaybackSpeeds(playbackSpeeds),
        widevineMinClientSecurityLevel = widevineMinClientSecurityLevel,
        zoneTier = zoneTier,
        rememberPlayerPosition = rememberPlayerPosition ?: false,
        enableCompactControls = enableCompactControls ?: false,
    )

    // endregion

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
    }
}
