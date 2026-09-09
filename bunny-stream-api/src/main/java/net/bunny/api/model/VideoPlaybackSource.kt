package net.bunny.api.model

import com.google.gson.annotations.SerializedName

/**
 * Preferred playback source for a video, as reported by the play-data endpoint.
 * Hand-written replacement for the generator's broken
 * `VideoPlayDataModelPreferredPlaybackSource` oneOf wrapper — wired in via
 * `typeMappings` in bunny-stream-api/build.gradle.kts.
 */
enum class VideoPlaybackSource {
    @SerializedName("None")
    NONE,

    @SerializedName("Playlist")
    PLAYLIST,

    @SerializedName("Original")
    ORIGINAL,
}
