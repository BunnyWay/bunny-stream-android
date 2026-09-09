package net.bunny.api.settings.domain

import net.bunny.api.error.BunnyResult
import net.bunny.api.settings.domain.model.PlayerSettings

interface SettingsRepository {
    suspend fun fetchSettings(
        libraryId: Long,
        videoId: String,
        token: String? = null,
        expires: Long? = null,
    ): BunnyResult<PlayerSettings>
}
