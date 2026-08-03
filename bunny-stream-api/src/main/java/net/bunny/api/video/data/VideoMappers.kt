package net.bunny.api.video.data

import android.graphics.Color
import net.bunny.api.model.VideoModelStatus
import net.bunny.api.settings.PlaybackSpeedManager
import net.bunny.api.settings.toColorOrDefault
import net.bunny.api.video.domain.model.Caption
import net.bunny.api.video.domain.model.CodecRenditionSize
import net.bunny.api.video.domain.model.AddCaptionRequest
import net.bunny.api.video.domain.model.CreateVideoRequest
import net.bunny.api.video.domain.model.FetchVideoRequest
import net.bunny.api.video.domain.model.ResolutionReference
import net.bunny.api.video.domain.model.SmartGenerateRequest
import net.bunny.api.video.domain.model.StorageObject
import net.bunny.api.video.domain.model.TranscribeVideoRequest
import net.bunny.api.video.domain.model.UpdateVideoRequest
import net.bunny.api.video.domain.model.VideoResolutionsInfo
import net.bunny.api.video.domain.model.VideoStatistics
import net.bunny.api.video.domain.model.VideoStorageSize
import net.bunny.api.video.domain.model.Chapter
import net.bunny.api.video.domain.model.MetaTag
import net.bunny.api.video.domain.model.Moment
import net.bunny.api.video.domain.model.SmartGenerateFeatures
import net.bunny.api.video.domain.model.TranscodingIssue
import net.bunny.api.video.domain.model.TranscodingMessage
import net.bunny.api.video.domain.model.TranscodingSeverity
import net.bunny.api.video.domain.model.Video
import net.bunny.api.video.domain.model.VideoList
import net.bunny.api.video.domain.model.VideoPlayData
import org.openapitools.client.models.CaptionModel
import org.openapitools.client.models.CodecRenditionSizeModel
import org.openapitools.client.models.ResolutionReference as GeneratedResolutionReference
import org.openapitools.client.models.VideoResolutionsInfoModel
import org.openapitools.client.models.VideoStorageSizeModel
import org.openapitools.client.models.StorageObjectModel
import org.openapitools.client.models.CaptionModelAdd
import org.openapitools.client.models.CreateVideoModel
import org.openapitools.client.models.FetchVideoRequest as GeneratedFetchVideoRequest
import org.openapitools.client.models.SmartGenerateModel
import org.openapitools.client.models.VideoStatisticsModel
import org.openapitools.client.models.TranscribeSettings
import org.openapitools.client.models.UpdateVideoModel
import org.openapitools.client.models.ChapterModel
import org.openapitools.client.models.MetaTagModel
import org.openapitools.client.models.MomentModel
import org.openapitools.client.models.PaginationListOfVideoModel
import org.openapitools.client.models.TranscodingMessageModel
import org.openapitools.client.models.VideoModel
import org.openapitools.client.models.SmartGenerateFeaturesStatusModel
import org.openapitools.client.models.VideoPlayDataModel

/**
 * Generated DTO → domain mapping for the video surface.
 *
 * Two rules run through all of it:
 *
 *  * **The generator marks every field optional; the API does not.** A video always has an id, a
 *    library, a title and a status, so those become non-null here with a defined fallback. Fields
 *    that are genuinely absent — a description nobody wrote, dimensions before transcoding — stay
 *    nullable.
 *  * **Comma-separated API strings become lists**, so a caller building a quality picker does not
 *    parse strings.
 *
 * Up to openapi-generator 7.6 the play-data endpoint got its own field-for-field copy of
 * [VideoModel], which meant two mappers that could drift. 7.24 reuses the one model, so there is
 * a single mapper again.
 */

// region — shared conversions

/** Splits an API list-in-a-string, dropping blanks. `null` and `""` both give an empty list. */
internal fun String?.toCommaSeparatedList(): List<String> =
    this?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

internal fun CaptionModel.toDomain(): Caption = Caption(
    languageCode = srclang,
    label = label,
    version = version,
)

internal fun ChapterModel.toDomain(): Chapter = Chapter(
    title = title,
    startSeconds = start,
    endSeconds = end,
)

internal fun MomentModel.toDomain(): Moment = Moment(
    label = label,
    timestampSeconds = timestamp,
)

