package net.bunny.bunnystreamcameraupload.domain

import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.Toast
import arrow.core.Either
import com.pedro.common.ConnectChecker
import com.pedro.common.socket.base.SocketType
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraHelper.Facing
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.bunny.bunnystreamcameraupload.DeviceCamera
import net.bunny.bunnystreamcameraupload.IngestEndpoint
import net.bunny.bunnystreamcameraupload.IngestEndpointState
import net.bunny.bunnystreamcameraupload.RecordingDurationListener
import net.bunny.bunnystreamcameraupload.RecordingStateListener
import net.bunny.bunnystreamcameraupload.util.ScreenUtil
import net.bunny.recording.R

/**
 * Drives the camera broadcast over one **or two** RTMP ingest outputs via RootEncoder's
 * [MultiStream] (one encode → N outputs).
 *
 * * **Single-publish (default):** one output to the primary ingest, with one-way active/standby
 *   failover to the backup (retry the same host a couple of times, then switch to the backup once,
 *   never back — matches the iOS SDK + Bunny's guidance).
 * * **Dual-publish (opt-in, [dualPublish]):** two outputs to primary + backup **simultaneously**,
 *   each reconnecting independently; the stream stays live as long as either is up. Doubles the
 *   upload bandwidth, hence opt-in. Falls back to single when the stream has no backup ingest.
 */
