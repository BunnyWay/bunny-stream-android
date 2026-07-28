package net.bunny.api.upload.service.basic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bunny.api.error.BunnyError
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.PauseState
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.upload.service.UploadControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Drives [BasicUploaderService] through a real Ktor stack backed by [MockEngine].
 *
 * The contract under test is the one every caller relies on: the flow ends with exactly one
 * terminal event, failures arrive as [UploadEvent.Failed] rather than as thrown exceptions, and a
 * rejected upload reports something — before 4.0.0 the non-success branch built a value and
 * dropped it, so a 401 looked identical to an upload that simply never finished.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BasicUploaderServiceTest {

    private val libraryId = 42L
    private val videoId = "video-guid"
    private val payload = ByteArray(2048) { 1 }

    private fun fileInfo() = FileInfo(
        fileName = "clip.mp4",
        size = payload.size.toLong(),
        inputStream = ByteArrayInputStream(payload),
    )

    private fun clientReturning(handler: suspend MockRequestHandleScope.() -> HttpResponseData) =
        HttpClient(
            MockEngine { request ->
                // Drain the request body. Ktor's onUpload hook is driven by the body actually being
                // read, so an engine that never reads it reports no progress at all — and the
                // progress assertions below would hold vacuously.
                request.body.toByteArray()
                handler()
            },
        ) {
            install(HttpTimeout)
        }

    /**
     * The dispatcher must be built from the running test's scheduler. A bare
     * `StandardTestDispatcher()` carries a scheduler nothing ever advances, so the `flowOn` hop
     * inside the service queues its work and the test hangs instead of failing.
     */
    private fun TestScope.serviceFor(client: HttpClient) =
        BasicUploaderService(client, UnconfinedTestDispatcher(testScheduler))

    // region — terminal events

    @Test
    fun `a successful response ends the flow with Completed`() = runTest {
        val service = serviceFor(
            clientReturning { respond("", HttpStatusCode.OK, headersOf()) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        assertEquals(UploadEvent.Completed(videoId), events.last())
        assertEquals(1, events.count { it is UploadEvent.Completed })
    }

    @Test
    fun `an unauthorized response ends the flow with a typed Auth failure`() = runTest {
        val service = serviceFor(
            clientReturning { respondError(HttpStatusCode.Unauthorized) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        val failed = events.last() as UploadEvent.Failed
        assertEquals(videoId, failed.videoId)
        assertTrue(failed.error is BunnyError.Auth)
        assertEquals(401, failed.error.httpStatus)
        assertTrue(failed.error.isTerminal)
    }

    @Test
    fun `a server error is reported as transient so callers can retry`() = runTest {
        val service = serviceFor(
            clientReturning { respondError(HttpStatusCode.InternalServerError) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        val failed = events.last() as UploadEvent.Failed
        assertEquals(500, failed.error.httpStatus)
        assertTrue(failed.error is BunnyError.Http)
        assertTrue(!failed.error.isTerminal)
    }

    @Test
    fun `a transport failure is mapped to Network instead of escaping the flow`() = runTest {
        val service = serviceFor(
            HttpClient(MockEngine { throw IOException("connection reset") }) {
                install(HttpTimeout)
            },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        val failed = events.last() as UploadEvent.Failed
        assertTrue(failed.error is BunnyError.Network)
        assertEquals(0, failed.error.httpStatus)
    }

    // endregion

    // region — progress

    @Test
    fun `progress is reported against the declared file size and never exceeds 100`() = runTest {
        val service = serviceFor(
            clientReturning { respond("", HttpStatusCode.OK, headersOf()) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()
        val progress = events.filterIsInstance<UploadEvent.Progress>()

        // Without this the two assertions below hold vacuously if progress stops being reported.
        assertTrue("expected at least one progress event", progress.isNotEmpty())
        assertTrue(progress.all { it.percentage in 0..100 })
        assertTrue(progress.all { it.videoId == videoId })
    }

    @Test
    fun `the plain path always reports pausing as unsupported`() = runTest {
        val service = serviceFor(
            clientReturning { respond("", HttpStatusCode.OK, headersOf()) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        val progress = events.filterIsInstance<UploadEvent.Progress>()
        assertTrue("expected at least one progress event", progress.isNotEmpty())
        assertTrue(progress.all { it.pauseState == PauseState.Unsupported })
    }

    // endregion

    // region — event ordering

    @Test
    fun `exactly one terminal event closes the flow`() = runTest {
        val service = serviceFor(
            clientReturning { respond("", HttpStatusCode.OK, headersOf()) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        val terminals = events.count {
            it is UploadEvent.Completed || it is UploadEvent.Cancelled || it is UploadEvent.Failed
        }
        assertEquals(1, terminals)
        assertTrue(events.dropLast(1).all { it is UploadEvent.Progress })
    }

    @Test
    fun `the service never emits Started - the uploader owns upload ids`() = runTest {
        val service = serviceFor(
            clientReturning { respond("", HttpStatusCode.OK, headersOf()) },
        )

        val events = service.upload(libraryId, videoId, fileInfo(), UploadControl()).toList()

        assertTrue(events.none { it is UploadEvent.Started })
    }

    // endregion
}
