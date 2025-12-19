package net.bunny.bunnystreamplayer.ui

import net.bunny.bunnystreamplayer.model.PlayerIconSet

interface BunnyPlayer {

    /**
     * Apply custom icons to video player interface
     */
    var iconSet: PlayerIconSet

    /**
     * Plays a video and fetches additional info, e.g. chapters, moments and subtitles
     *
     * @param videoId Video ID
     */
    fun playVideo(videoId: String, libraryId: Long?, videoTitle: String)

    /**
     * Pauses video
     */
    fun pause()

    /**
     * Resumes playing video
     */
    fun play()

    /**
     * Get current position of video in milliseconds
     */
    fun getCurrentPosition(): Long

    /**
     * Get total duration of video in milliseconds
     */
    fun getDuration(): Long

    /**
     * Get playback progress as a value between 0.0 and 1.0
     * @return Progress (0.0 = start, 1.0 = end)
     */
    fun getProgress(): Float

    /**
     * Check if video is currently playing
     */
    fun isPlaying(): Boolean

    /**
     * Check if video has ended
     */
    fun isEnded(): Boolean

    interface ProgressListener {
        fun onProgressChanged(position: Long, duration: Long, progress: Float)
    }

    /**
     * Set a listener to receive progress updates
     */
    fun setProgressListener(listener: ProgressListener?)
}