class DefaultStreamHandler(
    private val streamRepository: RecordingRepository,
    coroutineDispatcher: CoroutineDispatcher
) : StreamHandler {
    companion object {
        private const val TAG = "StreamHandler"

        /**
         * Quick retries on the SAME host before failing over to the backup (single mode). Kept in
         * sync with the iOS SDK's `maxRetryCount` so both platforms behave identically.
         */
        private const val RETRIES_BEFORE_SWITCH = 2

        private const val RETRY_DELAY_MS = 5000L

        /** RTMP output capacity: index 0 = primary slot, index 1 = backup slot. */
        private const val RTMP_OUTPUTS = 2
    }

    override var recordingStateListener: RecordingStateListener? = null

    override var recordingDurationListener: RecordingDurationListener? = null

    override var dualPublish: Boolean = false

    private lateinit var openGlView: OpenGlView

    private lateinit var stream: MultiStream

    private val scope = CoroutineScope(coroutineDispatcher)

    private var timerJob: Job? = null

    private var recordingStartTime: Long? = null

    /** Set while broadcasting to a Bunny live stream — (libraryId, streamId). */
    private var activeLiveStream: Pair<Long, String>? = null

    /** Guards against duplicate server-side start calls when an output reconnects. */
    private var liveStartRequested = false

    /** Whether this session is publishing to two outputs at once. */
    private var dualActive = false

    /** Per-output state. [index] maps to the RTMP output slot. */
    private class Output(val index: Int) {
        var endpoint: IngestEndpoint = if (index == 0) IngestEndpoint.PRIMARY else IngestEndpoint.BACKUP
        var url: String? = null

        /** Single-mode failover target (backup host). `null` in dual mode / when no backup. */
        var failoverUrl: String? = null
        var switched = false

        /** Consecutive failures since the last successful connect on this output. */
        var failures = 0

        /** We started this output this session (and haven't given up on it). */
        var active = false
        var connected = false
    }

    private val outputs = Array(RTMP_OUTPUTS) { Output(it) }

    private fun client(index: Int) = stream.getStreamClient(MultiType.RTMP, index)

    private fun notify(endpoint: IngestEndpoint, state: IngestEndpointState) {
        recordingStateListener?.onIngestEndpointChanged(endpoint, state)
    }

    /** Redacts the stream key (last path segment) so URLs are safe to log. */
    private fun String.redactKey(): String =
        substringBeforeLast('/') + "/" + substringAfterLast('/').take(4) + "…"

    private val width = 1920
    private val height = 1080
    private val fps = 30
    private val motionFactor = 0.15
    private val vBitrate = (width * height * fps * motionFactor).toInt()
    private val sampleRate = 44100
    private val isStereo = true
    private val aBitrate = 64 * 1024

    // region — per-output RTMP callbacks

    /** One [ConnectChecker] per output so callbacks are attributable to a specific ingest. */
    private fun makeChecker(index: Int): ConnectChecker = object : ConnectChecker {
        override fun onAuthError() = handleAuthError()
        override fun onAuthSuccess() = Unit
        override fun onConnectionStarted(url: String) = handleStarted(index, url)
        override fun onConnectionSuccess() = handleSuccess(index)
        override fun onConnectionFailed(reason: String) = handleFailed(index, reason)
        override fun onDisconnect() = handleDisconnect(index)
        override fun onNewBitrate(bitrate: Long) = Unit
    }

    private val rtmpCheckers: Array<ConnectChecker> = Array(RTMP_OUTPUTS) { makeChecker(it) }

    private fun handleStarted(index: Int, url: String) {
        val out = outputs[index]
        Log.d(TAG, "onConnectionStarted[$index] ${out.endpoint} ${url.redactKey()}")
        notify(out.endpoint, IngestEndpointState.CONNECTING)
        recordingStateListener?.onStreamConnected()
    }

    private fun handleSuccess(index: Int) {
        val out = outputs[index]
        out.failures = 0
        out.connected = true
        Log.d(TAG, "onConnectionSuccess[$index] ${out.endpoint}")
        notify(out.endpoint, IngestEndpointState.LIVE)
        // Start the timer on the first output that goes live; keep it running across reconnects.
        if (recordingStartTime == null) recordingStartTime = System.currentTimeMillis()
        timerJob?.cancel()
        startTimer()
        startServerLiveOnce()
    }

    /**
     * Marks the Bunny stream started server-side (PREVIEW → RUNNING) exactly once, on the first
     * output that connects — mirrors the dashboard's "Go live" button.
     */
    private fun startServerLiveOnce() {
        val live = activeLiveStream ?: return
        if (liveStartRequested) return
        liveStartRequested = true
        scope.launch {
            streamRepository.startLiveStream(live.first, live.second).fold(
                ifLeft = { message ->
                    Log.w(TAG, "startLiveStream failed: $message")
                    liveStartRequested = false
                },
                ifRight = { Log.d(TAG, "live stream marked as started") },
            )
        }
    }

    private fun handleFailed(index: Int, reason: String) {
        val out = outputs[index]
        if (!out.active) return
        out.failures++
        Log.w(
            TAG,
            "onConnectionFailed[$index] ${out.endpoint} attempt=${out.failures} " +
                    "reason=\"$reason\" url=${out.url?.redactKey()}"
        )
        val c = client(index)
        var toastMsg = openGlView.context.getString(R.string.stream_reconnecting)
        val scheduled = when {
            // Dual mode: each output just keeps reconnecting to its own host (no switching).
            dualActive -> c.reTry(RETRY_DELAY_MS, reason, null)

            // Single mode — transient blip: a couple of quick retries on the SAME host first.
            out.failures < RETRIES_BEFORE_SWITCH -> c.reTry(RETRY_DELAY_MS, reason, null)

            // Single mode — primary won't come up → fail over to the backup, once. Never back.
            out.failoverUrl != null && !out.switched -> {
                out.switched = true
                out.url = out.failoverUrl
                out.endpoint = IngestEndpoint.BACKUP
                out.failures = 0
                notify(IngestEndpoint.PRIMARY, IngestEndpointState.OFFLINE)
                notify(IngestEndpoint.BACKUP, IngestEndpointState.CONNECTING)
                toastMsg = openGlView.context.getString(R.string.ingest_switched_to_backup)
                Log.w(TAG, "failover[$index] → BACKUP (${out.failoverUrl?.redactKey()})")
                c.reTry(RETRY_DELAY_MS, reason, out.failoverUrl)
            }

            else -> false
        }
        if (scheduled) {
            notify(out.endpoint, IngestEndpointState.CONNECTING)
            Toast.makeText(openGlView.context, toastMsg, Toast.LENGTH_SHORT).show()
        } else {
            outputGaveUp(index, reason)
        }
    }

    /** This output exhausted its retries. Stop it; only fail the whole stream if none remain. */
    private fun outputGaveUp(index: Int, reason: String) {
        val out = outputs[index]
        out.active = false
        out.connected = false
        notify(out.endpoint, IngestEndpointState.OFFLINE)
        Log.e(TAG, "output[$index] ${out.endpoint} gave up: \"$reason\"")
        runCatching { stream.stopStream(MultiType.RTMP, index) }
        if (outputs.none { it.active }) {
            timerJob?.cancel()
            recordingStartTime = null
            recordingStateListener?.onStreamConnectionFailed(reason)
        }
    }

    private fun handleDisconnect(index: Int) {
        val out = outputs[index]
        out.connected = false
        Log.d(TAG, "onDisconnect[$index] ${out.endpoint}")
        notify(out.endpoint, IngestEndpointState.OFFLINE)
        // Only surface a stream-level disconnect once every output is down.
        if (outputs.none { it.connected }) {
            timerJob?.cancel()
            recordingStartTime = null
            recordingStateListener?.onStreamDisconnected()
        }
    }

    private fun handleAuthError() {
        Log.d(TAG, "onAuthError")
        stopAllOutputs()
        timerJob?.cancel()
        recordingStartTime = null
        recordingStateListener?.onStreamAuthError()
    }

    // endregion

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!stream.isOnPreview) {
                stream.startPreview(openGlView)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            stream.getGlInterface().setPreviewResolution(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (stream.isOnPreview) stream.stopPreview()
        }
    }

    override fun initialize(container: ViewGroup, deviceCamera: DeviceCamera) {
        stream = MultiStream(
            container.context,
            rtmpCheckers,
            emptyArray(),
            emptyArray(),
            emptyArray(),
            Camera2Source(container.context),
            MicrophoneSource(),
        )
        for (i in 0 until RTMP_OUTPUTS) {
            client(i).apply {
                setSocketType(SocketType.KTOR)
                setLogs(true)
                // Headroom for quick same-host retries + the primary→backup failover.
                setReTries(12)
                setCheckServerAlive(true)
            }
        }

        val prepared = try {
            stream.prepareVideo(width, height, vBitrate) &&
                    stream.prepareAudio(sampleRate, isStereo, aBitrate)
        } catch (e: IllegalArgumentException) {
            false
        }

        Log.d(TAG, "initialize: $prepared")

        openGlView = OpenGlView(container.context)
        val lp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(openGlView, lp)

        openGlView.setAspectRatioMode(AspectRatioMode.Fill)
        stream.getGlInterface().autoHandleOrientation = true
        openGlView.holder.addCallback(surfaceCallback)
        ScreenUtil.lockCurrentOrientation(openGlView)
    }

    override fun startStreaming(libraryId: Long) {
        activeLiveStream = null
        liveStartRequested = false
        // VOD recording has a single endpoint and no failover.
        startWithEndpoint { streamRepository.prepareRecording(libraryId).map { ResolvedIngest(it) } }
    }

    override fun startLiveStreaming(libraryId: Long, streamId: String, ingestEndpoint: String?) {
        activeLiveStream = libraryId to streamId
        liveStartRequested = false
        startWithEndpoint { streamRepository.prepareLiveBroadcast(libraryId, streamId, ingestEndpoint) }
    }

    private fun startWithEndpoint(prepare: suspend () -> Either<String, ResolvedIngest>) {
        recordingStateListener?.onStreamInitializing()
        scope.launch {
            when (val result = prepare()) {
                is Either.Left -> {
                    MainScope().launch {
                        recordingStateListener?.onStreamConnectionFailed(result.value)
                    }
                }

                is Either.Right -> {
                    if (stream.isStreaming) {
                        Log.w(TAG, "startStream skipped — already streaming")
                        return@launch
                    }
                    resetOutputs()
                    val ingest = result.value
                    val primary = ingest.primaryUrl
                    val backup = ingest.backupUrl
                    if (dualPublish && backup != null) {
                        dualActive = true
                        outputs[0].apply { url = primary; endpoint = IngestEndpoint.PRIMARY; active = true }
                        outputs[1].apply { url = backup; endpoint = IngestEndpoint.BACKUP; active = true }
                        Log.d(TAG, "startStream DUAL primary=${primary.redactKey()} + backup=${backup.redactKey()}")
                        stream.startStream(MultiType.RTMP, 0, primary)
                        stream.startStream(MultiType.RTMP, 1, backup)
                    } else {
                        dualActive = false
                        outputs[0].apply {
                            url = primary
                            failoverUrl = backup
                            endpoint = IngestEndpoint.PRIMARY
                            active = true
                        }
                        Log.d(TAG, "startStream SINGLE primary=${primary.redactKey()} hasBackup=${backup != null}")
                        stream.startStream(MultiType.RTMP, 0, primary)
                    }
                }
            }
        }
    }

    private fun resetOutputs() {
        outputs.forEach {
            it.endpoint = if (it.index == 0) IngestEndpoint.PRIMARY else IngestEndpoint.BACKUP
            it.url = null
            it.failoverUrl = null
            it.switched = false
            it.failures = 0
            it.active = false
            it.connected = false
        }
    }

    private fun stopAllOutputs() {
        for (i in 0 until RTMP_OUTPUTS) {
            if (outputs[i].active) {
                runCatching { stream.stopStream(MultiType.RTMP, i) }
                outputs[i].active = false
            }
        }
    }

    override fun stopStreaming() {
        stopAllOutputs()
        recordingStateListener?.onStreamStopped()

        // End the Bunny live stream server-side (mirrors the dashboard's "End stream" button);
        // merely disconnecting RTMP leaves the stream live/preview for viewers.
        val live = activeLiveStream
        if (live != null) {
            activeLiveStream = null
            liveStartRequested = false
            scope.launch {
                streamRepository.stopLiveStream(live.first, live.second).fold(
                    ifLeft = { message -> Log.w(TAG, "stopLiveStream failed: $message") },
                    ifRight = { Log.d(TAG, "live stream stopped server-side") },
                )
            }
        }
    }

    override fun isStreaming(): Boolean {
        return stream.isStreaming
    }

    override fun selectCamera(deviceCamera: DeviceCamera) {
        Log.d(TAG, "selectCamera: $deviceCamera")
        val camera2Source = stream.videoSource as Camera2Source
        val cameraFacing = camera2Source.getCameraFacing()

        if (deviceCamera == DeviceCamera.BACK && cameraFacing == Facing.FRONT) {
            switchCamera()
        } else if (deviceCamera == DeviceCamera.FRONT && cameraFacing == Facing.BACK) {
            switchCamera()
        }
    }

    override fun switchCamera() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.switchCamera()

        when (camera2Source.getCameraFacing()) {
            Facing.FRONT -> recordingStateListener?.onCameraChanged(DeviceCamera.FRONT)
            Facing.BACK -> recordingStateListener?.onCameraChanged(DeviceCamera.BACK)
            else -> { /* no-op */
            }
        }
    }

    override fun isMuted(): Boolean {
        return (stream.audioSource as MicrophoneSource).isMuted()
    }

    override fun setMuted(muted: Boolean) {
        val microphoneSource = stream.audioSource as MicrophoneSource
        if (muted) {
            microphoneSource.mute()
            recordingStateListener?.onAudioMuted(true)
        } else {
            microphoneSource.unMute()
            recordingStateListener?.onAudioMuted(false)
        }
    }

    private fun startTimer() {
        timerJob = MainScope().launch {
            while (isActive) {
                recordingStartTime?.let {
                    val duration = System.currentTimeMillis() - it
                    recordingDurationListener?.onDurationUpdated(
                        duration,
                        duration.toFormattedDuration()
                    )
                    delay(1000)
                }
            }
        }
    }
}
