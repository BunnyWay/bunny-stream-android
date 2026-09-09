package net.bunny.bunnystreamcameraupload.domain

import android.view.ViewGroup
import net.bunny.bunnystreamcameraupload.DeviceCamera
import net.bunny.bunnystreamcameraupload.RecordingDurationListener
import net.bunny.bunnystreamcameraupload.RecordingStateListener

internal interface StreamHandler {
    var recordingStateListener: RecordingStateListener?

    var recordingDurationListener: RecordingDurationListener?

    /** Publish to primary + backup simultaneously (see [StreamCameraUploadView.dualPublish]). */
    var dualPublish: Boolean

    fun initialize(container: ViewGroup, deviceCamera: DeviceCamera)

    fun startStreaming(libraryId: Long)

    /**
     * Starts broadcasting to an existing Bunny live stream (RTMP publish using its streamKey).
     * Pass `null` for [ingestEndpoint] to use the SDK default ingest host.
     */
    fun startLiveStreaming(libraryId: Long, streamId: String, ingestEndpoint: String? = null)

    fun stopStreaming()

    fun isStreaming(): Boolean

    fun selectCamera(deviceCamera: DeviceCamera)

    fun switchCamera()

    fun isMuted(): Boolean

    fun setMuted(muted: Boolean)
}