package net.bunny.api.collection.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.api.ManageCollectionsApi
import net.bunny.api.collection.domain.CollectionRepository
import net.bunny.api.collection.domain.model.VideoCollection
import net.bunny.api.collection.domain.model.VideoCollectionList
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.bunnyCatching
import net.bunny.api.error.requireSuccess
import org.openapitools.client.models.CollectionUpdateCollectionRequest

/**
 * Adapts the generated [ManageCollectionsApi] into the domain surface, on the same pattern as
 * [net.bunny.api.video.data.DefaultVideoRepository].
 */
internal class DefaultCollectionRepository(
    private val collectionsApi: ManageCollectionsApi,
    private val coroutineDispatcher: CoroutineDispatcher,
) : CollectionRepository {

    override suspend fun listCollections(
        libraryId: Long,
        page: Int,
        itemsPerPage: Int,
        search: String?,
        orderBy: String,
        includeThumbnails: Boolean,
    ): BunnyResult<VideoCollectionList> = withContext(coroutineDispatcher) {
        bunnyCatching {
            collectionsApi.collectionList(
                libraryId = libraryId,
                page = page,
                itemsPerPage = itemsPerPage,
                search = search.orEmpty(),
                orderBy = orderBy,
                includeThumbnails = includeThumbnails,
            ).toDomain()
        }
    }

    override suspend fun getCollection(
        libraryId: Long,
        collectionId: String,
        includeThumbnails: Boolean,
    ): BunnyResult<VideoCollection> = withContext(coroutineDispatcher) {
        bunnyCatching {
            collectionsApi.collectionGetCollection(libraryId, collectionId, includeThumbnails)
                .toDomain()
        }
    }

    override suspend fun createCollection(
        libraryId: Long,
        name: String,
    ): BunnyResult<VideoCollection> = withContext(coroutineDispatcher) {
        bunnyCatching {
            collectionsApi.collectionCreateCollection(
                libraryId = libraryId,
                collectionUpdateCollectionRequest = CollectionUpdateCollectionRequest(name = name),
            ).toDomain()
        }
    }

    override suspend fun updateCollection(
        libraryId: Long,
        collectionId: String,
        name: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            collectionsApi.collectionUpdateCollection(
                libraryId = libraryId,
                collectionId = collectionId,
                collectionUpdateCollectionRequest = CollectionUpdateCollectionRequest(name = name),
            ).requireSuccess()
        }
    }

    override suspend fun deleteCollection(
        libraryId: Long,
        collectionId: String,
    ): BunnyResult<Unit> = withContext(coroutineDispatcher) {
        bunnyCatching {
            collectionsApi.collectionDeleteCollection(libraryId, collectionId).requireSuccess()
        }
    }
}
