package net.bunny.api.collection.domain.model

/**
 * Paginated result of listing collections in a library.
 *
 * Shaped like [net.bunny.api.video.domain.model.VideoList] and
 * [net.bunny.api.livestream.domain.model.LiveStreamList] — every listing call in the SDK reads the
 * same way.
 *
 * @property totalItems collections matching the query across all pages.
 * @property currentPage 1-based index of the page in [items].
 */
public data class VideoCollectionList(
    val totalItems: Long,
    val currentPage: Long,
    val itemsPerPage: Int,
    val items: List<VideoCollection>,
)
