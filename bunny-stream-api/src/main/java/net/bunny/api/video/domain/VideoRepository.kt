package net.bunny.api.video.domain

import net.bunny.api.error.BunnyResult
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
import java.io.File

/**
 * Everything the SDK can do with a video, in domain terms.
 *
 * Replaces reaching into the generated `videosApi`: every call is `suspend`, returns
 * [BunnyResult] and speaks [Video] rather than the generator's DTOs, so a change to the OpenAPI
 * spec cannot break an integrator's build.
 *
 * Reach it through `BunnyStreamApi.getInstance().videoRepository`.
 *
 * Shapes match [net.bunny.api.livestream.domain.LiveStreamRepository]: `libraryId` first,
 * `suspend`, `BunnyResult`. Calls that the API answers with a bare success flag return
 * `BunnyResult<Unit>` — a failure arrives as [net.bunny.api.error.BunnyError], not as `false`.
 */
public interface VideoRepository {

    // region — reading

    /**
     * Lists videos in a library.
     *
     * @param orderBy `"date"`, `"title"` or any value the API accepts.
     * @param collectionId restricts the listing to one collection.
     */
    public suspend fun listVideos(
        libraryId: Long,
        page: Int = 1,
        itemsPerPage: Int = 100,
        search: String? = null,
        orderBy: String = "date",
        collectionId: String? = null,
    ): BunnyResult<VideoList>

    /** Fetches one video's metadata. */
    public suspend fun getVideo(libraryId: Long, videoId: String): BunnyResult<Video>

    /**
     * Fetches everything needed to play the video: URLs plus the library's player configuration.
     *
     * @param token signed playback token, required when the library has token authentication on.
     * @param expires expiry that the token was signed for.
     */
    public suspend fun fetchVideoPlayData(
        libraryId: Long,
        videoId: String,
        token: String? = null,
        expires: Long? = null,
    ): BunnyResult<VideoPlayData>

    /**
     * Retention heatmap: how many viewers were still watching at each point.
     *
     * Keys are the offsets the API returns as strings; a player renders them under the timeline.
     */
    public suspend fun fetchVideoHeatmap(
        libraryId: Long,
        videoId: String,
    ): BunnyResult<Map<String, Int>>

    /**
     * Play data enriched with heatmap information, for players that draw retention inline.
     *
     * Takes the same [token]/[expires] as [fetchVideoPlayData] — a token-authenticated library
     * refuses this endpoint without them.
     */
    public suspend fun fetchVideoHeatmapData(
        libraryId: Long,
        videoId: String,
        token: String? = null,
        expires: Long? = null,
    ): BunnyResult<VideoPlayData>

    /**
     * Viewing statistics over a date range. Omit both bounds for the API's default window.
     *
     * @param videoId narrows the statistics to one video; `null` covers the whole library.
     * @param hourly aggregate per hour instead of per day.
     */
    public suspend fun fetchVideoStatistics(
        libraryId: Long,
        videoId: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        hourly: Boolean = false,
    ): BunnyResult<VideoStatistics>

    /** What renditions exist, where they live, and whether the storage layout is mixed. */
    public suspend fun fetchVideoResolutions(
        libraryId: Long,
        videoId: String,
    ): BunnyResult<VideoResolutionsInfo>

    /** Per-rendition storage breakdown, for showing where a library's quota goes. */
    public suspend fun fetchVideoStorageSize(
        libraryId: Long,
        videoId: String,
    ): BunnyResult<VideoStorageSize>

    // endregion

    // region — creating and changing

    /**
     * Creates an empty video record to upload bytes into.
     *
     * Most callers should use [net.bunny.api.upload.VideoUploader] instead, which does this as
     * part of the upload.
     */
    public suspend fun createVideo(
        libraryId: Long,
        request: CreateVideoRequest,
    ): BunnyResult<Video>

    /** Applies metadata changes; `null` fields are left as they are. */
    public suspend fun updateVideo(
        libraryId: Long,
        videoId: String,
        request: UpdateVideoRequest,
    ): BunnyResult<Unit>

    /** Deletes the video and everything derived from it. */
    public suspend fun deleteVideo(libraryId: Long, videoId: String): BunnyResult<Unit>

    /** Sets the thumbnail to an image already reachable at [thumbnailUrl]. */
    public suspend fun setThumbnail(
        libraryId: Long,
        videoId: String,
        thumbnailUrl: String,
    ): BunnyResult<Unit>

