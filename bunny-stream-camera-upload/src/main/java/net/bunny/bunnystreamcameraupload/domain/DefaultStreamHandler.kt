package net.bunny.bunnystreamcameraupload.domain

import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.Toast
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.fold
import net.bunny.api.error.map
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
import net.bunny.bunnystreamcameraupload.util.redactSecrets

/**
 * Drives the camera broadcast over one **or two** RTMP ingest outputs via RootEncoder's
 * [MultiStream] (one encode → N outputs).
 *
 * * **Single-publish (default):** one output, reconnecting with the iOS SDK's policy
 *   ([ReconnectPolicy]): up to 5 attempts since the last successful connect, exponential backoff
 *   capped at 8 s, **alternating primary <-> backup** on every attempt when a backup ingest is
 *   configured. On top of the reactive path, a 5 s `GET /live/{id}/status` poll drives the
 *   Primary/Backup badges from the server's truth and **proactively fails over** when the ingest
 *   we publish to goes silent (RTMP up, Bunny not receiving) for two consecutive polls.
 * * **Dual-publish (opt-in, [dualPublish]):** two outputs to primary + backup **simultaneously**,
 *   each reconnecting independently on a fixed delay (unchanged by the single-mode policy); the
 *   stream stays live as long as either is up. Doubles the upload bandwidth, hence opt-in. Falls
 *   back to single when the stream has no backup ingest.
 */
