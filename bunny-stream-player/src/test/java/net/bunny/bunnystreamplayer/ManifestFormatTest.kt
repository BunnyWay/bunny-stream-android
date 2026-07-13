package net.bunny.bunnystreamplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies [ManifestFormat.fromUrl] — the HLS/DASH classifier that drives the media3 MIME and the
 * CMCD `sf`. The contract that matters: **every current Bunny URL stays HLS** (so existing playback
 * is byte-for-byte unchanged) and only an explicit `.mpd` switches to DASH.
 */
class ManifestFormatTest {

    @Test
    fun `dash manifests resolve to DASH`() {
        assertEquals(ManifestFormat.DASH, ManifestFormat.fromUrl("https://x.b-cdn.net/live/s/manifest.mpd"))
        assertEquals(ManifestFormat.DASH, ManifestFormat.fromUrl("https://x/Manifest.MPD"))
        assertEquals(ManifestFormat.DASH, ManifestFormat.fromUrl("https://x/s/manifest.mpd?token=abc&ver=1"))
        assertEquals(ManifestFormat.DASH, ManifestFormat.fromUrl("https://x/s/manifest.mpd#frag"))
    }

    @Test
    fun `hls and Bunny's real live and vod URLs resolve to HLS`() {
        // The exact shapes seen from Bunny /play (verified on device).
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl("https://vz-x.b-cdn.net/live/019f/live.m3u8"))
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl("https://vz-x.b-cdn.net/019f/playlist.m3u8"))
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl("https://vz-x.b-cdn.net/live/019f/live.m3u8?token=t"))
    }

    @Test
    fun `extension-less and unknown URLs default to HLS`() {
        // Bunny's fallbackUrl prefix has no manifest extension; unknown -> HLS (never accidental DASH).
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl("https://vz-x.b-cdn.net/019f/play_"))
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl("https://x/some/path"))
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl("https://x/s/manifest.mpd/"))
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl(""))
        assertEquals(ManifestFormat.HLS, ManifestFormat.fromUrl(null))
    }

    @Test
    fun `cmcd sf codes`() {
        assertEquals("h", ManifestFormat.HLS.cmcdSf)
        assertEquals("d", ManifestFormat.DASH.cmcdSf)
    }
}
