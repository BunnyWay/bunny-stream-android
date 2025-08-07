package net.bunny.bunnystreamcameraupload

interface RecordingDurationListener {
    /**
     * Called when stream duration changes, called approx. once per second
     * @param durationMillis stream duration in milliseconds
     * @param durationFormatted formatted duration, hh:mm:ss
     */
    fun onDurationUpdated(durationMillis: Long, durationFormatted: String)
}