internal class DefaultStreamHandler(
    private val streamRepository: RecordingRepository,
    coroutineDispatcher: CoroutineDispatcher
) : StreamHandler {
    companion object {
        private const val TAG = "StreamHandler"

        /** Dual-mode per-output retry delay (each output sticks to its own host). */
        private const val RETRY_DELAY_MS = 5000L

        /** RTMP output capacity: index 0 = primary slot, index 1 = backup slot. */
        private const val RTMP_OUTPUTS = 2

        /** How often the broadcaster polls `GET /live/{id}/status` while publishing. */
        private const val INGEST_STATUS_POLL_MS = 5_000L

        /**
         * Consecutive not-live `/status` polls (≈5 s each) on the currently-published ingest that
         * trigger a proactive failover (single mode). Matches the iOS SDK.
         */
        private const val PROACTIVE_FAILOVER_MISS_THRESHOLD = 2
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

    // region — single-mode reconnect + proactive-failover state (see [ReconnectPolicy])

    /** Single-mode ingest URLs resolved at start. Backup `null` when the stream has none. */
    private var singlePrimaryUrl: String? = null
    private var singleBackupUrl: String? = null

    /** Whether the single-mode output currently targets the backup ingest. */
    private var usingBackup = false

    /**
     * Proactive failover (single mode): whether `/status` has confirmed the currently-published
     * ingest live at least once — guards against acting during the startup window.
     */
    private var currentIngestConfirmedLive = false

    /** Consecutive `/status` polls reporting the current ingest not-live after confirmation. */
    private var consecutiveIngestMisses = 0

    /** 5 s `/status` poll while publishing to a live stream (badges + proactive failover). */
    private var ingestStatusJob: Job? = null

    // endregion

    /** Per-output state. [index] maps to the RTMP output slot. */
    private class Output(val index: Int) {
        var endpoint: IngestEndpoint = if (index == 0) IngestEndpoint.PRIMARY else IngestEndpoint.BACKUP
        var url: String? = null

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
        Log.d(TAG, "onConnectionStarted[$index] ${out.endpoint} ${url.redactSecrets()}")
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
        // The new connection isn't server-confirmed yet — /status will confirm it.
        currentIngestConfirmedLive = false
        consecutiveIngestMisses = 0
        startIngestStatusPolling()
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
                onOk = { Log.d(TAG, "live stream marked as started") },
                onErr = { error ->
                    Log.w(TAG, "startLiveStream failed: ${error.message}")
                    liveStartRequested = false
                },
            )
        }
    }

    private fun handleFailed(index: Int, reason: String) {
        val out = outputs[index]
        if (!out.active) return
        out.failures++
        out.connected = false
        Log.w(
            TAG,
            "onConnectionFailed[$index] ${out.endpoint} attempt=${out.failures} " +
                    "reason=\"$reason\" url=${out.url?.redactSecrets()}"
        )
        val c = client(index)
        var toastMsg = openGlView.context.getString(R.string.stream_reconnecting)
        val scheduled = when {
            // Dual mode: each output just keeps reconnecting to its own host (no switching).
            dualActive -> c.reTry(RETRY_DELAY_MS, reason, null)

            // Single mode: iOS-style policy — up to MAX_SINGLE_RETRIES attempts since the last
            // successful connect, exponential backoff (1,2,4,8,8 s), alternating primary <->
            // backup on every attempt when a backup ingest exists.
            out.failures <= ReconnectPolicy.MAX_SINGLE_RETRIES -> {
                val previousEndpoint = out.endpoint
                usingBackup = ReconnectPolicy.nextUsesBackup(
                    currentlyUsingBackup = usingBackup,
                    hasBackup = singleBackupUrl != null,
                )
                val target = (if (usingBackup) singleBackupUrl else singlePrimaryUrl)
                    ?: singlePrimaryUrl.orEmpty()
                out.url = target
                out.endpoint = if (usingBackup) IngestEndpoint.BACKUP else IngestEndpoint.PRIMARY
                // Changing/re-establishing the connection: the target isn't server-confirmed yet.
                currentIngestConfirmedLive = false
                consecutiveIngestMisses = 0
                if (out.endpoint != previousEndpoint) {
                    notify(previousEndpoint, IngestEndpointState.OFFLINE)
                    toastMsg = openGlView.context.getString(
                        if (usingBackup) {
                            R.string.ingest_switched_to_backup
                        } else {
                            R.string.ingest_switched_to_primary
                        },
                    )
                    Log.w(TAG, "failover[$index] → ${out.endpoint} (${target.redactSecrets()})")
                }
                c.reTry(ReconnectPolicy.reconnectDelayMs(out.failures), reason, target)
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
            stopIngestStatusPolling()
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

    // region — ingest /status polling (server-truth badges + proactive failover)

    /**
     * Polls `GET /live/{id}/status` every [INGEST_STATUS_POLL_MS] while publishing to a live
     * stream. Drives the Primary/Backup badges from the server's truth (whether Bunny is actually
     * receiving data — the RTMP callbacks only know whether *we* are sending) and feeds the
     * proactive failover. No-op for VOD recordings (no live stream to poll).
     */
    private fun startIngestStatusPolling() {
        val live = activeLiveStream ?: return
        if (ingestStatusJob?.isActive == true) return
        ingestStatusJob = scope.launch {
            while (isActive) {
                streamRepository.getIngestStatus(live.first, live.second).fold(
                    onOk = { status -> onIngestStatus(status.primaryLive, status.backupLive) },
                    onErr = { error -> Log.w(TAG, "ingest status poll failed: ${error.message}") },
                )
                delay(INGEST_STATUS_POLL_MS)
            }
        }
    }

    private fun stopIngestStatusPolling() {
        ingestStatusJob?.cancel()
        ingestStatusJob = null
        currentIngestConfirmedLive = false
        consecutiveIngestMisses = 0
    }

    private fun onIngestStatus(primaryLive: Boolean, backupLive: Boolean) {
        // Badges from the server's truth. LIVE means Bunny is receiving on that ingest; a
        // not-live ingest we're not actively reconnecting to reads as OFFLINE. The RTMP
        // callbacks still provide the immediate CONNECTING transitions in between polls.
        notify(IngestEndpoint.PRIMARY, if (primaryLive) IngestEndpointState.LIVE else IngestEndpointState.OFFLINE)
        if (singleBackupUrl != null || dualActive) {
            notify(IngestEndpoint.BACKUP, if (backupLive) IngestEndpointState.LIVE else IngestEndpointState.OFFLINE)
        }
        proactiveFailoverIfNeeded(primaryLive, backupLive)
    }

    /**
     * Proactively fails over (single mode only) when `/status` reports the ingest we're
     * publishing to as not-live for [PROACTIVE_FAILOVER_MISS_THRESHOLD] consecutive polls —
     * catching "silent" degradations where the RTMP/TCP connection stays up but Bunny stops
     * receiving, so the reactive reconnect never fires. Mirrors the iOS SDK. Dual-publish is
     * exempt: both ingests are already published simultaneously.
     */
    private fun proactiveFailoverIfNeeded(primaryLive: Boolean, backupLive: Boolean) {
        if (dualActive) return
        if (singleBackupUrl == null) return
        val out = outputs[0]
        // Only while we believe we're publishing; a reactive reconnect is already in charge
        // otherwise (failures > 0 means a retry is scheduled/running).
        if (!out.active || !out.connected || out.failures > 0) return

        val liveOnCurrent = if (usingBackup) backupLive else primaryLive
        if (liveOnCurrent) {
            currentIngestConfirmedLive = true
            consecutiveIngestMisses = 0
            return
        }
        // Ignore the startup window: act only once the current ingest was confirmed live.
        if (!currentIngestConfirmedLive) return
        consecutiveIngestMisses++
        if (consecutiveIngestMisses < PROACTIVE_FAILOVER_MISS_THRESHOLD) return
        consecutiveIngestMisses = 0
        currentIngestConfirmedLive = false
        Log.w(TAG, "proactive failover — ${out.endpoint} silent on /status for 2 polls")
        // Route through the same path as a reactive failure so the alternating policy,
        // backoff, badges and toasts all apply.
        MainScope().launch {
            handleFailed(0, "proactive failover: ingest silent (/status)")
        }
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
                // RootEncoder's own logging prints the RTMP publish command verbatim, and the
                // stream name Bunny requires carries the library access key. That would put a
                // working credential in logcat on every broadcast, on release builds too. The
                // connection state we actually need is already logged through the checkers above,
                // with the key redacted.
                setLogs(false)
                // Dual-mode per-output retry budget. Single mode overrides this at start:
                // RootEncoder's internal counter never resets on success, while our policy counts
                // failures since the last successful connect — so the internal counter must not
                // be the limiting factor there (see startWithEndpoint).
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

    private fun startWithEndpoint(prepare: suspend () -> BunnyResult<ResolvedIngest>) {
        recordingStateListener?.onStreamInitializing()
        scope.launch {
            when (val result = prepare()) {
                is BunnyResult.Err -> {
                    MainScope().launch {
                        recordingStateListener?.onStreamConnectionFailed(result.message)
                    }
                }

                is BunnyResult.Ok -> {
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
                        singlePrimaryUrl = null
                        singleBackupUrl = null
                        // Restore the dual-mode retry budget in case a previous single-mode
                        // session raised it (the handler instance is reused across sessions).
                        for (i in 0 until RTMP_OUTPUTS) client(i).setReTries(12)
                        outputs[0].apply { url = primary; endpoint = IngestEndpoint.PRIMARY; active = true }
                        outputs[1].apply { url = backup; endpoint = IngestEndpoint.BACKUP; active = true }
                        Log.d(TAG, "startStream DUAL primary=${primary.redactSecrets()} + backup=${backup.redactSecrets()}")
                        stream.startStream(MultiType.RTMP, 0, primary)
                        stream.startStream(MultiType.RTMP, 1, backup)
                    } else {
                        dualActive = false
                        singlePrimaryUrl = primary
                        singleBackupUrl = backup
                        usingBackup = false
                        // Our policy (failures since the last successful connect) is the limiting
                        // factor in single mode — RootEncoder's non-resetting internal counter
                        // must never give up first across a long session with occasional blips.
                        client(0).setReTries(Int.MAX_VALUE)
                        outputs[0].apply {
                            url = primary
                            endpoint = IngestEndpoint.PRIMARY
                            active = true
                        }
                        Log.d(TAG, "startStream SINGLE primary=${primary.redactSecrets()} hasBackup=${backup != null}")
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
            it.failures = 0
            it.active = false
            it.connected = false
        }
        usingBackup = false
        currentIngestConfirmedLive = false
        consecutiveIngestMisses = 0
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
        stopIngestStatusPolling()
        recordingStateListener?.onStreamStopped()

        // End the Bunny live stream server-side (mirrors the dashboard's "End stream" button);
        // merely disconnecting RTMP leaves the stream live/preview for viewers.
        val live = activeLiveStream
        if (live != null) {
            activeLiveStream = null
            liveStartRequested = false
            scope.launch {
                streamRepository.stopLiveStream(live.first, live.second).fold(
                    onOk = { Log.d(TAG, "live stream stopped server-side") },
                    onErr = { error -> Log.w(TAG, "stopLiveStream failed: ${error.message}") },
                )
            }
        }
    }

    override fun isStreaming(): Boolean {
        // [stream] exists only after [initialize]; a view that never started a preview asks this
        // from its detach hook, and "not initialized" simply means "not streaming".
        return ::stream.isInitialized && stream.isStreaming
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
