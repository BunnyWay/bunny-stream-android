package net.bunny.api.livestream.data

import android.graphics.Color
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.api.ManageLiveStreamsApi
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.bunnyCatching
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.LiveStreamIngestStatus
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
 * Every call runs through [bunnyCatching], so anything the stack throws — generated
 * client exceptions, transport failures, malformed bodies — comes back as a typed
 * [net.bunny.api.error.BunnyError] inside the [BunnyResult] envelope.
 */
internal class DefaultLiveStreamRepository(
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
    ): BunnyResult<LiveStreamList> = withContext(coroutineDispatcher) {
        bunnyCatching {
            try {
                liveStreamsApi.liveStreamList(
                    libraryId = libraryId,
                    page = page,
                    itemsPerPage = itemsPerPage,
                    search = search,
                    orderBy = orderBy,
                    collectionId = collectionId,
                ).toDomain()
            } catch (e: ClientException) {
                // Bunny returns 404 for a library that has no live streams yet. That's an empty
                // list, not an error — surfacing it as "Not Found" made apps show an error state
                // on a fresh library (the iOS demo maps this the same way).
                if (e.statusCode == HTTP_NOT_FOUND) {
                    LiveStreamList(
                        totalItems = 0L,
                        currentPage = page?.toLong() ?: 1L,
                        itemsPerPage = itemsPerPage ?: 0,
                        items = emptyList(),
                    )
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun getLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream> = withContext(coroutineDispatcher) {
        bunnyCatching {
            liveStreamsApi.liveStreamGetByStreamId(libraryId, streamId).toDomain()
        }
    }

    override suspend fun pollLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream> =
        // Same call as [getLiveStream]; kept as a named entry point because the polling loop's
        // terminal-vs-transient contract is documented on it. The BunnyResult envelope carries
        // the HTTP status and terminality the old LiveStreamPollResult existed to preserve.
        getLiveStream(libraryId, streamId)

    override suspend fun fetchLiveStreamPlayData(
        libraryId: Long,
        streamId: String,
        token: String?,
        expires: Long?,
    ): BunnyResult<LiveStreamPlayData> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
    ): BunnyResult<LiveStream> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
    ): BunnyResult<LiveStream> = withContext(coroutineDispatcher) {
        bunnyCatching {
            liveStreamsApi.liveStreamStartStream(libraryId, streamId).toDomain()
        }
    }

    override suspend fun stopLiveStream(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStream> = withContext(coroutineDispatcher) {
        bunnyCatching {
            liveStreamsApi.liveStreamStopStream(libraryId, streamId).toDomain()
        }
    }

    override suspend fun setLiveStreamThumbnail(
        libraryId: Long,
        streamId: String,
        thumbnailUrl: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
                    // Mirror the generated client's exception shape so [bunnyCatching] maps it
                    // through the shared taxonomy.
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
    ): BunnyResult<List<LiveStreamThumbnail>> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
     * Executes [request] on the shared OkHttp client and throws a [ClientException] (so
     * [bunnyCatching] maps it through the shared taxonomy) on any non-2xx response.
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
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
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
     * mutation. Throw [ClientException] so [bunnyCatching] can translate it through the shared
     * taxonomy, keeping the existing error-message vocabulary.
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

    override suspend fun getLiveStreamStatus(
        libraryId: Long,
        streamId: String,
    ): BunnyResult<LiveStreamIngestStatus> = withContext(coroutineDispatcher) {
        bunnyCatching {
            val model = liveStreamsApi.liveStreamGetStreamStatus(libraryId, streamId)
            LiveStreamIngestStatus(
                readyToStart = model.readyToStart ?: false,
                primaryLive = model.primaryLive ?: false,
                backupLive = model.backupLive ?: false,
                lastPingAgoMs = model.lastPingAgo,
                durationSeconds = model.duration,
            )
        }
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
        private const val HTTP_NOT_FOUND = 404
    }
}
