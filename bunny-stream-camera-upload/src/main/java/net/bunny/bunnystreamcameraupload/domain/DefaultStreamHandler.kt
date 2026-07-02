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
import com.pedro.library.generic.GenericStream
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
import net.bunny.bunnystreamcameraupload.RecordingDurationListener
import net.bunny.bunnystreamcameraupload.RecordingStateListener
import net.bunny.bunnystreamcameraupload.util.ScreenUtil
import net.bunny.recording.R

class DefaultStreamHandler(
    private val streamRepository: RecordingRepository,
    coroutineDispatcher: CoroutineDispatcher
) : StreamHandler {
    companion object {
        private const val TAG = "StreamHandler"

        /**
         * Quick retries on the SAME host before failing over to the backup. Kept in sync with the
         * iOS SDK's `maxRetryCount` so both platforms behave identically.
         */
        private const val RETRIES_BEFORE_SWITCH = 2

        private const val RETRY_DELAY_MS = 5000L
    }

    override var recordingStateListener: RecordingStateListener? = null

    override var recordingDurationListener: RecordingDurationListener? = null

    private lateinit var openGlView: OpenGlView

    private lateinit var genericStream: GenericStream

    private val scope = CoroutineScope(coroutineDispatcher)

    private var timerJob: Job? = null

    private var recordingStartTime: Long? = null

    /** Set while broadcasting to a Bunny live stream — (libraryId, streamId). */
    private var activeLiveStream: Pair<Long, String>? = null

    /** Guards against duplicate start calls when RTMP reconnects mid-session. */
    private var liveStartRequested = false

    /** Diagnostics: the RTMP URL we're publishing to, and when the connect attempt began. */
    private var streamUrl: String? = null
    private var connectStartedAt: Long? = null
    private var connectFailures = 0

    /** Failover: primary/backup RTMP URLs (with stream key) and which one is currently active. */
    private var primaryUrl: String? = null
    private var backupUrl: String? = null
    private var activeEndpoint: IngestEndpoint = IngestEndpoint.PRIMARY

    /** Failures since the last successful connect on the CURRENT endpoint. */
    private var failuresOnCurrent = 0

    private fun notifyEndpoint(connected: Boolean) {
        recordingStateListener?.onIngestEndpointChanged(activeEndpoint, connected)
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

    private val connectChecker: ConnectChecker = object : ConnectChecker {
        override fun onAuthError() {
            Log.d(TAG, "ConnectChecker onAuthError")
            genericStream.stopStream()
            timerJob?.cancel()
            recordingStateListener?.onStreamAuthError()
            recordingStartTime = null
        }

        override fun onAuthSuccess() {
            Log.d(TAG, "ConnectChecker onAuthSuccess")
        }

        override fun onConnectionFailed(reason: String) {
            connectFailures++
            failuresOnCurrent++
            val elapsed = connectStartedAt?.let { System.currentTimeMillis() - it } ?: -1
            Log.w(
                TAG,
                "onConnectionFailed reason=\"$reason\" endpoint=$activeEndpoint " +
                        "attempt=$failuresOnCurrent elapsedMs=$elapsed " +
                        "url=${streamUrl?.redactKey()} isStreaming=${genericStream.isStreaming}"
            )
            val client = genericStream.getStreamClient()
            val backup = backupUrl
            var toast = openGlView.context.getString(R.string.stream_reconnecting)
            val scheduled = when {
                // Transient blip: a couple of quick retries on the SAME host first.
                failuresOnCurrent < RETRIES_BEFORE_SWITCH ->
                    client.reTry(RETRY_DELAY_MS, reason, null)

                // Primary won't come up → fail over to the backup, once. We deliberately never
                // switch back to primary (mirrors the iOS SDK + Bunny's guidance); once on the
                // backup, exhausting the retries ends the stream.
                activeEndpoint == IngestEndpoint.PRIMARY && backup != null -> {
                    activeEndpoint = IngestEndpoint.BACKUP
                    failuresOnCurrent = 0
                    streamUrl = backup
                    notifyEndpoint(connected = false)
                    toast = openGlView.context.getString(R.string.ingest_switched_to_backup)
                    Log.w(TAG, "failover → BACKUP (${backup.redactKey()})")
                    client.reTry(RETRY_DELAY_MS, reason, backup)
                }

                else -> false
            }
            if (scheduled) {
                Toast.makeText(openGlView.context, toast, Toast.LENGTH_SHORT).show()
            } else {
                Log.e(
                    TAG,
                    "giving up after $connectFailures attempt(s): \"$reason\" " +
                            "(url=${streamUrl?.redactKey()}, totalElapsedMs=$elapsed)"
                )
                genericStream.stopStream()
                timerJob?.cancel()
                recordingStateListener?.onStreamConnectionFailed(reason)
                recordingStartTime = null
            }
        }

        override fun onConnectionStarted(url: String) {
            connectStartedAt = System.currentTimeMillis()
            connectFailures = 0
            Log.d(TAG, "onConnectionStarted endpoint=$activeEndpoint ${url.redactKey()}")
            notifyEndpoint(connected = false)
            recordingStateListener?.onStreamConnected()
        }

        override fun onConnectionSuccess() {
            val elapsed = connectStartedAt?.let { System.currentTimeMillis() - it } ?: -1
            Log.d(TAG, "onConnectionSuccess endpoint=$activeEndpoint (handshake+connect took ${elapsed}ms)")
            failuresOnCurrent = 0
            notifyEndpoint(connected = true)
            recordingStateListener?.onStreamConnected()
            // Preserve elapsed time across a reconnect/failover (only start fresh after a full
            // disconnect, which nulls recordingStartTime); cancel any prior timer so they can't stack.
            if (recordingStartTime == null) {
                recordingStartTime = System.currentTimeMillis()
            }
            timerJob?.cancel()
            startTimer()
            // RTMP is connected — the Bunny live stream is now in PREVIEW. Mark it as started
            // (RUNNING) so viewers can actually watch; mirrors the dashboard's "Go live" button.
            val live = activeLiveStream
            if (live != null && !liveStartRequested) {
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
        }

        override fun onDisconnect() {
            Log.d(TAG, "ConnectChecker onDisconnect")
            timerJob?.cancel()
            recordingStateListener?.onStreamDisconnected()
            recordingStartTime = null
        }

        override fun onNewBitrate(bitrate: Long) {
            Log.d(TAG, "ConnectChecker onNewBitrate: $bitrate")
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!genericStream.isOnPreview) {
                genericStream.startPreview(openGlView)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            genericStream.getGlInterface().setPreviewResolution(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (genericStream.isOnPreview) genericStream.stopPreview()
        }
    }

    override fun initialize(container: ViewGroup, deviceCamera: DeviceCamera) {
        genericStream = GenericStream(container.context, connectChecker)
        genericStream.getStreamClient().setSocketType(SocketType.KTOR)
        genericStream.getStreamClient().setLogs(true)
        genericStream.getStreamClient().setWriteChunkSize(4096)
        // Headroom for quick same-host retries + primary↔backup failover switches.
        genericStream.getStreamClient().setReTries(12)
        genericStream.getStreamClient().setCheckServerAlive(true)


        val prepared = try {
            genericStream.prepareVideo(width, height, vBitrate) &&
                    genericStream.prepareAudio(sampleRate, isStereo, aBitrate)
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
        genericStream.getGlInterface().autoHandleOrientation = true
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
                    if (!genericStream.isStreaming) {
                        val ingest = result.value
                        primaryUrl = ingest.primaryUrl
                        backupUrl = ingest.backupUrl
                        activeEndpoint = IngestEndpoint.PRIMARY
                        failuresOnCurrent = 0
                        connectFailures = 0
                        streamUrl = ingest.primaryUrl
                        Log.d(
                            TAG,
                            "startStream url=${ingest.primaryUrl.redactKey()} hasBackup=${ingest.backupUrl != null} " +
                                    "video=${width}x$height@${fps} vBitrate=$vBitrate " +
                                    "audio=${sampleRate}Hz aBitrate=$aBitrate"
                        )
                        genericStream.startStream(ingest.primaryUrl)
                    } else {
                        Log.w(TAG, "startStream skipped — already streaming")
                    }
                }
            }
        }
    }

    override fun stopStreaming() {
        genericStream.stopStream()
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
        return genericStream.isStreaming
    }

    override fun selectCamera(deviceCamera: DeviceCamera) {
        Log.d(TAG, "selectCamera: $deviceCamera")
        val camera2Source = genericStream.videoSource as Camera2Source
        val cameraFacing = camera2Source.getCameraFacing()

        if (deviceCamera == DeviceCamera.BACK && cameraFacing == Facing.FRONT) {
            switchCamera()
        } else if (deviceCamera == DeviceCamera.FRONT && cameraFacing == Facing.BACK) {
            switchCamera()
        }
    }

    override fun switchCamera() {
        val camera2Source = genericStream.videoSource as Camera2Source
        camera2Source.switchCamera()

        when (camera2Source.getCameraFacing()) {
            Facing.FRONT -> recordingStateListener?.onCameraChanged(DeviceCamera.FRONT)
            Facing.BACK -> recordingStateListener?.onCameraChanged(DeviceCamera.BACK)
            else -> { /* no-op */
            }
        }
    }

    override fun isMuted(): Boolean {
        return (genericStream.audioSource as MicrophoneSource).isMuted()
    }

    override fun setMuted(muted: Boolean) {
        val microphoneSource = genericStream.audioSource as MicrophoneSource
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