internal fun MetaTagModel.toDomain(): MetaTag = MetaTag(
    property = property,
    value = value,
)

/**
 * The generated `Severity` and `IssueCodes` enums are named `_0`.. `_n` with their meaning only in
 * a doc comment, so the domain enums are resolved from the numeric value instead of the name.
 */
internal fun TranscodingMessageModel.toDomain(): TranscodingMessage = TranscodingMessage(
    timestamp = timeStamp,
    severity = TranscodingSeverity.from(level?.value),
    issue = TranscodingIssue.from(issueCode?.value),
    message = message,
    value = value,
)

internal fun SmartGenerateFeaturesStatusModel.toDomain(): SmartGenerateFeatures =
    SmartGenerateFeatures(
        title = title,
        description = description,
        chapters = chapters,
        moments = moments,
    )



// endregion

internal fun VideoModel.toDomain(): Video = Video(
    id = guid.orEmpty(),
    videoLibraryId = videoLibraryId ?: 0L,
    title = title.orEmpty(),
    description = description,
    collectionId = collectionId?.takeIf { it.isNotBlank() },
    category = category,
    dateUploaded = dateUploaded,
    isPublic = isPublic ?: false,
    status = status ?: VideoModelStatus.CREATED,
    lengthSeconds = length ?: 0,
    width = width?.takeIf { it > 0 },
    height = height?.takeIf { it > 0 },
    framerate = framerate?.takeIf { it > 0 },
    rotation = rotation,
    availableResolutions = availableResolutions.toCommaSeparatedList(),
    outputCodecs = outputCodecs.toCommaSeparatedList(),
    hasMp4Fallback = hasMP4Fallback ?: false,
    jitEncodingEnabled = jitEncodingEnabled ?: false,
    storageSizeBytes = storageSize ?: 0L,
    encodeProgress = encodeProgress ?: 0,
    hasOriginal = hasOriginal ?: false,
    originalHash = originalHash,
    hasHighQualityPreview = hasHighQualityPreview ?: false,
    thumbnailCount = thumbnailCount ?: 0,
    thumbnailFileName = thumbnailFileName,
    thumbnailBlurhash = thumbnailBlurhash,
    views = views ?: 0L,
    averageWatchTimeSeconds = averageWatchTime ?: 0L,
    totalWatchTimeSeconds = totalWatchTime ?: 0L,
    captions = captions?.map { it.toDomain() }.orEmpty(),
    chapters = chapters?.map { it.toDomain() }.orEmpty(),
    moments = moments?.map { it.toDomain() }.orEmpty(),
    metaTags = metaTags?.map { it.toDomain() }.orEmpty(),
    transcodingMessages = transcodingMessages?.map { it.toDomain() }.orEmpty(),
    smartGenerateStatus = smartGenerateStatus,
    smartGenerateFeatures = smartGenerateFeaturesStatus?.toDomain(),
)

internal fun VideoStatisticsModel.toDomain(): VideoStatistics = VideoStatistics(
    viewsChart = viewsChart.orEmpty(),
    watchTimeChart = watchTimeChart.orEmpty(),
    countryViewCounts = countryViewCounts.orEmpty(),
    countryWatchTime = countryWatchTime.orEmpty(),
    engagementScore = engagementScore ?: 0,
)

internal fun VideoStorageSizeModel.toDomain(): VideoStorageSize =
    VideoStorageSize(
        encoded = encoded.orEmpty().mapValues { (_, rendition) -> rendition.toDomain() },
        thumbnailsBytes = thumbnails ?: 0L,
        previewsBytes = previews ?: 0L,
        originalsBytes = originals ?: 0L,
        mp4FallbackBytes = mp4Fallback ?: 0L,
        miscellaneousBytes = miscellaneous ?: 0L,
        calculatedAt = calculatedAt,
    )

internal fun CodecRenditionSizeModel.toDomain(): CodecRenditionSize = CodecRenditionSize(
    codec = codec,
    resolution = resolution,
    sizeBytes = propertySize ?: 0L,
)

