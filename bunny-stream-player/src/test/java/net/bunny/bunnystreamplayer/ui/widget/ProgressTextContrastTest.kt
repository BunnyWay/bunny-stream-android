package net.bunny.bunnystreamplayer.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTextContrastTest {
    @Test
    fun `mixed frame keeps white even when most of the readout is over white`() {
        val color = ProgressTextContrast.colorForPixels(100, 20) { x, _ ->
            if (x < 75) ProgressTextContrast.WHITE else ProgressTextContrast.BLACK
        }

        assertEquals(ProgressTextContrast.WHITE, color)
    }

    @Test
    fun `uniform light readout chooses black`() {
        val color = ProgressTextContrast.colorForPixels(100, 20) { _, _ -> ProgressTextContrast.WHITE }

        assertEquals(ProgressTextContrast.BLACK, color)
    }

    @Test
    fun `uniform dark readout chooses white`() {
        val color = ProgressTextContrast.colorForPixels(100, 20) { _, _ -> ProgressTextContrast.BLACK }

        assertEquals(ProgressTextContrast.WHITE, color)
    }
}
