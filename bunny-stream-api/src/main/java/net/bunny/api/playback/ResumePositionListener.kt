package net.bunny.api.playback

interface ResumePositionListener {
    fun onResumePositionAvailable(videoId: String, position: PlaybackPosition)
    fun onResumePositionSaved(videoId: String, position: PlaybackPosition)
}
