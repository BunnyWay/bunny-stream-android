package net.bunny.api.livestream.domain.model

/**
 * Paginated result of listing live streams in a video library.
 */
data class LiveStreamList(
    val totalItems: Long,
    val currentPage: Long,
    val itemsPerPage: Int,
    val items: List<LiveStream>,
)
