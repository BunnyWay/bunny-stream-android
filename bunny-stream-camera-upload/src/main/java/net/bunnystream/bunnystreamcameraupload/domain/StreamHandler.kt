package net.bunnystream.bunnystreamcameraupload.domain

import android.view.ViewGroup
import net.bunny.bunnystreamcameraupload.DeviceCamera
import net.bunny.bunnystreamcameraupload.RecordingDurationListener
import net.bunny.bunnystreamcameraupload.RecordingStateListener

internal interface StreamHandler {
    var recordingStateListener: RecordingStateListener?

    var recordingDurationListener: RecordingDurationListener?

    fun initialize(container: ViewGroup, deviceCamera: DeviceCamera)

    fun startStreaming(libraryId: Long)

    fun stopStreaming()

    fun isStreaming(): Boolean

    fun selectCamera(deviceCamera: DeviceCamera)

    fun switchCamera()

    fun isMuted(): Boolean

    fun setMuted(muted: Boolean)
}