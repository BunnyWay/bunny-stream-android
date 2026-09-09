package net.bunny.api.collection.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.models.CollectionModel
import org.openapitools.client.models.PaginationListOfCollectionModel

/**
 * Collections follow the same mapping rules as videos: fields the API guarantees become non-null,
 * and its comma-separated id list becomes a real list.
 */
class CollectionMappersTest {

    @Test
    fun `a populated collection keeps every value`() {
        val domain = CollectionModel(
            videoLibraryId = 42L,
            guid = "collection-guid",
            name = "Summer campaign",
            videoCount = 12L,
            totalSize = 987_654_321L,
            previewVideoIds = "v1,v2,v3",
            previewImageUrls = listOf("https://cdn/1.jpg", "https://cdn/2.jpg"),
        ).toDomain()

        assertEquals("collection-guid", domain.id)
        assertEquals(42L, domain.videoLibraryId)
        assertEquals("Summer campaign", domain.name)
        assertEquals(12L, domain.videoCount)
        assertEquals(987_654_321L, domain.totalSizeBytes)
        assertEquals(listOf("v1", "v2", "v3"), domain.previewVideoIds)
        assertEquals(listOf("https://cdn/1.jpg", "https://cdn/2.jpg"), domain.previewImageUrls)
    }

    @Test
    fun `an empty DTO produces a usable collection rather than nulls`() {
        val domain = CollectionModel().toDomain()

        assertEquals("", domain.id)
        assertEquals(0L, domain.videoLibraryId)
        assertEquals("", domain.name)
        assertEquals(0L, domain.videoCount)
        assertEquals(0L, domain.totalSizeBytes)
        assertTrue(domain.previewVideoIds.isEmpty())
        assertTrue(domain.previewImageUrls.isEmpty())
    }

    @Test
    fun `preview ids are split the same way resolutions are`() {
        assertEquals(
            listOf("v1", "v2"),
            CollectionModel(previewVideoIds = " v1 , ,v2, ").toDomain().previewVideoIds,
        )
        assertTrue(CollectionModel(previewVideoIds = "").toDomain().previewVideoIds.isEmpty())
    }

    @Test
    fun `a page maps its items and counters`() {
        val page = PaginationListOfCollectionModel(
            totalItems = 3L,
            currentPage = 1L,
            itemsPerPage = 100,
            items = listOf(CollectionModel(guid = "a"), CollectionModel(guid = "b")),
        ).toDomain()

        assertEquals(3L, page.totalItems)
        assertEquals(1L, page.currentPage)
        assertEquals(100, page.itemsPerPage)
        assertEquals(listOf("a", "b"), page.items.map { it.id })
    }

    @Test
    fun `an empty page reads as page one with no items`() {
        val page = PaginationListOfCollectionModel().toDomain()

        assertEquals(0L, page.totalItems)
        assertEquals(1L, page.currentPage)
        assertTrue(page.items.isEmpty())
    }
}
