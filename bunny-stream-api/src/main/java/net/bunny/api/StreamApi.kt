package net.bunny.api

import net.bunny.api.api.ManageCollectionsApi
import net.bunny.api.api.ManageLiveStreamsApi
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.settings.domain.SettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.upload.VideoUploader

interface StreamApi {
    /**
     * API endpoints for managing video collections
     * @see ManageCollectionsApi
     */
    val collectionsApi: ManageCollectionsApi

    /**
     * API endpoints for managing videos
     * @see ManageVideosApi
     */
    val videosApi: ManageVideosApi

    /**
     * API endpoints for managing live streams
     * @see ManageLiveStreamsApi
     */
    val liveStreamsApi: ManageLiveStreamsApi

    /**
     * Component for managing video uploads
     * @see VideoUploader
     */
    val videoUploader: VideoUploader

    /**
     * Component for managing TUS video uploads
     * @see VideoUploader
     */
    val tusVideoUploader: VideoUploader

    val settingsRepository: SettingsRepository

    /**
     * Repository wrapping [ManageLiveStreamsApi] with domain models and the [BunnyResult]
     * envelope for errors. Prefer this over the raw [liveStreamsApi] for SDK consumers.
     * @see LiveStreamRepository
     */
    val liveStreamRepository: LiveStreamRepository

    suspend fun fetchPlayerSettings(
        libraryId: Long,
        videoId: String,
        token: String? = null,
        expires: Long? = null,
    ): BunnyResult<PlayerSettings>
}