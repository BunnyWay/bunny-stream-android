package net.bunny.api.collection.domain

import net.bunny.api.collection.domain.model.VideoCollection
import net.bunny.api.collection.domain.model.VideoCollectionList
import net.bunny.api.error.BunnyResult

/**
 * Managing collections — the named groupings videos can belong to.
 *
 * Replaces reaching into the generated `collectionsApi`: `suspend`, [BunnyResult], domain models.
 * Reach it through `BunnyStreamApi.getInstance().collectionRepository`.
 */
public interface CollectionRepository {

    /**
     * Lists collections in a library.
     *
     * @param includeThumbnails also fills [VideoCollection.previewImageUrls]. Off by default
     *   because it costs an extra lookup per collection; leave it off when you only need names
     *   and counts.
     */
    public suspend fun listCollections(
        libraryId: Long,
        page: Int = 1,
        itemsPerPage: Int = 100,
        search: String? = null,
        orderBy: String = "date",
        includeThumbnails: Boolean = false,
    ): BunnyResult<VideoCollectionList>

    /**
     * Fetches one collection.
     *
     * @param includeThumbnails see [listCollections]; without it,
     *   [VideoCollection.previewImageUrls] comes back empty whether or not previews exist.
     */
    public suspend fun getCollection(
        libraryId: Long,
        collectionId: String,
        includeThumbnails: Boolean = false,
    ): BunnyResult<VideoCollection>

    /** Creates an empty collection. Videos are moved into it by updating their `collectionId`. */
    public suspend fun createCollection(
        libraryId: Long,
        name: String,
    ): BunnyResult<VideoCollection>

    /** Renames a collection. */
    public suspend fun updateCollection(
        libraryId: Long,
        collectionId: String,
        name: String,
    ): BunnyResult<Unit>

    /**
     * Deletes the collection.
     *
     * The videos in it are **not** deleted — they are left without a collection.
     */
    public suspend fun deleteCollection(
        libraryId: Long,
        collectionId: String,
    ): BunnyResult<Unit>
}
