package net.bunny.api.video.domain.model

/**
 * Request shapes for the video management calls.
 *
 * These exist for the same reason the response models do: without them the generated DTOs would
 * leak back in through the *input* side of every repository method, and a spec change would break
 * integrator code that only ever wanted to rename a video.
 */

/**
 * Creates an empty video record that bytes are then uploaded into.
 *
 * Most callers do not need this — [net.bunny.api.upload.VideoUploader] creates the record itself.
 * Use it when you drive the upload yourself.
 *
 * @property thumbnailTime offset in milliseconds to grab the thumbnail from.
 */
public data class CreateVideoRequest(
    val title: String,
    val collectionId: String? = null,
    val thumbnailTime: Int? = null,
)

/**
 * Changes to apply to an existing video. `null` leaves a field as it is; an empty list clears it.
 */
public data class UpdateVideoRequest(
    val title: String? = null,
    val collectionId: String? = null,
    val chapters: List<Chapter>? = null,
    val moments: List<Moment>? = null,
    val metaTags: List<MetaTag>? = null,
)

/**
 * Adds a subtitle track.
 *
 * @property languageCode source language, e.g. `"en"` — the API calls this `srclang`.
 * @property captionsFileBase64 the caption file (SRT/VTT) encoded as base64.
 */
public data class AddCaptionRequest(
    val languageCode: String,
    val label: String,
    val captionsFileBase64: String,
)

/**
 * Tells Bunny to fetch a video from a URL instead of uploading bytes from the device.
 *
 * @property headers extra request headers Bunny should send when fetching, for sources behind
 *   authentication.
 */
public data class FetchVideoRequest(
    val url: String,
    val headers: Map<String, String>? = null,
    val title: String? = null,
)

/**
 * Asks Bunny's AI pass to fill in metadata from the video's content.
 *
 * @property sourceLanguage language spoken in the video; improves the result when set.
 */
public data class SmartGenerateRequest(
    val generateTitle: Boolean? = null,
    val generateDescription: Boolean? = null,
    val generateChapters: Boolean? = null,
    val generateMoments: Boolean? = null,
    val sourceLanguage: String? = null,
)

/**
 * Codec a video can be re-encoded into.
 *
 * Named, unlike the generated `EncoderOutputCodec`, whose entries are `_0`.. `_3` with the meaning
 * only in a doc comment.
 */
@Suppress("MagicNumber") // the numbers are the API's codec ids
public enum class VideoCodec(public val value: Int) {
    H264(0),
    VP9(1),
    HEVC(2),
    AV1(3),
}

/**
 * Requests transcription, and optionally the same AI-generated metadata as
 * [SmartGenerateRequest].
 *
 * @property targetLanguages languages to translate the transcription into.
 */
public data class TranscribeVideoRequest(
    val targetLanguages: List<String>? = null,
    val generateTitle: Boolean? = null,
    val generateDescription: Boolean? = null,
    val generateChapters: Boolean? = null,
    val generateMoments: Boolean? = null,
    val sourceLanguage: String? = null,
)
