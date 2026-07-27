package net.bunny.api.upload

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.error.BunnyError
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.PauseState
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.upload.service.UploadControl
import net.bunny.api.upload.service.UploadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Covers the part of the upload model that only exists because an upload outlives the call that
 * started it: addressing one by id, attaching to it after the fact, and continuing one that failed
 * rather than starting a second upload of the same file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVideoUploaderTest {

    private val libraryId = 7L
    private val createdVideoId = "created-video"
    private val payload = ByteArray(128)

    private val videosApi = mockk<ManageVideosApi>()
    private val videoUri = mockk<Uri>(relaxed = true)

    /** Records what it was asked to transfer, then replays a scripted event sequence. */
    private class FakeUploadService(
        override val supportsResuming: Boolean,
        private val emitDelayMillis: Long = 0L,
        private val script: (videoId: String) -> List<UploadEvent>,
    ) : UploadService {

        val transferred = mutableListOf<String>()

        override fun upload(
            libraryId: Long,
            videoId: String,
            fileInfo: FileInfo,
            control: UploadControl,
        ): Flow<UploadEvent> = flow {
            transferred += videoId
            // A real transfer suspends on IO before it reports anything. Without that the whole
            // upload would finish inside one scheduler tick, which no network does and which would
            // make these tests assert against a situation that cannot occur.
            delay(TRANSFER_TICK_MILLIS)
            script(videoId).forEachIndexed { index, event ->
                if (index > 0) delay(emitDelayMillis)
                emit(event)
            }
        }
    }

    private fun completingService(supportsResuming: Boolean = true) = FakeUploadService(
        supportsResuming = supportsResuming,
        script = { videoId ->
            listOf(
                UploadEvent.Progress(50, videoId, PauseState.Uploading),
                UploadEvent.Completed(videoId),
            )
        },
    )

    /** Emits its Progress, then stalls before completing, leaving a window in which to attach. */
    private fun pausingService() = FakeUploadService(
        supportsResuming = true,
        emitDelayMillis = STALL_MILLIS,
        script = { videoId ->
            listOf(
                UploadEvent.Progress(50, videoId, PauseState.Uploading),
                UploadEvent.Completed(videoId),
            )
        },
    )

    /**
     * Transfers, reports progress, and then stalls — the shape of an upload still in flight when
     * something tears it down from outside.
     */
    private fun stallingService() = FakeUploadService(
        supportsResuming = true,
        emitDelayMillis = STALL_MILLIS,
        script = { videoId ->
            listOf(
                UploadEvent.Progress(50, videoId, PauseState.Uploading),
                UploadEvent.Completed(videoId),
            )
        },
    )

    private fun context(): Context {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "clip.mp4"
        every { cursor.getLong(1) } returns payload.size.toLong()

        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { resolver.openInputStream(any()) } returns ByteArrayInputStream(payload)

        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        return context
    }

    private fun stubVideoCreation(guid: String? = createdVideoId) {
        every { videosApi.videoCreateVideo(any(), any()) } returns mockk(relaxed = true) {
            every { this@mockk.guid } returns guid
        }
    }

    /**
     * StandardTestDispatcher keeps the uploader's launched coroutine queued until the test advances
     * it, so a collector can attach before any event is produced and observe the whole sequence.
     */
    private fun TestScope.uploader(service: UploadService) = DefaultVideoUploader(
        context = context(),
        videoUploadService = service,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        videosApi = videosApi,
    )

    // region — starting and observing

    @Test
    fun `startUpload returns an id before any work has happened`() = runTest {
        stubVideoCreation()
        val uploadId = uploader(completingService()).startUpload(libraryId, videoUri)

        assertTrue(uploadId.isNotEmpty())
        verify(exactly = 0) { videosApi.videoCreateVideo(any(), any()) }

        advanceUntilIdle()
    }

    @Test
    fun `an observer attached before any event runs sees the whole sequence`() = runTest {
        stubVideoCreation()
        val uploader = uploader(completingService())
        val uploadId = uploader.startUpload(libraryId, videoUri)

        val events = mutableListOf<UploadEvent>()
        val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
        advanceUntilIdle()
        collector.join()

        assertEquals(
            listOf(
                UploadEvent.Started(uploadId, createdVideoId),
                UploadEvent.Progress(50, createdVideoId, PauseState.Uploading),
                UploadEvent.Completed(createdVideoId),
            ),
            events,
        )
    }

    @Test
    fun `an observer attaching mid-transfer starts at the latest event, not at Started`() = runTest {
        stubVideoCreation()
        val uploader = uploader(pausingService())
        val uploadId = uploader.startUpload(libraryId, videoUri)

        // Let the upload get past Started and emit its first Progress before anyone attaches —
        // this is the returning-screen case, and the one LibraryViewModel relies on when it reads
        // the video id off Progress rather than only off Started.
        advanceTimeBy(TRANSFER_TICK_MILLIS + 1)

        val events = mutableListOf<UploadEvent>()
        val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
        advanceUntilIdle()
        collector.join()

        assertTrue("expected at least one event", events.isNotEmpty())
        assertTrue(
            "a late observer must not be replayed Started, got ${events.first()}",
            events.first() !is UploadEvent.Started,
        )
        assertEquals(
            UploadEvent.Progress(50, createdVideoId, PauseState.Uploading),
            events.first(),
        )
        assertTrue(events.last() is UploadEvent.Completed)
    }

    @Test
    fun `the flow completes on the terminal event instead of hanging`() = runTest {
        stubVideoCreation()
        val uploader = uploader(completingService())
        val uploadId = uploader.startUpload(libraryId, videoUri)

        // toList() only returns if the flow completes; a plain SharedFlow never would.
        val events = mutableListOf<UploadEvent>()
        val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
        advanceUntilIdle()
        collector.join()

        assertTrue(events.last() is UploadEvent.Completed)
    }

    @Test
    fun `two observers can watch the same upload`() = runTest {
        stubVideoCreation()
        val uploader = uploader(completingService())
        val uploadId = uploader.startUpload(libraryId, videoUri)

        val first = mutableListOf<UploadEvent>()
        val second = mutableListOf<UploadEvent>()
        val a = launch { uploader.observeUpload(uploadId)!!.toList(first) }
        val b = launch { uploader.observeUpload(uploadId)!!.toList(second) }
        advanceUntilIdle()
        a.join()
        b.join()

        assertEquals(first, second)
        assertTrue(first.last() is UploadEvent.Completed)
    }

    @Test
    fun `an unknown upload id has nothing to observe`() = runTest {
        assertNull(uploader(completingService()).observeUpload("never-existed"))
    }

    @Test
    fun `abandoning an observer does not stop the transfer`() = runTest {
        stubVideoCreation()
        val service = completingService()
        val uploader = uploader(service)
        val uploadId = uploader.startUpload(libraryId, videoUri)

        // Attach and immediately walk away, the way a screen being destroyed would.
        val collector = launch { uploader.observeUpload(uploadId)!!.toList(mutableListOf()) }
        collector.cancel()
        advanceUntilIdle()

        assertEquals(listOf(createdVideoId), service.transferred)
    }

    // endregion

    // region — continuing an interrupted upload

    @Test
    fun `continueUpload transfers the given video without creating a new one`() = runTest {
        val service = completingService(supportsResuming = true)
        val uploader = uploader(service)

        val uploadId = uploader.continueUpload(libraryId, "interrupted-video", videoUri)
        val events = mutableListOf<UploadEvent>()
        val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
        advanceUntilIdle()
        collector.join()

        // The whole point: no second video record for the same file.
        verify(exactly = 0) { videosApi.videoCreateVideo(any(), any()) }
        assertEquals(listOf("interrupted-video"), service.transferred)
        assertEquals(UploadEvent.Started(uploadId, "interrupted-video"), events.first())
    }

    @Test
    fun `continueUpload refuses on the non-resumable path instead of re-sending everything`() =
        runTest {
            val service = completingService(supportsResuming = false)
            val uploader = uploader(service)

            val uploadId = uploader.continueUpload(libraryId, "interrupted-video", videoUri)
            val events = mutableListOf<UploadEvent>()
            val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
            advanceUntilIdle()
            collector.join()

            val failed = events.single() as UploadEvent.Failed
            assertTrue(failed.error is BunnyError.InvalidState)
            assertTrue(failed.error.isTerminal)
            assertEquals("interrupted-video", failed.videoId)
            // Nothing was sent, and nothing was created.
            assertTrue(service.transferred.isEmpty())
            verify(exactly = 0) { videosApi.videoCreateVideo(any(), any()) }
        }

    // endregion

    // region — failures before the transfer

    @Test
    fun `a video created without a guid fails the upload rather than transferring nothing`() =
        runTest {
            stubVideoCreation(guid = null)
            val service = completingService()
            val uploader = uploader(service)

            val uploadId = uploader.startUpload(libraryId, videoUri)
            val events = mutableListOf<UploadEvent>()
            val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
            advanceUntilIdle()
            collector.join()

            val failed = events.single() as UploadEvent.Failed
            assertTrue(failed.error is BunnyError.Decode)
            assertNull(failed.videoId)
            assertTrue(service.transferred.isEmpty())
        }

    @Test
    fun `shutdown gives an in-flight upload a terminal event instead of stranding its observer`() =
        runTest {
            stubVideoCreation()
            val uploader = uploader(stallingService())
            val uploadId = uploader.startUpload(libraryId, videoUri)

            val events = mutableListOf<UploadEvent>()
            val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
            // Get the transfer genuinely under way — otherwise this asserts against queued work
            // that never ran, and would pass even if shutdown() did nothing at all.
            advanceTimeBy(TRANSFER_TICK_MILLIS + 1)
            assertTrue("the upload should be in flight by now", events.isNotEmpty())
            assertTrue(events.none { it.isTerminalEvent() })

            uploader.shutdown()
            advanceUntilIdle()

            // join() returns only if the flow completed — a stranded observer hangs here, which is
            // exactly the failure this guards against.
            collector.join()
            assertTrue("shutdown must end the stream", events.last().isTerminalEvent())
        }

    @Test
    fun `an upload started after shutdown fails instead of hanging`() = runTest {
        stubVideoCreation()
        val service = stallingService()
        val uploader = uploader(service)
        uploader.shutdown()

        // launch() on a cancelled scope is a silent no-op, so without a guard this upload would
        // never run and never speak — the caller would wait on it forever.
        val uploadId = uploader.startUpload(libraryId, videoUri)
        val events = mutableListOf<UploadEvent>()
        val collector = launch { uploader.observeUpload(uploadId)!!.toList(events) }
        advanceUntilIdle()
        collector.join()

        val failed = events.single() as UploadEvent.Failed
        assertTrue(failed.error is BunnyError.InvalidState)
        assertTrue(service.transferred.isEmpty())
    }

    // endregion

    private fun UploadEvent.isTerminalEvent(): Boolean =
        this is UploadEvent.Completed || this is UploadEvent.Cancelled || this is UploadEvent.Failed

    private companion object {
        private const val TRANSFER_TICK_MILLIS = 1L
        private const val STALL_MILLIS = 60_000L
    }
}
