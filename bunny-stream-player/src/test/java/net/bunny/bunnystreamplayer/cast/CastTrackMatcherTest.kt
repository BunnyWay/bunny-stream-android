package net.bunny.bunnystreamplayer.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastTrackMatcherTest {

    private val tracks = listOf(
        RemoteTrack(1, isAudio = true, language = "en", name = "English"),
        RemoteTrack(2, isAudio = true, language = "de-DE", name = "Deutsch"),
        RemoteTrack(3, isAudio = false, language = "en", name = "English CC"),
        RemoteTrack(4, isAudio = false, language = "sl", name = "Slovenščina"),
    )

    @Test
    fun `matches audio track by exact language`() {
        assertEquals(1L, CastTrackMatcher.pickTrack(tracks, true, "en", null)?.id)
    }

    @Test
    fun `matches audio track by primary language subtag`() {
        assertEquals(2L, CastTrackMatcher.pickTrack(tracks, true, "de", null)?.id)
        assertEquals(1L, CastTrackMatcher.pickTrack(tracks, true, "en-US", null)?.id)
    }

    @Test
    fun `falls back to name matching`() {
        assertEquals(2L, CastTrackMatcher.pickTrack(tracks, true, null, "deutsch")?.id)
    }

    @Test
    fun `does not cross track types`() {
        assertEquals(4L, CastTrackMatcher.pickTrack(tracks, false, "sl", null)?.id)
        assertNull(CastTrackMatcher.pickTrack(tracks, true, "sl", null))
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(CastTrackMatcher.pickTrack(tracks, true, "fr", "Français"))
        assertNull(CastTrackMatcher.pickTrack(emptyList(), true, "en", null))
    }

    @Test
    fun `active ids preserve the other track type`() {
        // Switching audio 1 -> 2 must keep caption track 3 active.
        assertArrayEquals(
            longArrayOf(3L, 2L),
            CastTrackMatcher.buildActiveTrackIds(tracks, listOf(1L, 3L), replaceAudio = true, 2L),
        )
    }

    @Test
    fun `null track id deactivates the type`() {
        // Captions off keeps the audio track untouched.
        assertArrayEquals(
            longArrayOf(1L),
            CastTrackMatcher.buildActiveTrackIds(tracks, listOf(1L, 3L), replaceAudio = false, null),
        )
    }

    @Test
    fun `unknown active ids are preserved`() {
        assertArrayEquals(
            longArrayOf(99L, 2L),
            CastTrackMatcher.buildActiveTrackIds(tracks, listOf(99L, 1L), replaceAudio = true, 2L),
        )
    }
}
