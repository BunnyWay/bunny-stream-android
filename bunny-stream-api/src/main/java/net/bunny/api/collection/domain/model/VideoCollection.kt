package net.bunny.api.collection.domain.model

/**
 * Domain representation of a collection — a named grouping of videos inside a library.
 *
 * Named `VideoCollection` rather than `Collection` on purpose: the latter would clash with
 * `kotlin.collections.Collection` in every file that touches both, and an integrator should never
 * have to write an import alias to use an SDK type.
 *
 * @property id the collection's GUID, used as `collectionId` elsewhere in the SDK.
 * @property videoCount how many videos the collection holds.
 * @property totalSizeBytes combined storage of those videos.
 * @property previewVideoIds ids of the videos the dashboard uses as preview tiles. Empty when the
 *   collection has none yet.
 * @property previewImageUrls thumbnails for those preview videos. Populated only when the call
 *   asked for thumbnails (`includeThumbnails`), so an empty list means "not requested" as often as
 *   it means "none".
 */
public data class VideoCollection(
    val id: String,
    val videoLibraryId: Long,
    val name: String,
    val videoCount: Long,
    val totalSizeBytes: Long,
    val previewVideoIds: List<String>,
    val previewImageUrls: List<String>,
)
