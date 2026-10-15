package net.bunny.bunnystreamplayer.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdTagTest {

    private val tag = "https://pubads.g.doubleclick.net/gampad/ads?iu=/21775744923/external/single_ad_samples"

    @Test
    fun `an http or https tag is used as is`() {
        assertEquals(tag, adTagUrlOrNull(tag))
        assertEquals("http://ads.example.com/vast.xml", adTagUrlOrNull("http://ads.example.com/vast.xml"))
        assertEquals("HTTPS://ads.example.com/vast.xml", adTagUrlOrNull("HTTPS://ads.example.com/vast.xml"))
    }

    @Test
    fun `surrounding whitespace from the dashboard field is trimmed`() {
        assertEquals(tag, adTagUrlOrNull("  $tag\n"))
    }

    @Test
    fun `a missing or blank tag means no ads`() {
        assertNull(adTagUrlOrNull(null))
        assertNull(adTagUrlOrNull(""))
        assertNull(adTagUrlOrNull("   "))
    }

    @Test
    fun `a value that is not an http url means no ads`() {
        assertNull(adTagUrlOrNull("pubads.g.doubleclick.net/gampad/ads"))
        assertNull(adTagUrlOrNull("ftp://ads.example.com/vast.xml"))
        assertNull(adTagUrlOrNull("javascript:alert(1)"))
        assertNull(adTagUrlOrNull("null"))
    }

    @Test
    fun `the first load gets the ads`() {
        assertEquals(tag, liveAdTagForLoad("stream-1", tag, adsLoadedFor = null))
    }

    @Test
    fun `reloading the same content does not play the pre-roll again`() {
        // Play data refresh or a playback-failure recovery on the live edge.
        assertNull(liveAdTagForLoad("stream-1", tag, adsLoadedFor = "stream-1"))
    }

    @Test
    fun `the recording is new content and gets its own pre-roll`() {
        assertEquals(tag, liveAdTagForLoad("vod-stream-1", tag, adsLoadedFor = "stream-1"))
    }

    @Test
    fun `no tag means no ads on any load`() {
        assertNull(liveAdTagForLoad("stream-1", null, adsLoadedFor = null))
        assertNull(liveAdTagForLoad("stream-1", " ", adsLoadedFor = null))
    }
}
