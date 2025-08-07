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
    fun playVideo(videoId: String, libraryId: Long?)

    /**
     * Pauses video
     */
    fun pause()

    /**
     * Resumes playing video
     */
    fun play()
}