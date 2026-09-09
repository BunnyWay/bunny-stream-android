package net.bunny.api.video.domain.model

/**
 * Paginated result of listing videos in a library.
 *
 * Shaped like [net.bunny.api.livestream.domain.model.LiveStreamList] on purpose — the two listing
 * calls should read the same way.
 *
 * @property totalItems videos matching the query across all pages, for rendering "x of y".
 * @property currentPage 1-based index of the page in [items].
 */
public data class VideoList(
    val totalItems: Long,
    val currentPage: Long,
    val itemsPerPage: Int,
    val items: List<Video>,
)
