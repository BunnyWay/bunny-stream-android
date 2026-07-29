package net.bunny.api.collection.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bunny.api.api.ManageCollectionsApi
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.CollectionModel
import org.openapitools.client.models.CollectionUpdateCollectionRequest
import org.openapitools.client.models.PaginationListOfCollectionModel
import org.openapitools.client.models.StatusModel

/**
 * Tests for [DefaultCollectionRepository].
 *
 * Collections are the surface nothing in this repo consumes but production integrators can — the
 * generated `collectionsApi` shipped in 3.x — so the point of these tests is that hiding it behind
 * a repository did not quietly change what it does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCollectionRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val api: ManageCollectionsApi = mockk()
    private val repo = DefaultCollectionRepository(api, dispatcher)

    @Test
    fun `listCollections maps the page and its items`() = runTest(dispatcher) {
        every { api.collectionList(LIBRARY_ID, 1, 100, "", "date", false) } returns
            PaginationListOfCollectionModel(
                totalItems = 2L,
                currentPage = 1L,
                itemsPerPage = 100,
                items = listOf(
                    CollectionModel(guid = "c-1", name = "Summer", videoCount = 4L),
                ),
            )

        val page = (repo.listCollections(LIBRARY_ID) as BunnyResult.Ok).value

        assertEquals(2L, page.totalItems)
        assertEquals("c-1", page.items.single().id)
        assertEquals("Summer", page.items.single().name)
        assertEquals(4L, page.items.single().videoCount)
    }

    @Test
    fun `thumbnails are opt-in and the flag reaches the API`() = runTest(dispatcher) {
        // Off by default because it costs an extra lookup per collection; a caller that wants
        // preview images has to ask, and must get what it asked for.
        every { api.collectionList(LIBRARY_ID, 1, 100, "", "date", true) } returns
            PaginationListOfCollectionModel()

        repo.listCollections(LIBRARY_ID, includeThumbnails = true)

        verify { api.collectionList(LIBRARY_ID, 1, 100, "", "date", true) }
    }

    @Test
    fun `getCollection maps a single collection`() = runTest(dispatcher) {
        every { api.collectionGetCollection(LIBRARY_ID, COLLECTION_ID, false) } returns
            CollectionModel(
                guid = COLLECTION_ID,
                name = "Tutorials",
                videoCount = 7L,
                totalSize = 1_234L,
                previewVideoIds = "v1,v2",
            )

        val collection = (
            repo.getCollection(LIBRARY_ID, COLLECTION_ID) as BunnyResult.Ok
            ).value

        assertEquals("Tutorials", collection.name)
        assertEquals(1_234L, collection.totalSizeBytes)
        assertEquals(listOf("v1", "v2"), collection.previewVideoIds)
    }

    @Test
    fun `createCollection sends the name and returns the created collection`() =
        runTest(dispatcher) {
            val sent = slot<CollectionUpdateCollectionRequest>()
            every { api.collectionCreateCollection(eq(LIBRARY_ID), capture(sent)) } returns
                CollectionModel(guid = "new-c", name = "Archive")

            val created = repo.createCollection(LIBRARY_ID, "Archive")

            assertEquals("Archive", sent.captured.name)
            assertEquals("new-c", (created as BunnyResult.Ok).value.id)
        }

    @Test
    fun `updateCollection sends the new name`() = runTest(dispatcher) {
        val sent = slot<CollectionUpdateCollectionRequest>()
        every {
            api.collectionUpdateCollection(eq(LIBRARY_ID), eq(COLLECTION_ID), capture(sent))
        } returns StatusModel(success = true)

        val result = repo.updateCollection(LIBRARY_ID, COLLECTION_ID, "Renamed")

        assertEquals("Renamed", sent.captured.name)
        assertTrue(result is BunnyResult.Ok)
    }

    @Test
    fun `deleteCollection answers with Unit on success`() = runTest(dispatcher) {
        every { api.collectionDeleteCollection(LIBRARY_ID, COLLECTION_ID) } returns
            StatusModel(success = true)

        assertTrue(repo.deleteCollection(LIBRARY_ID, COLLECTION_ID) is BunnyResult.Ok)
    }

    @Test
    fun `a rejection arrives as a typed error rather than a quiet success`() = runTest(dispatcher) {
        every { api.collectionDeleteCollection(LIBRARY_ID, COLLECTION_ID) } throws
            ClientException("Not Found", 404)

        val error = (repo.deleteCollection(LIBRARY_ID, COLLECTION_ID) as BunnyResult.Err).error
        assertTrue(error is BunnyError.NotFound)
        assertTrue(error.isTerminal)
    }

    private companion object {
        private const val LIBRARY_ID = 42L
        private const val COLLECTION_ID = "collection-guid"
    }
}
