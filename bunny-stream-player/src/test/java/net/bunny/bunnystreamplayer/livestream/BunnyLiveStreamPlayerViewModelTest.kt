package net.bunny.bunnystreamplayer.livestream

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CompletableDeferred
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
        val vm = vodPlayingVm(endedRecordedRepo(pollCount = pollCount))
        try {
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
    fun `stopping the poll loop on an ended recording does not block playback recovery`() {
        // The two used to be one flag, so an ended recording that lost the network was stranded
        // behind the terminal panel forever. Polling stays off — there is no status left to watch —
        // while the recovery path keeps working.
        val pollCount = AtomicInteger(0)
        val vm = vodPlayingVm(endedRecordedRepo(pollCount = pollCount))
        try {
            vm.onForeground()
            scheduler.runCurrent()

            vm.onPlaybackFailure(networkFailureInfo())
            scheduler.runCurrent()

            assertEquals("nothing left to poll on an ended stream", 0, pollCount.get())
            assertEquals("recovery still rebuilds the recording", 1, vm.playerRebuildToken.value)
            assertNull(vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `network failure during VOD playback refreshes play data and bumps the rebuild token`() {
        val playDataCount = AtomicInteger(0)
        val vm = vodPlayingVm(endedRecordedRepo(playDataCount = playDataCount))
        try {
            val playDataBefore = playDataCount.get()

            vm.onPlaybackFailure(networkFailureInfo())
            scheduler.runCurrent()

            assertTrue(
                "recovery should refresh play-data (before=$playDataBefore, " +
                    "now=${playDataCount.get()})",
                playDataCount.get() > playDataBefore,
            )
            assertEquals("the recording must be rebuilt", 1, vm.playerRebuildToken.value)
            assertNull("a network drop is never terminal", vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `VOD recovery keeps retrying across a long network outage`() {
        // Nothing bounds a network failure: an outage lasts as long as it lasts, and the player
        // has to still be trying when the connection returns.
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            repeat(12) {
                vm.onPlaybackFailure(networkFailureInfo())
                scheduler.runCurrent()
                // Close the throttle window so the deferred retry fires — a rebuild that fails
                // straight away does the same in production.
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }

            assertEquals("one rebuild per throttle window, a minute in", 12, vm.playerRebuildToken.value)
            assertNull("a network outage never goes terminal", vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `repeated 404 on the recording goes terminal after the attempt bound`() {
        // A CDN answering 404 for a full minute isn't finalising the recording, it hasn't got one.
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            repeat(BunnyLiveStreamPlayerViewModel.MAX_HTTP_RECOVERY_ATTEMPTS - 1) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull("still inside the bound", vm.terminalError.value)
            val tokenAtBound = vm.playerRebuildToken.value

            // The verdict lands on the attempt, not on the engine error that asks for it — so the
            // throttle window has to close before the last attempt runs.
            vm.onPlaybackFailure(failureInfo(httpStatus = 404))
            scheduler.runCurrent()
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            assertEquals(UNAVAILABLE_MESSAGE, vm.terminalError.value)

            scheduler.advanceTimeBy(60_000L); scheduler.runCurrent()
            assertEquals(
                "no rebuild past the bound",
                tokenAtBound,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `404 during the finalization window recovers when the recording appears`() {
        // The ~30 s after a stream stops: /play already says ENDED and hands out a URL, but the
        // CDN is still finalising the playlist behind it and answers 404 until it isn't.
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            repeat(5) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull("five 404s are well inside the bound", vm.terminalError.value)

            vm.onPlaybackStarted()
            scheduler.runCurrent()

            // Five more 404s later on: with the counter reset they are nowhere near the bound.
            repeat(5) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull(
                "the earlier attempts must not carry over the recording that played",
                vm.terminalError.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `successful playback resets the recovery attempt counter`() {
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            // One short of the bound: the very next failure would go terminal.
            repeat(BunnyLiveStreamPlayerViewModel.MAX_HTTP_RECOVERY_ATTEMPTS - 1) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull(vm.terminalError.value)

            vm.onPlaybackStarted()
            scheduler.runCurrent()

            // A second budget, in full: without the reset the very next attempt would be the last.
            repeat(BunnyLiveStreamPlayerViewModel.MAX_HTTP_RECOVERY_ATTEMPTS - 1) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull("playback running makes it attempt one again", vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `the attempt bound counts recoveries, not failure events`() {
        // ExoPlayer can raise several errors inside one throttle window (the manifest, then a
        // segment). They share one recovery, so they must share one unit of the budget — counting
        // events would burn the ~60 s in a few seconds and cut into the CDN's finalisation window.
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            // Ten windows, three failures each: 30 events, 11 recoveries.
            repeat(10) {
                repeat(3) {
                    vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                    scheduler.runCurrent()
                }
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull("30 failures, 11 attempts — well inside the bound", vm.terminalError.value)

            repeat(3) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
            }
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            assertEquals("the twelfth attempt is the last", UNAVAILABLE_MESSAGE, vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `404 while the stream is live is never bounded`() {
        // A 404 on the manifest is routine while the stream is RUNNING but the playlist isn't
        // published yet. The bound belongs to the ended recording alone; here the player retries
        // for as long as the stream is live.
        val rounds = BunnyLiveStreamPlayerViewModel.MAX_HTTP_RECOVERY_ATTEMPTS + 4
        val repo = FakeRepo(
            pollResult = { BunnyResult.Ok(runningStream()) },
            playData = { BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8")) },
        )
        val vm = newVm(repo)
        try {
            vm.unavailableMessage = UNAVAILABLE_MESSAGE
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)

            repeat(rounds) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }

            assertNull("a live stream's 404 is never the last word", vm.terminalError.value)
            assertEquals("one rebuild per window, all the way through", rounds, vm.playerRebuildToken.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `404 while the recording is still processing is never bounded`() {
        // VOD_PROCESSING resolves to VodPlay too, but the recording isn't final and polling is
        // still on — the CDN's 404 means "not yet", exactly as it does on a live manifest.
        val rounds = BunnyLiveStreamPlayerViewModel.MAX_HTTP_RECOVERY_ATTEMPTS + 4
        val pollCount = AtomicInteger(0)
        val vm = newVm(vodProcessingRepo(pollCount))
        try {
            vm.unavailableMessage = UNAVAILABLE_MESSAGE
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            assertTrue(
                "expected VodPlay while processing, got ${vm.state.value}",
                vm.state.value is LiveStreamPlayerState.VodPlay,
            )
            vm.onForeground()
            scheduler.runCurrent()

            repeat(rounds) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }

            assertNull("processing is not the CDN's verdict", vm.terminalError.value)
            assertEquals("one rebuild per window", rounds, vm.playerRebuildToken.value)
            assertTrue(
                "the status can still change, so polling must stay on (got ${pollCount.get()})",
                pollCount.get() > 0,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `the attempt bound starts fresh when the ended recording takes over`() {
        // Failures the live edge absorbed say nothing about whether the recording is being
        // finalised. Carrying them over would leave the recording less than the ~30 s it needs.
        val status = java.util.concurrent.atomic.AtomicReference(LiveStreamStatus.RUNNING)
        val stream = { runningStream().copy(status = status.get(), recordVod = true) }
        val repo = FakeRepo(
            pollResult = { BunnyResult.Ok(stream()) },
            playData = {
                BunnyResult.Ok(
                    playDataWithUrl("https://vod.test/recording.m3u8")
                        .let { it.copy(liveStream = stream()) },
                )
            },
        )
        val vm = newVm(repo)
        try {
            vm.unavailableMessage = UNAVAILABLE_MESSAGE
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            vm.onForeground()
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)

            // Six windows of 404 while the stream is still running — routine, and unbounded.
            repeat(6) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull(vm.terminalError.value)

            // The stream stops; the next poll flips the player onto the recording.
            status.set(LiveStreamStatus.ENDED)
            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            assertTrue(
                "expected VodPlay once the stream ended, got ${vm.state.value}",
                vm.state.value is LiveStreamPlayerState.VodPlay,
            )

            // A whole budget bar one, all of it spent on the recording: still not terminal. Had the
            // six from the live phase carried over, the recording would have run out mid-way.
            repeat(BunnyLiveStreamPlayerViewModel.MAX_HTTP_RECOVERY_ATTEMPTS - 1) {
                vm.onPlaybackFailure(failureInfo(httpStatus = 404))
                scheduler.runCurrent()
                scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            }
            assertNull("the recording gets the bound to itself", vm.terminalError.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `playback starting does not reopen the throttle window`() {
        // A half-finalised recording plays its first segment — which reports playback started — and
        // 404s on the next one. With the window cleared, every rebuild replays that same first
        // segment with no wait at all: a tear-down loop that never gets anywhere.
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            vm.onPlaybackFailure(networkFailureInfo())
            scheduler.runCurrent()
            assertEquals(1, vm.playerRebuildToken.value)

            vm.onPlaybackStarted()
            scheduler.runCurrent()

            vm.onPlaybackFailure(networkFailureInfo())
            scheduler.runCurrent()
            assertEquals(
                "the failure right after playback started still waits its turn",
                1,
                vm.playerRebuildToken.value,
            )

            scheduler.advanceTimeBy(5_000L); scheduler.runCurrent()
            assertEquals("and recovers once the window closes", 2, vm.playerRebuildToken.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `recovery waits out the play-data fetch already in flight`() {
        // Two failures land while one recovery's play-data refresh is still out. The second
        // recovery must ride on that refresh — no third fetch queued behind it, and no second
        // rebuild once the first one has already torn the player down on the same URL.
        val gate = CompletableDeferred<Unit>()
        val playDataCalls = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = { BunnyResult.Ok(runningStream()) },
            playData = {
                if (playDataCalls.incrementAndGet() == 2) gate.await()
                BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8"))
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            assertEquals(1, playDataCalls.get())

            vm.onPlaybackFailure("first failure")
            scheduler.runCurrent()
            assertEquals("the first recovery's refresh is out", 2, playDataCalls.get())

            vm.onPlaybackFailure("second failure, inside the throttle window")
            scheduler.advanceTimeBy(5_001)
            scheduler.runCurrent()
            assertEquals(
                "the deferred recovery must not queue a third fetch",
                2,
                playDataCalls.get(),
            )
            assertEquals("nor rebuild before the refresh lands", 0, vm.playerRebuildToken.value)

            gate.complete(Unit)
            scheduler.runCurrent()

            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            assertEquals("one rebuild serves both failures", 1, vm.playerRebuildToken.value)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `a failure during the initial play-data fetch initialises on the landed URL`() {
        // The engine failed while start()'s play-data was still out, so nothing has been built
        // yet. When the fetch lands the surface initialises on that URL by itself; bumping the
        // token on top of it would tear the fresh player down at once.
        val gate = CompletableDeferred<Unit>()
        val playDataCalls = AtomicInteger(0)
        val repo = FakeRepo(
            pollResult = { BunnyResult.Ok(runningStream()) },
            playData = {
                if (playDataCalls.incrementAndGet() == 1) gate.await()
                BunnyResult.Ok(playDataWithUrl("https://live.test/p.m3u8"))
            },
        )
        val vm = newVm(repo)
        try {
            vm.start(libraryId = 1L, streamId = "s")
            scheduler.runCurrent()
            assertEquals("start()'s fetch is still out", 1, playDataCalls.get())
            assertTrue(vm.state.value is LiveStreamPlayerState.Loading)

            vm.onPlaybackFailure("failed while the fetch was out")
            scheduler.runCurrent()
            assertEquals(
                "recovery must not queue a second fetch behind the first",
                1,
                playDataCalls.get(),
            )

            gate.complete(Unit)
            scheduler.runCurrent()

            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)
            assertEquals(
                "the landed URL is new, so there is nothing to rebuild",
                0,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `blocked 403 on the recording is terminal`() {
        // Same verdict as a blocked live stream — a recording the CDN refuses stays refused.
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            val tokenBefore = vm.playerRebuildToken.value
            val info = failureInfo(httpStatus = 403)
            vm.onPlaybackFailure(info)
            scheduler.runCurrent()
            assertEquals(info.userMessage, vm.terminalError.value)

            scheduler.advanceTimeBy(60_000L); scheduler.runCurrent()
            assertEquals(
                "no rebuild for a blocked recording",
                tokenBefore,
                vm.playerRebuildToken.value,
            )
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun `dns sinkhole on the recording is terminal`() {
        val vm = vodPlayingVm(endedRecordedRepo())
        try {
            val tokenBefore = vm.playerRebuildToken.value
            val info = sinkholeInfo()
            vm.onPlaybackFailure(info)
            scheduler.runCurrent()
            assertEquals(info.userMessage, vm.terminalError.value)

            scheduler.advanceTimeBy(60_000L); scheduler.runCurrent()
            assertEquals(
                "no rebuild for a sinkholed recording",
                tokenBefore,
                vm.playerRebuildToken.value,
            )
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
    fun `the friendly no-internet copy does not make a network failure terminal`() {
        // The viewer copy is only copy — the verdict comes from the status and the sinkhole, both
        // absent here. Reading the message instead would strand a live stream behind the terminal
        // panel for as long as the outage lasts.
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
            assertTrue(vm.state.value is LiveStreamPlayerState.LivePlay)

            val dropped = networkFailureInfo()
            assertEquals("No internet connection", dropped.userMessage)
            vm.onPlaybackFailure(dropped)
            scheduler.runCurrent()

            assertEquals("the outage still rebuilds the player", 1, vm.playerRebuildToken.value)
            assertNull("a lost connection is never terminal", vm.terminalError.value)
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

    /**
     * The engine's report for a plain network drop: no HTTP status, no sinkhole — nothing the
     * server said, so nothing that bounds the retries. The viewer copy is the friendly
     * "No internet connection" the player swaps in; only [PlaybackFailureInfo.rawMessage] keeps
     * the engine's own text.
     */
    private fun networkFailureInfo(): PlaybackFailureInfo = PlaybackFailureInfo(
        errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
        httpStatus = null,
        rawMessage = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: Source error",
        userMessage = "No internet connection",
    )

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

    /** The same stream after it stopped, with a recording — what resolves to `VodPlay`. */
    private fun endedRecordedStream() = runningStream().copy(
        status = LiveStreamStatus.ENDED,
        recordVod = true,
        endedAt = "2023-11-14T23:00:00Z",
    )

    /**
     * A repository for a stream that has ENDED with a recording: every poll and every play-data
     * fetch reports the same finished stream and the same final recording URL.
     */
    private fun endedRecordedRepo(
        pollCount: AtomicInteger = AtomicInteger(0),
        playDataCount: AtomicInteger = AtomicInteger(0),
    ): FakeRepo {
        val ended = endedRecordedStream()
        return FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(ended)
            },
            playData = {
                playDataCount.incrementAndGet()
                BunnyResult.Ok(
                    playDataWithUrl("https://vod.test/recording.m3u8")
                        .let { it.copy(liveStream = ended) },
                )
            },
        )
    }

    /**
     * The same stream one step earlier: the recording is still being processed. It resolves to
     * `VodPlay` exactly like the ended one, but the status can still change — polling stays on and
     * nothing about the recording is final yet.
     */
    private fun vodProcessingRepo(pollCount: AtomicInteger = AtomicInteger(0)): FakeRepo {
        val processing = endedRecordedStream().copy(status = LiveStreamStatus.VOD_PROCESSING)
        return FakeRepo(
            pollResult = {
                pollCount.incrementAndGet()
                BunnyResult.Ok(processing)
            },
            playData = {
                BunnyResult.Ok(
                    playDataWithUrl("https://vod.test/recording.m3u8")
                        .let { it.copy(liveStream = processing) },
                )
            },
        )
    }

    /**
     * A view model already settled in [LiveStreamPlayerState.VodPlay]: the stream ended with a
     * recording, so the poll loop is off and playback recovery is the only thing left running.
     */
    private fun vodPlayingVm(repo: FakeRepo): BunnyLiveStreamPlayerViewModel {
        val vm = newVm(repo)
        // The real composable hands this in from resources; the view model owns no Context.
        vm.unavailableMessage = UNAVAILABLE_MESSAGE
        vm.start(libraryId = 1L, streamId = "s")
        scheduler.runCurrent() // play-data lands → VodPlay → polling stops
        assertTrue(
            "expected VodPlay, got ${vm.state.value}",
            vm.state.value is LiveStreamPlayerState.VodPlay,
        )
        return vm
    }

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
        // Suspending on purpose: a test that needs a fetch to still be in flight gates this on its
        // own [CompletableDeferred] and completes it when it wants the response to land.
        private val playData: suspend () -> BunnyResult<LiveStreamPlayData> =
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

    private companion object {
        /** Stands in for `R.string.error_video_not_available`, which a JVM test can't resolve. */
        const val UNAVAILABLE_MESSAGE = "Video is not available"
    }
}
