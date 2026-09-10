package net.bunny.bunnystreamplayer.livestream

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A recovery rebuild of the ended stream's recording used to restart it from 00:00: the viewer
 * lost their place every time the connection dropped. These cases pin the one decision that
 * prevents it — which of the engine's last reported positions is worth restoring.
 */
class LiveStreamResumePositionTest {

    @Test
    fun `mid-recording position is restored`() {
        assertEquals(12_000L, resumePositionAfterRebuild(currentMs = 12_000L, durationMs = 40_000L))
    }

    @Test
    fun `a failure before playback got anywhere restarts from the beginning`() {
        assertNull(resumePositionAfterRebuild(currentMs = 0L, durationMs = 40_000L))
    }

    @Test
    fun `a viewer who reached the end restarts from the beginning`() {
        // Within the end-of-recording tolerance: replaying the last second would be the surprise.
        assertNull(resumePositionAfterRebuild(currentMs = 39_500L, durationMs = 40_000L))
    }

    @Test
    fun `an unknown duration does not throw away a position we have`() {
        assertNull(resumePositionAfterRebuild(currentMs = 0L, durationMs = C.TIME_UNSET))
        assertEquals(
            8_000L,
            resumePositionAfterRebuild(currentMs = 8_000L, durationMs = C.TIME_UNSET),
        )
        assertEquals(8_000L, resumePositionAfterRebuild(currentMs = 8_000L, durationMs = 0L))
    }

    @Test
    fun `a position past the reported duration restarts from the beginning`() {
        assertNull(resumePositionAfterRebuild(currentMs = 41_000L, durationMs = 40_000L))
    }
}
