package net.bunny.api.livestream.domain

import net.bunny.api.livestream.domain.model.LiveStream
import net.bunny.api.model.LiveStreamStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the terminal-vs-transient classification used by the live-stream player's polling
 * loop. The web-player spec (`project_live_stream_player_behavior`, section 3) treats
 * 401/403/404/410 as permanent — anything else (5xx, network errors → status 0) is transient.
 * If this list drifts, the player will either give up too early or hammer Bunny forever; both
 * are user-visible regressions.
 */
class LiveStreamPollResultTest {

    @Test fun `401 Unauthorized is terminal`() {
        assertTrue(failure(statusCode = 401).isTerminal())
    }

    @Test fun `403 Forbidden is terminal`() {
        assertTrue(failure(statusCode = 403).isTerminal())
    }

    @Test fun `404 Not Found is terminal`() {
        assertTrue(failure(statusCode = 404).isTerminal())
    }

    @Test fun `410 Gone is terminal`() {
        // 410 is included because Bunny returns it when a stream's library has been deleted.
        assertTrue(failure(statusCode = 410).isTerminal())
    }

    @Test fun `400 Bad Request is NOT terminal`() {
        // 400 is in the 4xx range but not in the terminal list — it usually means we constructed
        // a bad request (transient bug), not that the stream is gone.
        assertFalse(failure(statusCode = 400).isTerminal())
    }

    @Test fun `5xx server errors are transient`() {
        listOf(500, 502, 503, 504).forEach { code ->
            assertFalse("$code should be transient", failure(statusCode = code).isTerminal())
        }
    }

    @Test fun `status code 0 (network failure) is transient`() {
        // Transport-level failures (DNS, socket reset, timeout) come through as statusCode == 0.
        // Spec lumps them with 5xx — keep polling.
        assertFalse(failure(statusCode = 0).isTerminal())
    }

    @Test fun `Success has no terminal classification`() {
        val success = LiveStreamPollResult.Success(stubStream())
        assertEquals(stubStream(), success.stream)
    }

    private fun failure(statusCode: Int) =
        LiveStreamPollResult.Failure(statusCode = statusCode, message = "msg")

    private fun stubStream() = LiveStream(
        id = "s",
        videoLibraryId = 1L,
        title = "",
        description = null,
        category = null,
        collectionId = null,
        isPublic = false,
        status = LiveStreamStatus.RUNNING,
        dateCreated = "",
        scheduledStartTime = null,
        scheduledEndTime = null,
        startedAt = null,
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
}
