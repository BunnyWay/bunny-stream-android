package net.bunny.bunnystreamplayer.livestream

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.livestream.domain.model.LiveStreamList
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.livestream.domain.model.LiveStreamThumbnail
import net.bunny.api.model.LiveStreamStatus
import net.bunny.bunnystreamplayer.PlaybackFailureInfo
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
                BunnyResult.Err(BunnyError.Auth(403, "Forbidden"))
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
                BunnyResult.Err(BunnyError.Http(503, "Service Unavailable"))
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
                BunnyResult.Err(BunnyError.Network("Network error: timeout"))
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
                BunnyResult.Err(BunnyError.Http(500, "Server"))
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
                BunnyResult.Err(BunnyError.Http(500, "Server"))
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
                BunnyResult.Err(BunnyError.Http(410, "Gone"))
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
            pollResult = { BunnyResult.Ok(runningStream()) },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
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

    @Test
    fun `playback failure while live re-polls and bumps the rebuild token`() {
        val pollCount = AtomicInteger(0)
        val playDataCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(runningStream())
            },
            playData = {
                playDataCount.incrementAndGet()
                BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8"))
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            val pollsBefore = pollCount.get()
            val playDataBefore = playDataCount.get()

            vm.onPlaybackFailure("boom")
            scheduler.runCurrent()

            assertTrue("failure should trigger an immediate poll", pollCount.get() > pollsBefore)
            assertTrue(
                "failure should refresh play-data",
                playDataCount.get() > playDataBefore,
            )
            assertEquals(
                "still-live stream should request a player rebuild",
                1,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `failure inside the throttle window defers one retry instead of dropping it`() {
        // nowEpochMs is pinned to 0 in these tests, so the second call lands "0 ms later": it
        // must not rebuild immediately (throttle) but MUST schedule a deferred retry — an errored
        // ExoPlayer never re-raises, so dropping it would strand the viewer on a frozen frame.
        val repo = FakeRepo(
            pollResult = { BunnyResult.Ok(runningStream()) },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()

            vm.onPlaybackFailure("first")
            scheduler.runCurrent()
            vm.onPlaybackFailure("second — inside the throttle window")
            scheduler.runCurrent()
            vm.onPlaybackFailure("third — also inside; must not double-schedule")
            scheduler.runCurrent()

            assertEquals("no immediate rebuild inside the window", 1, vm.playerRebuildToken.value)

            // The throttle window closes — exactly one deferred recovery fires.
            scheduler.advanceTimeBy(5_000L)
            scheduler.runCurrent()
            assertEquals("deferred retry must rebuild once", 2, vm.playerRebuildToken.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `playback failure does not rebuild when the stream is no longer live`() {
        // The failure re-poll discovers the stream ended (no recording) — the state flips to
        // Offline and no rebuild must be requested.
        val status = java.util.concurrent.atomic.AtomicReference(LiveStreamStatus.RUNNING)
        val repo = FakeRepo(
            pollResult = { BunnyResult.Ok(runningStream().copy(status = status.get())) },
            playData = {
                BunnyResult.Ok(
                    playDataWithUrl("https://live.test/p.m3u8")
                        .let { it.copy(liveStream = it.liveStream?.copy(status = status.get())) },
                )
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)

            status.set(LiveStreamStatus.ENDED)
            vm.onPlaybackFailure("stream died")
            scheduler.runCurrent()

            assertEquals("no rebuild for a stream that ended", 0, vm.playerRebuildToken.value)
            assertTrue(vm.state.value is LiveStreamPlayerState.Offline)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `polling stops permanently once the ended stream's recording is playing`() {
        // ENDED + recordVod + URL resolves to VodPlay; an ended stream can't restart, so the
        // 5s loop must stop for good (matching iOS) — including across a background/foreground
        // round-trip.
        val pollCount = AtomicInteger(0)
        val endedRecorded = runningStream().copy(
            status = LiveStreamStatus.ENDED,
            recordVod = true,
            endedAt = "2023-11-14T23:00:00Z",
        )
        val repo = FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(endedRecorded)
            },
            playData = {
                BunnyResult.Ok(
                    playDataWithUrl("https://vod.test/recording.m3u8")
                        .let { it.copy(liveStream = endedRecorded) },
                )
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent() // play-data lands → VodPlay → polling stops
            assertTrue(
                "expected VodPlay, got ${vm.state.value}",
                vm.state.value is LiveStreamPlayerState.VodPlay,
            )
            assertNull("stopping the poll loop is not an error", vm.terminalError.value)

            vm.onForeground()
            scheduler.runCurrent()
            scheduler.advanceTimeBy(60_000L)
            scheduler.runCurrent()
            assertEquals("no polls once the recording is playing", 0, pollCount.get())
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `blocked playback failure is terminal and stops polling`() {
        // The CDN answered 403 (geo-blocking, hotlink protection, expired token — not told apart).
        // Product decision: show "Video is not available" and stop; no rebuild loop, no re-poll.
        val pollCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(runningStream())
            },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            val pollsBefore = pollCount.get()
            val tokenBefore = vm.playerRebuildToken.value

            val info = failureInfo(httpStatus = 403)
            vm.onPlaybackFailure(info)
            scheduler.runCurrent()
            assertEquals(info.userMessage, vm.terminalError.value)

            // Twelve poll intervals pass — nothing may fire and nothing may rebuild.
            scheduler.advanceTimeBy(60_000L)
            scheduler.runCurrent()
            assertEquals("no polls after a blocked stream", pollsBefore, pollCount.get())
            assertEquals(
                "no rebuild for a blocked stream",
                tokenBefore,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `dns sinkhole playback failure is terminal like a 403`() {
        // Bunny's "Blocked countries" rejects the CDN host at the DNS level: it resolves to
        // 127.0.0.1 and the connect is refused, so there is no status code at all. It still has
        // to end the same way as a 403 - panel up, polling and rebuilds off.
        val pollCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(runningStream())
            },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            val pollsBefore = pollCount.get()
            val tokenBefore = vm.playerRebuildToken.value

            val info = sinkholeInfo()
            vm.onPlaybackFailure(info)
            scheduler.runCurrent()
            assertEquals(info.userMessage, vm.terminalError.value)

            scheduler.advanceTimeBy(60_000L)
            scheduler.runCurrent()
            assertEquals("no polls after a sinkholed stream", pollsBefore, pollCount.get())
            assertEquals(
                "no rebuild for a sinkholed stream",
                tokenBefore,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `non-blocked playback failure keeps the recovery loop`() {
        // A 404 on the manifest is routine while the stream is RUNNING but the playlist isn't
        // published yet — it must still go through re-poll + rebuild, never the terminal panel.
        val pollCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(runningStream())
            },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            val pollsBefore = pollCount.get()

            vm.onPlaybackFailure(failureInfo(httpStatus = 404))
            scheduler.runCurrent()

            assertNull("a 404 is transient, never terminal", vm.terminalError.value)
            assertTrue("failure should trigger an immediate poll", pollCount.get() > pollsBefore)
            assertEquals(
                "still-live stream should request a player rebuild",
                1,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `blocked playback failure cancels the pending deferred recovery`() {
        // A transient failure inside the throttle window leaves one deferred recovery armed. The
        // 403 that lands next must disarm it — otherwise, one interval later, it would re-poll and
        // rebuild the player straight over the terminal panel.
        val pollCount = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(runningStream())
            },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)

            vm.onPlaybackFailure(failureInfo(httpStatus = 404))
            scheduler.runCurrent()
            vm.onPlaybackFailure(failureInfo(httpStatus = 404)) // inside the window — deferred
            scheduler.runCurrent()
            assertEquals("first recovery rebuilt, second is deferred", 1, vm.playerRebuildToken.value)

            val blocked = failureInfo(httpStatus = 403)
            vm.onPlaybackFailure(blocked)
            scheduler.runCurrent()
            val pollsAtTerminal = pollCount.get()
            val tokenAtTerminal = vm.playerRebuildToken.value

            // The throttle window closes, with margin — the deferred recovery must not fire.
            scheduler.advanceTimeBy(6_000L)
            scheduler.runCurrent()
            assertEquals(blocked.userMessage, vm.terminalError.value)
            assertEquals("no poll after the terminal point", pollsAtTerminal, pollCount.get())
            assertEquals(
                "no rebuild after the terminal point",
                tokenAtTerminal,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `blocked playback failure before start is ignored`() {
        val vm = newVm(FakeRepo(pollResult = { BunnyResult.Ok(runningStream()) }))
        vm.onPlaybackFailure(failureInfo(httpStatus = 403))
        scheduler.runCurrent()
        assertNull(vm.terminalError.value)
    }

    // region — Fixtures

    private fun newVm(repo: LiveStreamRepository): BunnyLiveStreamPlayerViewModel =
        BunnyLiveStreamPlayerViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            nowEpochMs = { 0L },
            pollIntervalMs = 5_000L,
        )

    /** The engine's structured report for a bad HTTP status, shaped as DefaultBunnyPlayer builds it. */
    private fun failureInfo(httpStatus: Int): PlaybackFailureInfo {
        val raw = "ERROR_CODE_IO_BAD_HTTP_STATUS: Source error"
        return PlaybackFailureInfo(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS",
            httpStatus = httpStatus,
            rawMessage = raw,
            userMessage = if (httpStatus == 403) "Video is not available" else raw,
        )
    }

    /** The engine's report for a DNS-level geo-block: no status, a refused connect to loopback. */
    private fun sinkholeInfo(): PlaybackFailureInfo = PlaybackFailureInfo(
        errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
        httpStatus = null,
        rawMessage = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: Source error",
        userMessage = "Video is not available",
        sinkholeAddress = "127.0.0.1",
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
        primaryIngestUrl = null,
        backupIngestUrl = null,
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
        private val pollResult: () -> BunnyResult<LiveStream>,
        private val playData: () -> BunnyResult<LiveStreamPlayData> =
            { BunnyResult.Err(BunnyError.Network("not used in this test")) },
    ) : LiveStreamRepository {
        override suspend fun pollLiveStream(libraryId: Long, streamId: String) = pollResult()

        override suspend fun fetchLiveStreamPlayData(
            libraryId: Long, streamId: String, token: String?, expires: Long?,
        ) = playData()

        // Unused in tests — fail loudly if a future test grows to depend on them.
        override suspend fun listLiveStreams(
            libraryId: Long, page: Int?, itemsPerPage: Int?, search: String?,
            orderBy: String?, collectionId: String?,
        ): BunnyResult<LiveStreamList> = error("not implemented for test")

        override suspend fun getLiveStream(
            libraryId: Long, streamId: String,
        ): BunnyResult<LiveStream> = error("not implemented for test")

        override suspend fun createLiveStream(
            libraryId: Long, request: LiveStreamCreateRequest,
        ): BunnyResult<LiveStream> = error("not implemented for test")

        override suspend fun updateLiveStream(
            libraryId: Long, streamId: String, request: LiveStreamCreateRequest,
        ): BunnyResult<Unit> = error("not implemented for test")

        override suspend fun deleteLiveStream(
            libraryId: Long, streamId: String,
        ): BunnyResult<Unit> = error("not implemented for test")

        override suspend fun startLiveStream(
            libraryId: Long, streamId: String,
        ): BunnyResult<LiveStream> = error("not implemented for test")

        override suspend fun stopLiveStream(
            libraryId: Long, streamId: String,
        ): BunnyResult<LiveStream> = error("not implemented for test")

        override suspend fun setLiveStreamThumbnail(
            libraryId: Long, streamId: String, thumbnailUrl: String,
        ): BunnyResult<Unit> = error("not implemented for test")

        override suspend fun uploadLiveStreamThumbnail(
            libraryId: Long, streamId: String, imageBytes: ByteArray, contentType: String,
        ): BunnyResult<Unit> = error("not implemented for test")

        override suspend fun listLiveStreamThumbnails(
            libraryId: Long, streamId: String, limit: Int?, from: String?, to: String?,
        ): BunnyResult<List<LiveStreamThumbnail>> = error("not implemented for test")

        override suspend fun deleteLiveStreamThumbnail(
            libraryId: Long, streamId: String, restoreLibraryDefault: Boolean,
        ): BunnyResult<Unit> = error("not implemented for test")

        override suspend fun getLiveStreamStatus(
            libraryId: Long, streamId: String,
        ): BunnyResult<net.bunny.api.livestream.domain.model.LiveStreamIngestStatus> =
            error("not implemented for test")
    }

    // endregion
}
