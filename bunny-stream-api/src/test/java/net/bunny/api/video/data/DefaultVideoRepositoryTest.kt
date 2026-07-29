package net.bunny.api.video.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.video.domain.model.AddCaptionRequest
import net.bunny.api.video.domain.model.Chapter
import net.bunny.api.video.domain.model.CreateVideoRequest
import net.bunny.api.video.domain.model.UpdateVideoRequest
import net.bunny.api.video.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException
import org.openapitools.client.models.EncoderOutputCodec
import org.openapitools.client.models.PaginationListOfVideoModel
import org.openapitools.client.models.StatusModel
import org.openapitools.client.models.StatusModelOfVideoStorageSizeModel
import org.openapitools.client.models.StatusModelOfVideoStorageSizeModelAllOfData
import org.openapitools.client.models.VideoAddCaptionRequest
import org.openapitools.client.models.VideoCreateVideoRequest
import org.openapitools.client.models.VideoHeatmapModel
import org.openapitools.client.models.VideoModel
import org.openapitools.client.models.VideoUpdateVideoRequest

/**
 * Tests for [DefaultVideoRepository] — the layer that replaced reaching into the generated
 * `videosApi` directly.
 *
 * Focus, in priority order:
 *
 *  1. **Everything the HTTP stack throws becomes a typed [BunnyError].** This is the whole point of
 *     the repository: before it, callers wrapped each generated call in their own `try/catch`.
 *  2. **Domain requests reach the API intact.** A mapping that silently drops a field would look
 *     like a working call that quietly does less than asked.
 *  3. **Calls the API answers with a bare status become `BunnyResult<Unit>`** — a failure has to
 *     arrive as an error, never as a `false` the caller might ignore.
 *  4. **A `2xx` with no usable body is a failure, not empty data.**
 *
 * mockk stubs the generated final class; the repository's `withContext` is fed a
 * [StandardTestDispatcher] so suspend calls resolve synchronously under `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVideoRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val api: ManageVideosApi = mockk()
    private val repo = DefaultVideoRepository(api, dispatcher)

    // region — errors become typed results instead of exceptions

    @Test
    fun `a 401 from the generated client becomes a terminal Auth error`() = runTest(dispatcher) {
        every { api.videoGetVideo(LIBRARY_ID, VIDEO_ID) } throws
            ClientException("Unauthorized", 401)

        val result = repo.getVideo(LIBRARY_ID, VIDEO_ID)

        val error = (result as BunnyResult.Err).error
        assertTrue(error is BunnyError.Auth)
        assertEquals(401, error.httpStatus)
        assertTrue(error.isTerminal)
    }

    @Test
    fun `a 404 becomes NotFound so a caller can drop the video from a list`() =
        runTest(dispatcher) {
            every { api.videoGetVideo(LIBRARY_ID, VIDEO_ID) } throws ClientException("Gone", 404)

            val error = (repo.getVideo(LIBRARY_ID, VIDEO_ID) as BunnyResult.Err).error
            assertTrue(error is BunnyError.NotFound)
            assertTrue(error.isTerminal)
        }

    @Test
    fun `a 5xx stays transient so a retry is worth attempting`() = runTest(dispatcher) {
        every { api.videoList(any(), any(), any(), any(), any(), any()) } throws
            ServerException("boom", 503)

        val error = (repo.listVideos(LIBRARY_ID) as BunnyResult.Err).error
        assertEquals(503, error.httpStatus)
        assertTrue(!error.isTerminal)
    }

    @Test
    fun `a transport failure with no response is reported as Network`() = runTest(dispatcher) {
        every { api.videoGetVideo(LIBRARY_ID, VIDEO_ID) } throws java.io.IOException("reset")

        val error = (repo.getVideo(LIBRARY_ID, VIDEO_ID) as BunnyResult.Err).error
        assertTrue(error is BunnyError.Network)
        assertEquals(0, error.httpStatus)
    }

    // endregion

    // region — reading

    @Test
    fun `listVideos maps the page and its items to domain`() = runTest(dispatcher) {
        every { api.videoList(LIBRARY_ID, 2, 25, "", "", "title") } returns
            PaginationListOfVideoModel(
                totalItems = 40L,
                currentPage = 2L,
                itemsPerPage = 25,
                items = listOf(VideoModel(guid = "a", title = "First")),
            )

        val page = (
            repo.listVideos(
                libraryId = LIBRARY_ID,
                page = 2,
                itemsPerPage = 25,
                orderBy = "title",
            ) as BunnyResult.Ok
            ).value

        assertEquals(40L, page.totalItems)
        assertEquals(2L, page.currentPage)
        assertEquals("a", page.items.single().id)
        assertEquals("First", page.items.single().title)
    }

    @Test
    fun `a null search or collection reaches the API as the empty string it expects`() =
        runTest(dispatcher) {
            // The generated client defaults these to "" rather than null; passing null through
            // would send the literal string "null" as a query parameter.
            every { api.videoList(LIBRARY_ID, 1, 100, "", "", "date") } returns
                PaginationListOfVideoModel()

            repo.listVideos(LIBRARY_ID, search = null, collectionId = null)

            verify { api.videoList(LIBRARY_ID, 1, 100, "", "", "date") }
        }

    @Test
    fun `the heatmap wrapper is unwrapped to the map itself`() = runTest(dispatcher) {
        every { api.videoGetVideoHeatmap(LIBRARY_ID, VIDEO_ID) } returns
            VideoHeatmapModel(heatmap = mapOf("0" to 100, "30" to 60))

        val heatmap = (repo.fetchVideoHeatmap(LIBRARY_ID, VIDEO_ID) as BunnyResult.Ok).value
        assertEquals(mapOf("0" to 100, "30" to 60), heatmap)
    }

    @Test
    fun `an absent heatmap reads as empty rather than failing`() = runTest(dispatcher) {
        every { api.videoGetVideoHeatmap(LIBRARY_ID, VIDEO_ID) } returns VideoHeatmapModel()

        assertTrue((repo.fetchVideoHeatmap(LIBRARY_ID, VIDEO_ID) as BunnyResult.Ok).value.isEmpty())
    }

    // endregion

    // region — the status envelope

    @Test
    fun `a 2xx without data is a Decode failure, not empty data`() = runTest(dispatcher) {
        // The API wraps these responses in { success, message, statusCode, data }. A success with
        // no data means the call worked but returned nothing usable — handing back an empty
        // object would look like a real answer.
        every { api.videoGetVideoStorageSize(LIBRARY_ID, VIDEO_ID) } returns
            StatusModelOfVideoStorageSizeModel(success = true, statusCode = 200, data = null)

        val error = (repo.fetchVideoStorageSize(LIBRARY_ID, VIDEO_ID) as BunnyResult.Err).error
        assertTrue(error is BunnyError.Decode)
    }

    @Test
    fun `storage size is unwrapped from the envelope when present`() = runTest(dispatcher) {
        every { api.videoGetVideoStorageSize(LIBRARY_ID, VIDEO_ID) } returns
            StatusModelOfVideoStorageSizeModel(
                success = true,
                data = StatusModelOfVideoStorageSizeModelAllOfData(
                    thumbnails = 1_024L,
                    originals = 8_192L,
                    calculatedAt = "2026-07-01T00:00:00Z",
                ),
            )

        val size = (repo.fetchVideoStorageSize(LIBRARY_ID, VIDEO_ID) as BunnyResult.Ok).value
        assertEquals(1_024L, size.thumbnailsBytes)
        assertEquals(8_192L, size.originalsBytes)
        assertEquals("2026-07-01T00:00:00Z", size.calculatedAt)
    }

    // endregion

    // region — domain requests reach the API intact

    @Test
    fun `createVideo forwards every field of the domain request`() = runTest(dispatcher) {
        val sent = slot<VideoCreateVideoRequest>()
        every { api.videoCreateVideo(eq(LIBRARY_ID), capture(sent)) } returns
            VideoModel(guid = "new-video", title = "Clip")

        val created = repo.createVideo(
            LIBRARY_ID,
            CreateVideoRequest(title = "Clip", collectionId = "c-1", thumbnailTime = 5_000),
        )

        assertEquals("Clip", sent.captured.title)
        assertEquals("c-1", sent.captured.collectionId)
        assertEquals(5_000, sent.captured.thumbnailTime)
        assertEquals("new-video", (created as BunnyResult.Ok).value.id)
    }

    @Test
    fun `a created video with no id is a failure, not a video with an empty id`() =
        runTest(dispatcher) {
            // guid is optional in the generated model, so a response without one would otherwise
            // map to Video(id = ""). The camera builds its RTMP ingest URL from that id and would
            // publish into nothing, losing the recording with no error anywhere.
            every { api.videoCreateVideo(eq(LIBRARY_ID), any()) } returns VideoModel(title = "Clip")

            val created = repo.createVideo(LIBRARY_ID, CreateVideoRequest(title = "Clip"))

            assertTrue((created as BunnyResult.Err).error is BunnyError.Decode)
        }

    @Test
    fun `updateVideo carries chapters across the domain boundary`() = runTest(dispatcher) {
        val sent = slot<VideoUpdateVideoRequest>()
        every { api.videoUpdateVideo(eq(LIBRARY_ID), eq(VIDEO_ID), capture(sent)) } returns
            StatusModel(success = true)

        repo.updateVideo(
            LIBRARY_ID,
            VIDEO_ID,
            UpdateVideoRequest(
                title = "Renamed",
                chapters = listOf(Chapter(title = "Intro", startSeconds = 0, endSeconds = 15)),
            ),
        )

        assertEquals("Renamed", sent.captured.title)
        val chapter = sent.captured.chapters!!.single()
        assertEquals("Intro", chapter.title)
        assertEquals(0, chapter.start)
        assertEquals(15, chapter.end)
    }

    @Test
    fun `addCaption sends the language both as a parameter and in the body`() =
        runTest(dispatcher) {
            // The endpoint takes srclang twice — as a path/query parameter and inside the body.
            // Sending them out of step would attach the track to the wrong language.
            val sent = slot<VideoAddCaptionRequest>()
            every {
                api.videoAddCaption(eq(LIBRARY_ID), eq(VIDEO_ID), eq("pl"), capture(sent))
            } returns StatusModel(success = true, statusCode = 200)

            repo.addCaption(
                LIBRARY_ID,
                VIDEO_ID,
                AddCaptionRequest(languageCode = "pl", label = "Polski", captionsFileBase64 = "AAA"),
            )

            assertEquals("pl", sent.captured.srclang)
            assertEquals("Polski", sent.captured.label)
            assertEquals("AAA", sent.captured.captionsFile)
        }

    @Test
    fun `the named codec maps onto the generated enum`() = runTest(dispatcher) {
        // The generated enum's entries are _0.._3; getting this mapping wrong would silently
        // re-encode into a different codec than the caller asked for.
        every { api.videoReencodeUsingCodec(LIBRARY_ID, VIDEO_ID, EncoderOutputCodec._2) } returns
            VideoModel(guid = VIDEO_ID)

        repo.reencodeUsingCodec(LIBRARY_ID, VIDEO_ID, VideoCodec.HEVC)

        verify { api.videoReencodeUsingCodec(LIBRARY_ID, VIDEO_ID, EncoderOutputCodec._2) }
    }

    @Test
    fun `deleteResolutions joins the renditions and passes each destructive flag to its own parameter`() =
        runTest(dispatcher) {
            // Every flag here deletes something irreversibly, so each one must land on the wire
            // parameter its name promises. An earlier version wired "delete orphaned" to
            // deleteMp4Files, which would have removed a caller's MP4 fallbacks instead.
            val sent = mutableMapOf<String, Any?>()
            every {
                api.videoDeleteResolutions(
                    libraryId = LIBRARY_ID,
                    videoId = VIDEO_ID,
                    resolutionsToDelete = any(),
                    deleteNonConfiguredResolutions = any(),
                    allResolutions = any(),
                    deleteOriginal = any(),
                    outputs = any(),
                    deleteMp4Files = any(),
                    dryRun = any(),
                )
            } answers {
                sent["resolutionsToDelete"] = arg<String?>(2)
                sent["deleteNonConfigured"] = arg<Boolean?>(3)
                sent["allResolutions"] = arg<Boolean?>(4)
                sent["deleteOriginal"] = arg<Boolean?>(5)
                sent["deleteMp4Files"] = arg<Boolean?>(7)
                sent["dryRun"] = arg<Boolean?>(8)
                StatusModel(success = true)
            }

            val result = repo.deleteResolutions(
                LIBRARY_ID,
                VIDEO_ID,
                resolutions = listOf("240p", "360p"),
                deleteNonConfiguredResolutions = true,
                deleteMp4Files = false,
                deleteOriginal = true,
                deleteAllResolutions = false,
                dryRun = true,
            )

            assertTrue(result is BunnyResult.Ok)
            assertEquals("240p,360p", sent["resolutionsToDelete"])
            assertEquals(true, sent["deleteNonConfigured"])
            assertEquals(false, sent["allResolutions"])
            assertEquals(true, sent["deleteOriginal"])
            assertEquals(false, sent["deleteMp4Files"])
            assertEquals(true, sent["dryRun"])
        }

    @Test
    fun `a 200 carrying success=false is an error, not a quiet success`() = runTest(dispatcher) {
        // Bunny answers 200 with success=false when a mutation is rejected for a non-HTTP reason.
        // Reporting that as Ok tells a caller their delete worked when it did not.
        every { api.videoDeleteVideo(LIBRARY_ID, VIDEO_ID) } returns
            StatusModel(success = false, message = "Video is being processed", statusCode = 400)

        val result = repo.deleteVideo(LIBRARY_ID, VIDEO_ID)

        val error = (result as BunnyResult.Err).error
        assertEquals("Video is being processed", error.message)
        assertEquals(400, error.httpStatus)
    }

    // endregion

    // region — status-only calls

    @Test
    fun `deleteVideo answers with Unit on success`() = runTest(dispatcher) {
        every { api.videoDeleteVideo(LIBRARY_ID, VIDEO_ID) } returns
            StatusModel(success = true, statusCode = 200)

        assertTrue(repo.deleteVideo(LIBRARY_ID, VIDEO_ID) is BunnyResult.Ok)
    }

    @Test
    fun `deleteVideo reports a rejection as an error rather than a quiet success`() =
        runTest(dispatcher) {
            every { api.videoDeleteVideo(LIBRARY_ID, VIDEO_ID) } throws
                ClientException("Forbidden", 403)

            val error = (repo.deleteVideo(LIBRARY_ID, VIDEO_ID) as BunnyResult.Err).error
            assertTrue(error is BunnyError.Auth)
            assertTrue(error.isTerminal)
        }

    // endregion

    private companion object {
        private const val LIBRARY_ID = 42L
        private const val VIDEO_ID = "video-guid"
    }
}
