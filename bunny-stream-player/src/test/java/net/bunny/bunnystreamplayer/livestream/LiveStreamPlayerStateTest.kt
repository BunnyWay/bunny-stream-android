package net.bunny.bunnystreamplayer.livestream

import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import net.bunny.api.model.LiveStreamStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [resolveLiveStreamPlayerState] — locks in the decision table of Bunny's web
 * player. Tests are written to fail loudly if the resolver and that behaviour drift apart.
 */
class LiveStreamPlayerStateTest {

    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z; arbitrary, kept stable across tests

    // region — Spec section 1: display state by stream status

    @Test
    fun `null stream renders Loading`() {
        val state = resolveLiveStreamPlayerState(
            stream = null,
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.Loading, state)
    }

    @Test
    fun `Created status renders NotActive offline`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.CREATED),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Scheduled with countdown opt-in and future start renders Countdown`() {
        val target = now + 60_000L
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = true,
                scheduledStartTime = "2023-11-14T22:14:20Z", // = target
            ),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
            posterUrl = "https://cdn.test/poster.jpg",
        )
        // Countdown now carries the title (for "{title} will start in") and the poster URL (blurred
        // background) so the overlay matches the web player.
        assertEquals(
            LiveStreamPlayerState.Countdown(
                targetEpochMs = target,
                title = "Test",
                posterUrl = "https://cdn.test/poster.jpg",
            ),
            state,
        )
    }

    @Test
    fun `Countdown carries trailer URL as background when trailer is configured and resolved`() {
        // Web-player parity: when a pre-stream trailer is set and its URL is resolved, the
        // countdown plays it (muted, looped) behind the timer instead of the blurred poster.
        val target = now + 60_000L
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = true,
                scheduledStartTime = "2023-11-14T22:14:20Z", // = target
                preStreamTrailerVideoId = "trailer-guid",
                startedAt = null,
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
            posterUrl = "https://cdn.test/poster.jpg",
        )
        assertEquals(
            LiveStreamPlayerState.Countdown(
                targetEpochMs = target,
                title = "Test",
                posterUrl = "https://cdn.test/poster.jpg",
                trailerUrl = "https://trailer.test/playlist.m3u8",
            ),
            state,
        )
    }

    @Test
    fun `Countdown has no trailer URL when no trailer is configured`() {
        val target = now + 60_000L
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = true,
                scheduledStartTime = "2023-11-14T22:14:20Z",
                preStreamTrailerVideoId = null,
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8", // present but no videoId -> ignored
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Countdown(targetEpochMs = target, title = "Test", posterUrl = null),
            state,
        )
    }

    @Test
    fun `Scheduled without countdown opt-in renders NotActive offline`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = false,
                scheduledStartTime = "2023-11-14T22:14:20Z",
            ),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Scheduled with countdown but past start renders NotActive offline`() {
        // Countdown only renders while scheduledStartTime > now. After the moment passes, the
        // spec says fall back to the static offline overlay; the transition to playback happens
        // via the poll, not the timer.
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = true,
                scheduledStartTime = "2023-11-14T22:12:00Z", // before `now`
            ),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Scheduled with null enableCountdown is treated as opt-out`() {
        // Spec: "enableCountdown == true (publisher opt-in - not always set, do not assume)".
        // A null value means we render the offline overlay, not the countdown.
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = null,
                scheduledStartTime = "2099-01-01T00:00:00Z",
            ),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Running with URL renders LivePlay`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.RUNNING),
            playableUrl = "https://live.test/playlist.m3u8",
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.LivePlay("https://live.test/playlist.m3u8"), state)
    }

    @Test
    fun `Running propagates the stream's DVR flag to LivePlay`() {
        assertEquals(
            LiveStreamPlayerState.LivePlay("https://live.test/playlist.m3u8", dvrEnabled = true),
            resolveLiveStreamPlayerState(
                stream = stream(status = LiveStreamStatus.RUNNING, dvrEnabled = true),
                playableUrl = "https://live.test/playlist.m3u8",
                trailerUrl = null,
                nowEpochMs = now,
            ),
        )
        assertEquals(
            LiveStreamPlayerState.LivePlay("https://live.test/playlist.m3u8", dvrEnabled = false),
            resolveLiveStreamPlayerState(
                stream = stream(status = LiveStreamStatus.RUNNING, dvrEnabled = false),
                playableUrl = "https://live.test/playlist.m3u8",
                trailerUrl = null,
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun `Running without URL renders Loading rather than offline`() {
        // While the URL is being fetched, we'd rather hold the spinner than flash an offline
        // overlay. The VM kicks off a play-data re-fetch when it sees Running with no URL.
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.RUNNING),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.Loading, state)
    }

    @Test
    fun `Ended with recordVod renders VodPlay`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.ENDED, recordVod = true),
            playableUrl = "https://vod.test/playlist.m3u8",
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.VodPlay("https://vod.test/playlist.m3u8"), state)
    }

    @Test
    fun `Ended without recordVod renders Ended offline`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.ENDED, recordVod = false),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.Ended),
            state,
        )
    }

    @Test
    fun `Ended with recordVod but no URL falls through to Ended offline`() {
        // Spec: VodProcessing "Plays VOD playlist (when available)" — when the recording isn't
        // ready yet, the same "Live stream ended" copy applies.
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.VOD_PROCESSING, recordVod = true),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.Ended),
            state,
        )
    }

    @Test
    fun `VodProcessing with recordVod and URL renders VodPlay`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.VOD_PROCESSING, recordVod = true),
            playableUrl = "https://vod.test/playlist.m3u8",
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.VodPlay("https://vod.test/playlist.m3u8"), state)
    }

    @Test
    fun `VodProcessing without recordVod renders Ended offline`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.VOD_PROCESSING, recordVod = false),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.Ended),
            state,
        )
    }

    @Test
    fun `Error status renders Error offline`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.ERROR),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.Error),
            state,
        )
    }

    @Test
    fun `Preview status (not in spec) treated as NotActive offline`() {
        // The Bunny enum has a PREVIEW status the web spec doesn't enumerate. The closest
        // matching branch is "not yet running" — render NotActive rather than crash.
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.PREVIEW),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Unknown status falls back to NotActive offline`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(status = LiveStreamStatus.UNKNOWN),
            playableUrl = null,
            trailerUrl = null,
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    // endregion

    // region — Spec optional: pre-stream trailer overrides the offline overlay

    @Test
    fun `Trailer plays in pre-start state when trailerVideoId and URL are set`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.CREATED,
                preStreamTrailerVideoId = "trailer-guid",
                startedAt = null,
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.Trailer("https://trailer.test/playlist.m3u8"), state)
    }

    @Test
    fun `Trailer does not play when stream has already started`() {
        // Spec: trailer only plays "in place of the static offline overlay" while the stream
        // hasn't started. Once startedAt is set, the trailer must not appear — even if status
        // is briefly Created/Error during a bounce.
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.CREATED,
                preStreamTrailerVideoId = "trailer-guid",
                startedAt = "2023-11-14T22:00:00Z",
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Trailer plays for Error status when configured`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.ERROR,
                preStreamTrailerVideoId = "trailer-guid",
                startedAt = null,
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.Trailer("https://trailer.test/playlist.m3u8"), state)
    }

    @Test
    fun `Trailer URL alone without trailerVideoId does not enable trailer branch`() {
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.CREATED,
                preStreamTrailerVideoId = null,
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
        )
        assertEquals(
            LiveStreamPlayerState.Offline(LiveStreamPlayerState.OfflineReason.NotActive),
            state,
        )
    }

    @Test
    fun `Trailer does not play for Running status even when configured`() {
        // Once live, we play the live stream, not the trailer — the spec says playback
        // "switches to the live source".
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.RUNNING,
                preStreamTrailerVideoId = "trailer-guid",
            ),
            playableUrl = "https://live.test/playlist.m3u8",
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
        )
        assertEquals(LiveStreamPlayerState.LivePlay("https://live.test/playlist.m3u8"), state)
    }

    // endregion

    // region — URL priority

    @Test
    fun `resolvePlayableUrl prefers videoPlaylistUrl over fallbackUrl and playbackUrlHls`() {
        val stream = stream(playbackUrlHls = "https://list.test/playlist.m3u8")
        val playData = playData(
            videoPlaylistUrl = "https://playdata.test/playlist.m3u8",
            fallbackUrl = "https://fallback.test/playlist.m3u8",
        )
        assertEquals("https://playdata.test/playlist.m3u8", resolvePlayableUrl(stream, playData))
    }

    @Test
    fun `resolvePlayableUrl falls back to fallbackUrl when videoPlaylistUrl is blank`() {
        val playData = playData(
            videoPlaylistUrl = "",
            fallbackUrl = "https://fallback.test/playlist.m3u8",
        )
        assertEquals(
            "https://fallback.test/playlist.m3u8",
            resolvePlayableUrl(stream(), playData),
        )
    }

    @Test
    fun `resolvePlayableUrl falls back to list URL when play-data has no URLs`() {
        val s = stream(playbackUrlHls = "https://list.test/playlist.m3u8")
        val playData = playData(videoPlaylistUrl = null, fallbackUrl = null)
        assertEquals("https://list.test/playlist.m3u8", resolvePlayableUrl(s, playData))
    }

    @Test
    fun `resolvePlayableUrl returns null when no URL is available anywhere`() {
        assertNull(resolvePlayableUrl(stream(playbackUrlHls = null), null))
    }

    // endregion

    // region — Timestamp parsing edge cases

    @Test
    fun `parseEpochMs returns null for malformed timestamps`() {
        assertNull(parseEpochMs("not-a-date"))
        assertNull(parseEpochMs(""))
        assertNull(parseEpochMs(null))
    }

    @Test
    fun `parseEpochMs handles ISO-8601 with milliseconds`() {
        val ms = parseEpochMs("2023-11-14T22:14:20.500Z")
        assertTrue(ms != null && ms > 0)
    }

    @Test
    fun `parseEpochMs handles zone-less timestamps as UTC`() {
        // The Manage Live Streams API often omits the trailing Z (e.g. "2026-06-10T10:00:00").
        // Instant.parse rejects that, so the countdown silently never fired — we now treat it as
        // UTC. 2026-06-10T10:00:00Z == 1781085600000 ms.
        assertEquals(1781085600000L, parseEpochMs("2026-06-10T10:00:00"))
        // Same instant with explicit Z must match.
        assertEquals(parseEpochMs("2026-06-10T10:00:00Z"), parseEpochMs("2026-06-10T10:00:00"))
    }

    @Test
    fun `Scheduled with countdown and zone-less future start renders Countdown`() {
        // Regression: a future scheduledStartTime without a Z used to fall through to the offline
        // / trailer branch because the timestamp failed to parse.
        val state = resolveLiveStreamPlayerState(
            stream = stream(
                status = LiveStreamStatus.SCHEDULED,
                enableCountdown = true,
                scheduledStartTime = "2026-06-10T10:00:00", // no Z, in the future relative to `now`
            ),
            playableUrl = null,
            trailerUrl = "https://trailer.test/playlist.m3u8",
            nowEpochMs = now,
        )
        assertTrue(state is LiveStreamPlayerState.Countdown)
    }

    // endregion

    // region — Test fixtures

    private fun stream(
        status: LiveStreamStatus = LiveStreamStatus.CREATED,
        recordVod: Boolean = false,
        enableCountdown: Boolean? = null,
        scheduledStartTime: String? = null,
        startedAt: String? = null,
        preStreamTrailerVideoId: String? = null,
        playbackUrlHls: String? = null,
        dvrEnabled: Boolean = false,
    ): LiveStream = LiveStream(
        id = "stream-guid",
        videoLibraryId = 1L,
        title = "Test",
        description = null,
        category = null,
        collectionId = null,
        isPublic = false,
        status = status,
        dateCreated = "2023-11-14T00:00:00Z",
        scheduledStartTime = scheduledStartTime,
        scheduledEndTime = null,
        startedAt = startedAt,
        endedAt = null,
        durationSeconds = null,
        streamKey = null,
        playbackUrlHls = playbackUrlHls,
        dvrEnabled = dvrEnabled,
        dvrWindowSeconds = null,
        recordVod = recordVod,
        availableResolutions = null,
        width = null,
        height = null,
        framerate = null,
        ingestRegion = null,
        peakConcurrentViewers = null,
        totalViewerSeconds = null,
        thumbnailFileName = null,
        thumbnailUpdatedAt = null,
        enableCountdown = enableCountdown,
        rtmpOutputs = emptyList(),
        preStreamTrailerVideoId = preStreamTrailerVideoId,
        primaryIngestUrl = null,
        backupIngestUrl = null,
    )

    private fun playData(
        videoPlaylistUrl: String?,
        fallbackUrl: String?,
    ): LiveStreamPlayData = LiveStreamPlayData(
        liveStream = null,
        libraryName = null,
        captionsPath = null,
        seekPath = null,
        thumbnailUrl = null,
        fallbackUrl = fallbackUrl,
        videoPlaylistUrl = videoPlaylistUrl,
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

    // endregion
}
