package net.bunny.bunnystreamplayer.ui.widget

/** Chooses a single readable tone for the pixels immediately behind the time readout. */
internal object ProgressTextContrast {
    // The controller draws a dark gradient over the video. A moderately bright video frame
    // therefore looks dark behind the glyphs; only near-white video can safely use black text.
    private const val LUMINANCE_THRESHOLD = 0.94
    private const val DARK_AREA_THRESHOLD_PERCENT = 5
    private const val MAX_SAMPLES_PER_AXIS = 64

    const val WHITE = -1
    const val BLACK = -0x1000000

    fun colorForPixels(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Int {
        if (width <= 0 || height <= 0) return WHITE

        val strideX = maxOf(1, (width + MAX_SAMPLES_PER_AXIS - 1) / MAX_SAMPLES_PER_AXIS)
        val strideY = maxOf(1, (height + MAX_SAMPLES_PER_AXIS - 1) / MAX_SAMPLES_PER_AXIS)
        var darkPixels = 0
        var samples = 0
        for (y in 0 until height step strideY) {
            for (x in 0 until width step strideX) {
                val pixel = pixelAt(x, y)
                val red = (pixel ushr 16 and 0xff) / 255.0
                val green = (pixel ushr 8 and 0xff) / 255.0
                val blue = (pixel and 0xff) / 255.0
                val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
                if (luminance <= LUMINANCE_THRESHOLD) darkPixels++
                samples++
            }
        }
        // Average luminance hides dark patches in a predominantly bright frame. A meaningful
        // dark patch keeps the text white; its shadow protects the part over bright pixels.
        return if (darkPixels * 100 >= samples * DARK_AREA_THRESHOLD_PERCENT) WHITE else BLACK
    }
}
