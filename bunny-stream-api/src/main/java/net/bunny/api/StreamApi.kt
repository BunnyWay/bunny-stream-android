package net.bunny.api

import net.bunny.api.collection.domain.CollectionRepository
import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.settings.domain.SettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.upload.VideoUploader
import net.bunny.api.video.domain.VideoRepository

/**
 * The SDK's entry point: everything reachable from `BunnyStreamApi.getInstance()`.
 *
 * Every surface here speaks domain models and returns [BunnyResult]. The generated OpenAPI client
 * that backs them is an implementation detail — before 4.0.0 it was exposed directly, which meant
 * a change to Bunny's spec could break an integrator's build without anyone touching their code.
 */
interface StreamApi {
    /**
     * Managing videos: listing, metadata, playback data, captions, encoding, statistics.
     * @see VideoRepository
     */
    val videoRepository: VideoRepository

    /**
     * Managing collections — the named groupings videos belong to.
     * @see CollectionRepository
     */
    val collectionRepository: CollectionRepository

    /**
     * Uploads a video in a single request. Simple, but an interrupted upload starts over — prefer
     * [tusVideoUploader] for anything a user might background or lose signal during.
     * @see VideoUploader
     */
    val videoUploader: VideoUploader

    /**
     * Uploads a video over TUS, which can resume where an interrupted transfer stopped.
     * @see VideoUploader
     */
    val tusVideoUploader: VideoUploader

    /**
     * Live streams: creating, scheduling, starting and stopping, thumbnails, play data.
     * @see LiveStreamRepository
     */
    val liveStreamRepository: LiveStreamRepository

    /**
     * The library's player configuration — colours, controls, captions, playback speeds.
     *
     * Most callers want [fetchPlayerSettings] instead, which is the same call with the arguments
     * already in the right shape. Reach for the repository when you want to hold onto it, for
     * instance to fake it in a test.
     * @see SettingsRepository
     */
    val settingsRepository: SettingsRepository

    /**
     * The library's player configuration for one video, including any per-video overrides.
     *
     * @param token signed playback token, required when the library has token authentication on.
     * @param expires expiry that the token was signed for.
     */
    suspend fun fetchPlayerSettings(
        libraryId: Long,
        videoId: String,
        token: String? = null,
        expires: Long? = null,
    ): BunnyResult<PlayerSettings>
}