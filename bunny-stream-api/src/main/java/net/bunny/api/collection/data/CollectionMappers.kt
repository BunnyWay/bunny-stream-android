package net.bunny.api.collection.data

import net.bunny.api.collection.domain.model.VideoCollection
import net.bunny.api.collection.domain.model.VideoCollectionList
import net.bunny.api.video.data.toCommaSeparatedList
import org.openapitools.client.models.CollectionModel
import org.openapitools.client.models.PaginationListOfCollectionModel

/**
 * Generated DTO → domain mapping for collections. Follows the same two rules as the video
 * mappers: fields the API guarantees become non-null, and comma-separated API strings become
 * lists.
 */
internal fun CollectionModel.toDomain(): VideoCollection = VideoCollection(
    id = guid.orEmpty(),
    videoLibraryId = videoLibraryId ?: 0L,
    name = name.orEmpty(),
    videoCount = videoCount ?: 0L,
    totalSizeBytes = totalSize ?: 0L,
    previewVideoIds = previewVideoIds.toCommaSeparatedList(),
    previewImageUrls = previewImageUrls.orEmpty(),
)

internal fun PaginationListOfCollectionModel.toDomain(): VideoCollectionList = VideoCollectionList(
    totalItems = totalItems ?: 0L,
    currentPage = currentPage ?: 1L,
    itemsPerPage = itemsPerPage ?: 0,
    items = items?.map { it.toDomain() }.orEmpty(),
)
