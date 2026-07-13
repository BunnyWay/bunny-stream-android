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
import net.bunny.api.livestream.domain.model.LiveStreamThumbnail
import net.bunny.api.livestream.domain.model.RtmpOutput
import net.bunny.api.model.LiveStreamStatus
import net.bunny.api.settings.PlaybackSpeedManager
import net.bunny.api.settings.toColorOrDefault
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.ClientError
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ResponseType
import org.openapitools.client.infrastructure.ServerError
import org.openapitools.client.infrastructure.ServerException
import org.openapitools.client.models.LiveStreamModel
import org.openapitools.client.models.LiveStreamPlayDataModel
import org.openapitools.client.models.LiveStreamPlayDataModelLiveStream
import org.openapitools.client.models.PaginationListOfLiveStreamModel
import org.openapitools.client.models.StatusModel
import org.openapitools.client.models.ThumbnailListResponseModel
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

    /**
     * CDN base (`scheme://host`) learned best-effort from a stream's `playbackUrlHls` in the DTO
     * mappers. Used to turn the Get-Thumbnails endpoint's *relative* paths into absolute, loadable
     * URLs; falls back to the relative path when still unknown.
     */
    @Volatile
    private var cdnBaseUrl: String? = null

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
            // The generated liveStreamSetThumbnail models the endpoint's optional octet-stream body
            // and throws ("requestBody currently only supports JSON body, byte body and File body")
            // when called with just the thumbnailUrl query and no body. Issue the POST directly —
            // same pattern as [uploadLiveStreamThumbnail] — with the URL as a query param and an
            // empty body, reusing the generated client's base URL, OkHttp client and AccessKey.
            val url = setThumbnailRequestUrl(liveStreamsApi.baseUrl, libraryId, streamId, thumbnailUrl)
            val requestBuilder = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody(null))
                .header("Accept", "application/json")
            ApiClient.apiKey["AccessKey"]?.let { requestBuilder.header("AccessKey", it) }
            executeExpectingSuccess(requestBuilder.build(), "Failed to set thumbnail")
            Unit
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

    override suspend fun listLiveStreamThumbnails(
        libraryId: Long,
        streamId: String,
        limit: Int?,
        from: String?,
        to: String?,
    ): Either<String, List<LiveStreamThumbnail>> = withContext(coroutineDispatcher) {
        runApi {
            liveStreamsApi.liveStreamGetThumbnails(
                libraryId = libraryId,
                streamId = streamId,
                limit = limit,
                from = from,
                to = to,
            ).map { it.toDomain() }
        }
    }

    override suspend fun deleteLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        restoreLibraryDefault: Boolean,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        runApi {
            // The generated liveStreamDeleteThumbnail returns Unit on 2xx (no body cast), so unlike
            // liveStreamDelete it's safe to call directly.
            liveStreamsApi.liveStreamDeleteThumbnail(
                libraryId = libraryId,
                streamId = streamId,
                restoreLibraryDefault = restoreLibraryDefault,
            )
            Unit
        }
    }

    /**
     * Executes [request] on the shared OkHttp client and throws a [ClientException] (so [runApi]
     * maps it to the shared error vocabulary) on any non-2xx response.
     */
    private fun executeExpectingSuccess(request: Request, failureMessage: String) {
        liveStreamsApi.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ClientException(
                    message = response.body?.string()?.takeIf { it.isNotBlank() } ?: failureMessage,
                    statusCode = response.code,
                )
            }
        }
    }

    override suspend fun deleteLiveStream(
        libraryId: Long,
        streamId: String,
    ): Either<String, Unit> = withContext(coroutineDispatcher) {
        runApi {
            // The delete endpoint returns 2xx with an empty body, but the generated
            // liveStreamDelete() blindly casts that null body to a non-null LiveStreamModel and
            // throws NPE. Use the *WithHttpInfo variant and treat any 2xx as success without
            // touching the (absent) body.
            val response = liveStreamsApi.liveStreamDeleteWithHttpInfo(
                libraryId = libraryId,
                streamId = streamId,
            )
            when (response.responseType) {
                ResponseType.Success -> Unit
                ResponseType.ClientError -> {
                    val err = response as ClientError<*>
                    throw ClientException(
                        "Client error : ${err.statusCode} ${err.message.orEmpty()}",
                        err.statusCode,
                        response,
                    )
                }

                ResponseType.ServerError -> {
                    val err = response as ServerError<*>
                    throw ServerException(
                        "Server error : ${err.statusCode} ${err.message.orEmpty()}",
                        err.statusCode,
                        response,
                    )
                }

                else -> throw ClientException(
                    "Unexpected response deleting live stream: ${response.responseType}",
                    statusCode = 0,
                )
            }
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
            rtmpOutputs = rtmpOutputs?.map { it.toGeneratedDto() },
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
            rtmpOutputs = rtmpOutputs?.map { it.toGeneratedDto() },
        )

    /**
     * Domain [RtmpOutput] -> generated DTO. The generated `endpoint` is a [java.net.URI]
     * (`format: uri`); a blank or malformed endpoint is sent as null rather than crashing.
     */
    private fun RtmpOutput.toGeneratedDto(): GeneratedRtmpOutput = GeneratedRtmpOutput(
        endpoint = endpoint?.takeIf { it.isNotBlank() }
            ?.let { runCatching { java.net.URI(it) }.getOrNull() },
        streamKey = streamKey?.takeIf { it.isNotBlank() },
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

    /** Best-effort capture of the CDN base from a stream's playback URL (see [cdnBaseUrl]). */
    private fun captureCdnBase(playbackUrlHls: String?) {
        cdnBaseFromPlaybackUrl(playbackUrlHls)?.let { cdnBaseUrl = it }
    }

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
        primaryIngestUrl = ingestEndpoints?.rtmp?.primaryIngestUrl,
        backupIngestUrl = ingestEndpoints?.rtmp?.backupIngestUrl,
    ).also { captureCdnBase(it.playbackUrlHls) }

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
        primaryIngestUrl = ingestEndpoints?.rtmp?.primaryIngestUrl,
        backupIngestUrl = ingestEndpoints?.rtmp?.backupIngestUrl,
    ).also { captureCdnBase(it.playbackUrlHls) }

    private fun ThumbnailListResponseModel.toDomain(): LiveStreamThumbnail = LiveStreamThumbnail(
        // The endpoint returns paths relative to the library CDN host; make them absolute so they're
        // directly loadable (see [cdnBaseUrl]). Falls back to the raw path when the base is unknown.
        url = resolveThumbnailUrl(cdnBaseUrl, url),
        timestamp = timestamp,
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
