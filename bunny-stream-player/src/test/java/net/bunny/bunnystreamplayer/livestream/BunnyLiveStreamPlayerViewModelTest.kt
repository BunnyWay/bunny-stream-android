package net.bunny.bunnystreamplayer.livestream

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bunny.api.livestream.domain.LiveStreamPollResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.LiveStreamList
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.model.LiveStreamStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the polling lifecycle and terminal-vs-transient handling described in the web player
 * spec (`project_live_stream_player_behavior`, section 3).
 *
 * **Why no `runTest`:** in kotlinx-coroutines-test 1.6.4 the polling loop (`while { delay(5s);
 * poll }`) tripped two failure modes with `runTest` — `advanceUntilIdle()` would never reach
 * idle, and the test scope's scheduler didn't always line up with the dispatcher we'd installed
 * via `Dispatchers.setMain`. The pattern here side-steps both: we install an
 * [UnconfinedTestDispatcher] as Main so `viewModelScope.launch` runs eagerly to first suspension,
 * then drive virtual time by hand via the dispatcher's `scheduler`. Each test cancels the poll
 * job with `vm.onBackground()` in `finally` so the next test starts clean.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BunnyLiveStreamPlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scheduler get() = testDispatcher.scheduler

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `terminal status code stops polling permanently`() {
        // Repository returns 403 every time. After the first failure, polling should stop and
        // the in-flight counter should never advance again — even after several poll intervals.
        val callCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                callCount.incrementAndGet()
                LiveStreamPollResult.Failure(statusCode = 403, message = "Forbidden")
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent() // drain start()'s play-data fetch
            vm.onForeground()
            scheduler.runCurrent() // immediate poll fires

            assertEquals(1, callCount.get())
            assertNotNull("terminal error should be set after 403", vm.terminalError.value)

            // Several more intervals pass — nothing should fire because polling was terminated.
            scheduler.advanceTimeBy(60_000L)
            scheduler.runCurrent()
            assertEquals("no polls after terminal status", 1, callCount.get())
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `transient status code keeps polling`() {
        // 5xx and network errors must not stop the loop — the spec is explicit: "Treat them as
        // transient; back off if you want, but do not stop."
        val callCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                callCount.incrementAndGet()
                LiveStreamPollResult.Failure(statusCode = 503, message = "Service Unavailable")
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent() // immediate poll

            // Three more interval ticks. `advanceTimeBy(5000)` plus `runCurrent` lands on each
            // tick exactly; the loop's next `delay(5000)` re-queues for 5s later, which we
            // explicitly choose not to enter.
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()

            assertTrue(
                "expected polling to continue past transient failures (got ${callCount.get()})",
                callCount.get() >= 4,
            )
            assertNull(
                "terminal error must remain null on transient failures",
                vm.terminalError.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `network error (status 0) is treated as transient`() {
        // DNS/socket/timeout errors come through as statusCode == 0. The spec lumps these with
        // 5xx — keep polling.
        val callCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                callCount.incrementAndGet()
                LiveStreamPollResult.Failure(statusCode = 0, message = "Network error: timeout")
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            assertTrue(callCount.get() >= 3)
            assertNull(vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `onBackground cancels the poll loop`() {
        val callCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                callCount.incrementAndGet()
                LiveStreamPollResult.Failure(statusCode = 500, message = "Server")
            },
        )
        val vm = newVm(repo)
        vm.start(libraryId = 1L, streamId = "s")
        scheduler.runCurrent()
        vm.onForeground()
        scheduler.runCurrent()
        val before = callCount.get()

        vm.onBackground()
        // Pretend the user backgrounded the app for a minute. No new polls should fire.
        scheduler.advanceTimeBy(60_000L)
        scheduler.runCurrent()
        assertEquals("no polls while backgrounded", before, callCount.get())
    }

    @Test
    fun `onForeground after onBackground fires an immediate poll`() {
        // Spec: "On return to foreground, fire one immediate poll, then resume the interval."
        val callCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                callCount.incrementAndGet()
                LiveStreamPollResult.Failure(statusCode = 500, message = "Server")
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            vm.onBackground()
            val before = callCount.get()

            vm.onForeground()
            scheduler.runCurrent() // immediate poll should run without advancing virtual time
            assertTrue(
                "expected at least one new poll right after foreground " +
                    "(before=$before, now=${callCount.get()})",
                callCount.get() > before,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `410 Gone is terminal`() {
        val repo = FakeRepo(
            pollResult = {
                LiveStreamPollResult.Failure(statusCode = 410, message = "Gone")
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertNotNull(vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `successful poll updates state from Loading`() {
        val repo = FakeRepo(
            pollResult = { LiveStreamPollResult.Success(runningStream()) },
            playData = { Either.Right(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent() // play-data fetch from start()
            vm.onForeground()
            scheduler.runCurrent() // immediate poll

            // Either the play-data response (live) or the poll (live) should have flipped us to
            // LivePlay. Both code paths converge here.
            assertEquals(LiveStreamPlayerState.LivePlay("https://live.test/p.m3u8"), vm.state.value)
        } finally {
            vm.onBackground()
        }
    }

    // region — Fixtures

    private fun newVm(repo: LiveStreamRepository): BunnyLiveStreamPlayerViewModel =
        BunnyLiveStreamPlayerViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            nowEpochMs = { 0L },
            pollIntervalMs = 5_000L,
        )

    private fun runningStream() = LiveStream(
        id = "s",
        videoLibraryId = 1L,
        title = "Test",
        description = null,
        category = null,
        collectionId = null,
        isPublic = false,
        status = LiveStreamStatus.RUNNING,
        dateCreated = "2023-11-14T00:00:00Z",
        scheduledStartTime = null,
        scheduledEndTime = null,
        startedAt = "2023-11-14T22:00:00Z",
        endedAt = null,
        durationSeconds = null,
        streamKey = null,
        playbackUrlHls = null,
        dvrEnabled = false,
        dvrWindowSeconds = null,
        recordVod = false,
        availableResolutions = null,
        width = null,
        height = null,
        framerate = null,
        ingestRegion = null,
        peakConcurrentViewers = null,
        totalViewerSeconds = null,
        thumbnailFileName = null,
        thumbnailUpdatedAt = null,
        enableCountdown = null,
        rtmpOutputs = emptyList(),
        preStreamTrailerVideoId = null,
    )

    private fun playDataWithUrl(url: String) = LiveStreamPlayData(
        liveStream = runningStream(),
        libraryName = null,
        captionsPath = null,
        seekPath = null,
        thumbnailUrl = null,
        fallbackUrl = null,
        videoPlaylistUrl = url,
        originalUrl = null,
        previewUrl = null,
        controls = "",
        enableDRM = false,
        drmVersion = 0,
        keyColor = 0,
        vastTagUrl = null,
        captionsFontSize = 0,
        captionsFontColor = null,
        captionsBackgroundColor = null,
        uiLanguage = null,
        allowEarlyPlay = false,
        tokenAuthEnabled = false,
        enableMP4Fallback = false,
        showHeatmap = false,
        fontFamily = null,
        playbackSpeeds = emptyList(),
        widevineMinClientSecurityLevel = null,
        zoneTier = null,
        rememberPlayerPosition = false,
        enableCompactControls = false,
    )

    /**
     * Lightweight fake [LiveStreamRepository] — only the methods touched by the polling code path
     * have meaningful implementations; the rest throw. Lambdas let each test customise the polling
     * outcome without subclassing.
     */
    private class FakeRepo(
        private val pollResult: () -> LiveStreamPollResult,
        private val playData: () -> Either<String, LiveStreamPlayData> =
            { Either.Left("not used in this test") },
    ) : LiveStreamRepository {
        override suspend fun pollLiveStream(libraryId: Long, streamId: String) = pollResult()

        override suspend fun fetchLiveStreamPlayData(
            libraryId: Long, streamId: String, token: String?, expires: Long?,
        ) = playData()

        // Unused in tests — fail loudly if a future test grows to depend on them.
        override suspend fun listLiveStreams(
            libraryId: Long, page: Int?, itemsPerPage: Int?, search: String?,
            orderBy: String?, collectionId: String?,
        ): Either<String, LiveStreamList> = error("not implemented for test")

        override suspend fun getLiveStream(
            libraryId: Long, streamId: String,
        ): Either<String, LiveStream> = error("not implemented for test")

        override suspend fun createLiveStream(
            libraryId: Long, request: LiveStreamCreateRequest,
        ): Either<String, LiveStream> = error("not implemented for test")

        override suspend fun updateLiveStream(
            libraryId: Long, streamId: String, request: LiveStreamCreateRequest,
        ): Either<String, Unit> = error("not implemented for test")

        override suspend fun deleteLiveStream(
            libraryId: Long, streamId: String,
        ): Either<String, Unit> = error("not implemented for test")

        override suspend fun startLiveStream(
            libraryId: Long, streamId: String,
        ): Either<String, LiveStream> = error("not implemented for test")

        override suspend fun stopLiveStream(
            libraryId: Long, streamId: String,
        ): Either<String, LiveStream> = error("not implemented for test")

        override suspend fun setLiveStreamThumbnail(
            libraryId: Long, streamId: String, thumbnailUrl: String,
        ): Either<String, Unit> = error("not implemented for test")

        override suspend fun uploadLiveStreamThumbnail(
            libraryId: Long, streamId: String, imageBytes: ByteArray, contentType: String,
        ): Either<String, Unit> = error("not implemented for test")
    }

    // endregion
}
