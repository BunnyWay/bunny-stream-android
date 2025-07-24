package net.bunnystream.api.playback

interface ResumePositionListener {
    fun onResumePositionAvailable(videoId: String, position: PlaybackPosition)
    fun onResumePositionSaved(videoId: String, position: PlaybackPosition)
}