    /** Uploads [thumbnailFile] from the device as the video's thumbnail. */
    public suspend fun uploadThumbnail(
        libraryId: Long,
        videoId: String,
        thumbnailFile: File,
    ): BunnyResult<Unit>

    /**
     * Has Bunny fetch the video from a URL rather than the device uploading it.
     *
     * @param collectionId collection to place the fetched video in.
     * @param thumbnailTime offset in milliseconds to grab the thumbnail from.
     */
    public suspend fun fetchNewVideo(
        libraryId: Long,
        request: FetchVideoRequest,
        collectionId: String? = null,
        thumbnailTime: Int? = null,
    ): BunnyResult<Unit>

    /**
     * Re-fetches an existing video from a source URL, replacing its content.
     *
     * @param enabledResolutions restricts which renditions are produced, e.g.
     *   `listOf("360p", "720p")`; `null` uses the library's configuration.
     * @param lowPriority queues the fetch behind normal-priority work.
     */
    public suspend fun refetchVideo(
        libraryId: Long,
        videoId: String,
        request: FetchVideoRequest,
        collectionId: String? = null,
        enabledResolutions: List<String>? = null,
        lowPriority: Boolean = false,
        thumbnailTime: Int? = null,
    ): BunnyResult<Unit>

    // endregion

    // region — captions

    /** Adds or replaces a subtitle track for [AddCaptionRequest.languageCode]. */
    public suspend fun addCaption(
        libraryId: Long,
        videoId: String,
        request: AddCaptionRequest,
    ): BunnyResult<Unit>

    /** Removes the subtitle track for [languageCode]. */
    public suspend fun deleteCaption(
        libraryId: Long,
        videoId: String,
        languageCode: String,
    ): BunnyResult<Unit>

    // endregion

    // region — encoding

    /** Re-encodes the video with the library's current settings. */
    public suspend fun reencodeVideo(libraryId: Long, videoId: String): BunnyResult<Video>

    /** Re-encodes using a specific [VideoCodec]. */
    public suspend fun reencodeUsingCodec(
        libraryId: Long,
        videoId: String,
        codec: VideoCodec,
    ): BunnyResult<Video>

    /** Rebuilds the playlists and containers without re-encoding the renditions. */
    public suspend fun repackageVideo(
        libraryId: Long,
        videoId: String,
        keepOriginalFiles: Boolean = true,
    ): BunnyResult<Video>

    /**
     * Deletes renditions to reclaim storage. **Irreversible** — the only way back is a re-encode,
     * and [deleteOriginal] removes the master the re-encode would start from.
     *
     * @param resolutions renditions to remove, e.g. `listOf("240p", "360p")`.
     * @param deleteNonConfiguredResolutions also removes renditions the library no longer produces.
     * @param deleteMp4Files also removes the MP4 fallback files for those renditions. Playback
     *   falls back to MP4 when HLS is unavailable, so removing them narrows compatibility.
     * @param deleteOriginal also removes the uploaded master file. After this the video can never
     *   be re-encoded or repackaged.
     * @param deleteAllResolutions ignores [resolutions] and removes every rendition.
     * @param dryRun asks the server what *would* be deleted without deleting it. Prefer running
     *   this first for anything destructive.
     */
    public suspend fun deleteResolutions(
        libraryId: Long,
        videoId: String,
        resolutions: List<String>,
        deleteNonConfiguredResolutions: Boolean = false,
        deleteMp4Files: Boolean = false,
        deleteOriginal: Boolean = false,
        deleteAllResolutions: Boolean = false,
        dryRun: Boolean = false,
    ): BunnyResult<Unit>

    // endregion

    // region — AI features

    /** Runs Bunny's AI pass to fill in title, description, chapters or moments. */
    public suspend fun smartGenerate(
        libraryId: Long,
        videoId: String,
        request: SmartGenerateRequest,
    ): BunnyResult<Unit>

    /**
     * Requests transcription, and optionally the same AI-generated metadata.
     *
     * @param force re-transcribes a video that already has a transcription instead of returning
     *   the existing one.
     */
    public suspend fun transcribeVideo(
        libraryId: Long,
        videoId: String,
        request: TranscribeVideoRequest,
        force: Boolean = false,
    ): BunnyResult<Unit>

    // endregion
}
