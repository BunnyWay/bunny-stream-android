package net.bunny.api.upload.service.tus

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import io.tus.java.client.TusClient
import io.tus.java.client.TusUploader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bunny.api.error.BunnyError
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.PauseState
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.upload.service.UploadControl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Covers the chunk loop, which is where the resumable path earns its keep and where the ways to
 * strand an upload live: a pause that never releases, a cancel a paused upload sleeps through, and
 * progress that stops reporting because only the percentage is compared.
 *
 * [TusClient] is constructed inside the service, so its constructor is mocked rather than injected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TusUploaderServiceTest {

    private val libraryId = 7L
    private val videoId = "video-guid"
    private val fileSize = 2048L
    private val chunkBytes = 512L

    private lateinit var uploader: TusUploader
    private var offset = 0L

    @Before
    fun setUp() {
        offset = 0L
        uploader = mockk(relaxed = true)
        every { uploader.offset } answers { offset }

        mockkConstructor(TusClient::class)
        every { anyConstructed<TusClient>().resumeOrCreateUpload(any()) } returns uploader
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Advances [offset] by one chunk per call and reports "no more chunks" at the end. */
    private fun uploadsCleanly() {
        every { uploader.uploadChunk() } answers {
            offset += chunkBytes
            if (offset >= fileSize) NO_MORE_CHUNKS else 1
        }
    }

    private fun fileInfo() = FileInfo(
        fileName = "clip.mp4",
        size = fileSize,
        inputStream = ByteArrayInputStream(ByteArray(fileSize.toInt())),
    )

    private fun TestScope.service(): TusUploaderService = TusUploaderService(
        preferences = mockk<SharedPreferences>(relaxed = true),
        chunkSize = chunkBytes.toInt(),
        accessKey = "access-key",
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    // region — happy path

    @Test
    fun `reports progress for each chunk and ends with Completed`() = runTest {
        uploadsCleanly()

        val events = service()
            .upload(libraryId, videoId, fileInfo(), UploadControl())
            .toList()

        assertEquals(UploadEvent.Completed(videoId), events.last())
        assertEquals(
            listOf(0, 25, 50, 75),
            events.filterIsInstance<UploadEvent.Progress>().map { it.percentage },
        )
    }

    @Test
    fun `progress reports the transfer as running while it is not held`() = runTest {
        uploadsCleanly()

        val events = service()
            .upload(libraryId, videoId, fileInfo(), UploadControl())
            .toList()

        assertTrue(
            events.filterIsInstance<UploadEvent.Progress>()
                .all { it.pauseState == PauseState.Uploading },
        )
    }

    // endregion

    // region — cancellation

    @Test
    fun `a cancel requested before the first chunk stops without uploading anything`() = runTest {
        uploadsCleanly()
        val control = UploadControl().apply { cancel() }

        val events = service().upload(libraryId, videoId, fileInfo(), control).toList()

        assertEquals(listOf(UploadEvent.Cancelled(videoId)), events)
        verify(exactly = 0) { uploader.uploadChunk() }
    }

    @Test
    fun `a cancel mid-transfer ends with Cancelled rather than Completed`() = runTest {
        val control = UploadControl()
        every { uploader.uploadChunk() } answers {
            offset += chunkBytes
            if (offset >= chunkBytes * 2) control.cancel()
            if (offset >= fileSize) NO_MORE_CHUNKS else 1
        }

        val events = service().upload(libraryId, videoId, fileInfo(), control).toList()

        assertEquals(UploadEvent.Cancelled(videoId), events.last())
        assertTrue(events.none { it is UploadEvent.Completed })
    }

    // endregion

    // region — pause

    @Test
    fun `a paused upload sends no chunks until it is resumed`() = runTest {
        uploadsCleanly()
        val control = UploadControl().apply { pause() }
        val events = mutableListOf<UploadEvent>()

        val collector = launch {
            service().upload(libraryId, videoId, fileInfo(), control).toList(events)
        }

        advanceTimeBy(HELD_MILLIS)
        verify(exactly = 0) { uploader.uploadChunk() }
        assertTrue(events.none { it is UploadEvent.Completed })
        assertTrue(
            events.filterIsInstance<UploadEvent.Progress>()
                .all { it.pauseState == PauseState.Paused },
        )

        control.resume()
        advanceUntilIdle()
        collector.join()

        assertEquals(UploadEvent.Completed(videoId), events.last())
    }

    @Test
    fun `a held upload does not repeat the same progress event while it waits`() = runTest {
        uploadsCleanly()
        val control = UploadControl().apply { pause() }
        val events = mutableListOf<UploadEvent>()

        val collector = launch {
            service().upload(libraryId, videoId, fileInfo(), control).toList(events)
        }
        advanceTimeBy(HELD_MILLIS)

        // Many poll iterations elapse while held; the state never changes, so exactly one event
        // describes it.
        assertEquals(1, events.filterIsInstance<UploadEvent.Progress>().size)

        control.cancel()
        advanceUntilIdle()
        collector.join()
    }

    // endregion

    // region — failures

    @Test
    fun `a transport failure mid-transfer is reported as a typed failure`() = runTest {
        every { uploader.uploadChunk() } throws IOException("connection reset")

        val events = service()
            .upload(libraryId, videoId, fileInfo(), UploadControl())
            .toList()

        val failed = events.last() as UploadEvent.Failed
        assertTrue(failed.error is BunnyError.Network)
        assertEquals(videoId, failed.videoId)
    }

    @Test
    fun `failing to create the upload fails the flow instead of throwing`() = runTest {
        every { anyConstructed<TusClient>().resumeOrCreateUpload(any()) } throws
            IOException("cannot reach tus endpoint")

        val events = service()
            .upload(libraryId, videoId, fileInfo(), UploadControl())
            .toList()

        assertEquals(1, events.size)
        val failed = events.single() as UploadEvent.Failed
        assertTrue(failed.error is BunnyError.Network)
    }

    // endregion

    private companion object {
        private const val NO_MORE_CHUNKS = -1
        private const val HELD_MILLIS = 2_000L
    }
}
