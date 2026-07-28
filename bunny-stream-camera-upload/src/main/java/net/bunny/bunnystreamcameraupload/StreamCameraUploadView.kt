package net.bunny.bunnystreamcameraupload

import android.view.View

/**
 * Contract of the camera capture view, [BunnyStreamCameraUpload]. The same view covers two cases:
 *
 * - [liveStreamId] not set (default): the camera records straight to a new video in your library.
 * - [liveStreamId] set: the camera broadcasts to that existing live stream. The SDK starts the
 *   stream on the server when the connection is up and ends it when you call [stopRecording].
 *
 * Before use: call `BunnyStreamApi.initialize(...)` and make sure the `CAMERA` and `RECORD_AUDIO`
 * runtime permissions are granted - [startPreview] does nothing without them. Configure
 * [liveStreamId], [dualPublish] and the listeners before starting, not mid-broadcast.
 *
 * The view ships its own controls (start/stop, mute, camera switch, close, status). Set
 * [hideDefaultControls] to true to drive it from your own UI through the methods below.
 */
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
     * Starts the camera preview.
     *
     * Requires the `CAMERA` and `RECORD_AUDIO` runtime permissions to be granted already. When
     * either is missing the call logs a warning and returns without an error callback - the host
     * app must request the permissions first and call this again after they are granted.
     */
    fun startPreview()

    /**
     * Stops the recording or broadcast. For a live stream ([liveStreamId] set) this also ends the
     * stream on the server, so viewers see it as ended. Stop through this method rather than just
     * tearing the view down; otherwise the stream stays live for viewers until the server times
     * it out.
     */
    fun stopRecording()

    /**
     * Switches between the front and back camera. Works during preview and mid-broadcast.
     */
    fun switchCamera()

    /**
     * Mutes or unmutes the microphone. The video keeps streaming either way.
     */
    fun setAudioMuted(muted: Boolean)

    /**
     * Returns true while a recording or live broadcast is running. Covers both the VOD-record and
     * the live-broadcast case despite the name.
     */
    fun isRecording(): Boolean
}