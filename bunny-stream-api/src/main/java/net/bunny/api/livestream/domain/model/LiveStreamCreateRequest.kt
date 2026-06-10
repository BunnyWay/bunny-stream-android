package net.bunny.api.livestream.domain.model

/**
 * Writable subset of [LiveStream] used to create new live streams or update existing ones.
 *
 * Mirrors the `CreateLiveStreamModel`/`UpdateLiveStreamModel` schemas in `StreamApi.yml`
 * (official preview API v1.4.78 — note `category` is response-only and not writable).
 * All fields are nullable so callers can update only the fields they want — null values
 * are not sent to the server.
 */
data class LiveStreamCreateRequest(
    val title: String? = null,
    val description: String? = null,
    val collectionId: String? = null,
    val isPublic: Boolean? = null,
    val scheduledStartTime: String? = null,
    val scheduledEndTime: String? = null,
    val dvrEnabled: Boolean? = null,
    val dvrWindowSeconds: Int? = null,
    val recordVod: Boolean? = null,
    val enableCountdown: Boolean? = null,
    val preStreamTrailerVideoId: String? = null,
)
