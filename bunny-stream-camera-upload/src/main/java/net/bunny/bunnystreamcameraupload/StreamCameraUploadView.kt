package net.bunny.bunnystreamcameraupload

import android.view.View

interface StreamCameraUploadView {
    /**
     * Hides default controls, if you plan to use your own
     */
    var hideDefaultControls: Boolean

    /**
     * Click listener to receive close clicked event so you can handle it,
     * e.g. finish hosting activity or navigate to some other screen
     */
    var closeStreamClickListener: View.OnClickListener?

    /**
     * Listener to receive events about stream status
     * @see RecordingStateListener
     */
    var streamStateListener: RecordingStateListener?

    /**
     * Listener to receive stream duration
     * @see RecordingDurationListener
     */
    var streamDurationListener: RecordingDurationListener?

    /**
     * Starts camera preview
     */
    fun startPreview()

    /**
     * Stops streaming
     */
    fun stopRecording()

    /**
     * Switches stream camera
     */
    fun switchCamera()

    /**
     * Mutes/un-mutes audio
     */
    fun setAudioMuted(muted: Boolean)

    /**
     * Check if streaming is in progress
     */
    fun isRecording(): Boolean
}