internal fun VideoResolutionsInfoModel.toDomain(): VideoResolutionsInfo =
    VideoResolutionsInfo(
        videoId = videoId.orEmpty(),
        videoLibraryId = videoLibraryId ?: 0L,
        availableResolutions = availableResolutions.orEmpty(),
        configuredResolutions = configuredResolutions.orEmpty(),
        playlistResolutions = playlistResolutions?.map { it.toDomain() }.orEmpty(),
        storageResolutions = storageResolutions?.map { it.toDomain() }.orEmpty(),
        mp4Resolutions = mp4Resolutions?.map { it.toDomain() }.orEmpty(),
        storageObjects = storageObjects?.map { it.toDomain() }.orEmpty(),
        oldResolutions = oldResolutions?.map { it.toDomain() }.orEmpty(),
        hasBothOldAndNewResolutionFormat = hasBothOldAndNewResolutionFormat ?: false,
        hasOriginal = hasOriginal ?: false,
    )

internal fun GeneratedResolutionReference.toDomain(): ResolutionReference = ResolutionReference(
    resolution = resolution,
    path = path,
)

internal fun StorageObjectModel.toDomain(): StorageObject = StorageObject(
    id = guid,
    storageZoneName = storageZoneName,
    storageZoneId = storageZoneId,
    path = path,
    objectName = objectName,
    lengthBytes = length ?: 0L,
    dateCreated = dateCreated,
    lastChanged = lastChanged,
    isDirectory = isDirectory ?: false,
    contentType = contentType,
    serverId = serverId,
    userId = userId,
    checksum = checksum,
    replicatedZones = replicatedZones,
)

// region — domain → generated (request bodies)

internal fun CreateVideoRequest.toGenerated(): CreateVideoModel = CreateVideoModel(
    title = title,
    collectionId = collectionId,
    thumbnailTime = thumbnailTime,
)

internal fun UpdateVideoRequest.toGenerated(): UpdateVideoModel = UpdateVideoModel(
    title = title,
    collectionId = collectionId,
    chapters = chapters?.map { ChapterModel(title = it.title, start = it.startSeconds, end = it.endSeconds) },
    moments = moments?.map { MomentModel(label = it.label, timestamp = it.timestampSeconds) },
    metaTags = metaTags?.map { MetaTagModel(property = it.property, value = it.value) },
)

internal fun AddCaptionRequest.toGenerated(): CaptionModelAdd = CaptionModelAdd(
    srclang = languageCode,
    label = label,
    captionsFile = captionsFileBase64,
)

internal fun FetchVideoRequest.toGenerated(): GeneratedFetchVideoRequest = GeneratedFetchVideoRequest(
    url = url,
    headers = headers,
    title = title,
)

internal fun SmartGenerateRequest.toGenerated(): SmartGenerateModel =
    SmartGenerateModel(
        generateTitle = generateTitle,
        generateDescription = generateDescription,
        generateChapters = generateChapters,
        generateMoments = generateMoments,
        sourceLanguage = sourceLanguage,
    )

internal fun TranscribeVideoRequest.toGenerated(): TranscribeSettings =
    TranscribeSettings(
        targetLanguages = targetLanguages,
        generateTitle = generateTitle,
        generateDescription = generateDescription,
        generateChapters = generateChapters,
        generateMoments = generateMoments,
        sourceLanguage = sourceLanguage,
    )

// endregion

internal fun PaginationListOfVideoModel.toDomain(): VideoList = VideoList(
    totalItems = totalItems ?: 0L,
    currentPage = currentPage ?: 1L,
    itemsPerPage = itemsPerPage ?: 0,
    items = items?.map { it.toDomain() }.orEmpty(),
)

/**
 * Colors are decoded to Android `Color` ints and playback speeds parsed to floats here, exactly as
 * the live-stream and player-settings mappers do — a player must not have to care which endpoint
 * its configuration came from.
 */
internal fun VideoPlayDataModel.toDomain(): VideoPlayData = VideoPlayData(
    video = video?.toDomain(),
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
    viAiPublisherId = viAiPublisherId,
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
    isPlayable = isPlayable ?: false,
    isPlaylistPlayable = isPlaylistPlayable ?: false,
    preferredPlaybackSource = preferredPlaybackSource,
    rememberPlayerPosition = rememberPlayerPosition ?: false,
    customCss = customCss,
    exposeVideoMetadata = exposeVideoMetadata ?: false,
    enableCompactControls = enableCompactControls ?: false,
)
