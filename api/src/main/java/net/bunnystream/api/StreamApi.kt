package net.bunnystream.api

import arrow.core.Either
import net.bunnystream.api.api.ManageCollectionsApi
import net.bunnystream.api.api.ManageVideosApi
import net.bunnystream.api.settings.domain.SettingsRepository
import net.bunnystream.api.settings.domain.model.PlayerSettings
import net.bunnystream.api.upload.VideoUploader

interface StreamApi {
    /**
     * API endpoints for managing video collections
     * @see ManageVideosApi
     */
    val collectionsApi: ManageCollectionsApi

    /**
     * API endpoints for managing videos
     * @see ManageVideosApi
     */
    val videosApi: ManageVideosApi

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

    suspend fun fetchPlayerSettings(libraryId: Long, videoId: String): Either<String, PlayerSettings>
}