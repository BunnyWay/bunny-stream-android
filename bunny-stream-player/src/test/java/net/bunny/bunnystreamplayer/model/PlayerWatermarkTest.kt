package net.bunny.bunnystreamplayer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerWatermarkTest {

    @Test
    fun `default values match iOS defaults`() {
        val watermark = PlayerWatermark(imageUrl = "https://example.com/logo.png")

        assertEquals(PlayerWatermark.Position.TOP_TRAILING, watermark.position)
        assertEquals(0.18f, watermark.relativeWidth)
        assertEquals(0.85f, watermark.opacity)
        assertEquals(12f, watermark.marginDp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank imageUrl is rejected`() {
        PlayerWatermark(imageUrl = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid imageUrl is rejected`() {
        PlayerWatermark(imageUrl = "not-a-url")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero relativeWidth is rejected`() {
        PlayerWatermark(imageUrl = "https://example.com/logo.png", relativeWidth = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `relativeWidth above one is rejected`() {
        PlayerWatermark(imageUrl = "https://example.com/logo.png", relativeWidth = 1.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative opacity is rejected`() {
        PlayerWatermark(imageUrl = "https://example.com/logo.png", opacity = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `opacity above one is rejected`() {
        PlayerWatermark(imageUrl = "https://example.com/logo.png", opacity = 1.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative margin is rejected`() {
        PlayerWatermark(imageUrl = "https://example.com/logo.png", marginDp = -1f)
    }
}
