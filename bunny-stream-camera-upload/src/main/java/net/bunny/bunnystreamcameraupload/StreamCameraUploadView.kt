package net.bunny.bunnystreamcameraupload

import android.view.View

interface StreamCameraUploadView {
    /**
     * Hides default controls, if you plan to use your own
     */
    var hideDefaultControls: Boolean

    /**
     * When set, the view broadcasts to this existing Bunny *live stream* (RTMP publish using
     * the stream's streamKey) instead of recording a new VOD video. Set to `null` (default)
     * for the classic camera-upload behavior.
     */
    var liveStreamId: String?

    /**
     * Optional override for the live RTMP ingest endpoint (e.g. a regional host shown in the
     * Bunny dashboard). `null` uses the SDK default. Only used when [liveStreamId] is set.
     */
    var liveIngestEndpoint: String?

    /**
     * When `true`, publishes to BOTH the primary and backup ingest **simultaneously**
     * (dual-publish), so a failover is instant — the second ingest is already running.
     *
     * **This doubles the upload bandwidth / data cost**, so it is `false` by default and strictly
     * opt-in — the host app should warn the user before enabling it. Only takes effect when
     * [liveStreamId] is set and the stream actually has a backup ingest; otherwise the SDK falls
     * back to single-publish with active/standby failover. Set before [startPreview].
     */
    var dualPublish: Boolean

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