package net.bunny.bunnystreamcameraupload

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import net.bunny.api.BunnyStreamApi
import net.bunny.api.StreamApi
import net.bunny.bunnystreamcameraupload.data.DefaultRecordingRepository
import net.bunny.bunnystreamcameraupload.domain.DefaultStreamHandler
import net.bunny.bunnystreamcameraupload.domain.RecordingRepository
import net.bunny.bunnystreamcameraupload.domain.StreamHandler
import net.bunny.recording.R
import net.bunny.recording.databinding.RecordingViewBinding

/**
 * Camera capture view of the Bunny Stream SDK. Add it to a layout:
 *
 * ```xml
 * <net.bunny.bunnystreamcameraupload.BunnyStreamCameraUpload
 *     android:id="@+id/cameraUpload"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" />
 * ```
 *
 * then request the `CAMERA` and `RECORD_AUDIO` runtime permissions and call [startPreview].
 * By default the view records the camera to a new video in your library. Set [liveStreamId]
 * before starting to broadcast to an existing live stream instead - the SDK resolves the ingest,
 * starts the stream on the server once connected, shows primary/backup badges and reconnects on
 * network drops. See [StreamCameraUploadView] for the full contract.
 *
 * `BunnyStreamApi.initialize(...)` must have been called before this view is created.
 *
 * XML attributes: `brvDefaultCamera` ("back" or "front") picks the starting camera;
 * `brvHideDefaultControls` hides the built-in controls, same as [hideDefaultControls].
 */
class BunnyStreamCameraUpload @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), StreamCameraUploadView {

    companion object {
        private const val TAG = "BunnyStreamCameraUpload"
    }

    private val binding = RecordingViewBinding.inflate(LayoutInflater.from(context), this)

    override var bunny: StreamApi? = null

    private val streamRepository: RecordingRepository = DefaultRecordingRepository(
        coroutineDispatcher = Dispatchers.IO,
        // Resolved per call, so assigning [bunny] after the view is built still takes effect.
        sdk = { bunny ?: BunnyStreamApi.getInstance() },
    )
    private val streamHandler: StreamHandler = DefaultStreamHandler(
        streamRepository = streamRepository,
        coroutineDispatcher = Dispatchers.IO
    )

    override var hideDefaultControls: Boolean = false
        set(value) {
            field = value
            binding.streamControls.isVisible = !value
        }

    override var liveStreamId: String? = null

    override var liveIngestEndpoint: String? = null

    override var dualPublish: Boolean
        get() = streamHandler.dualPublish
        set(value) {
            streamHandler.dualPublish = value
        }

    override var closeStreamClickListener: OnClickListener? = null
        set(value) {
            field = value
            binding.close.setOnClickListener(value)
        }

    override var streamStateListener: RecordingStateListener? = null

    override var streamDurationListener: RecordingDurationListener? = null

    private var defaultCamera = DeviceCamera.BACK

    init {
        extractAttrs(attrs)

        streamHandler.recordingStateListener = object : RecordingStateListener {
            override fun onStreamInitializing() {
                setPreparing()
                streamStateListener?.onStreamInitializing()
            }

            override fun onStreamConnected() {
                setRecording()
                streamStateListener?.onStreamConnected()
            }

            override fun onStreamStopped() {
                setNotRecording()
                streamStateListener?.onStreamStopped()
            }

            override fun onStreamDisconnected() {
                setNotRecording()
                streamStateListener?.onStreamDisconnected()
            }

            override fun onStreamAuthError() {
                setNotRecording()
                if (streamStateListener != null) {
                    streamStateListener?.onStreamAuthError()
                } else {
                    showStreamAuthErrorDialog()
                }
            }

            override fun onStreamConnectionFailed(message: String) {
                setNotRecording()
                if (streamStateListener != null) {
                    streamStateListener?.onStreamConnectionFailed(message)
                } else {
                    showStreamConnectionErrorDialog(message)
                }
            }

            override fun onCameraChanged(deviceCamera: DeviceCamera) {
                streamStateListener?.onCameraChanged(deviceCamera)
            }

            override fun onAudioMuted(muted: Boolean) {
                binding.mute.isActivated = muted
                streamStateListener?.onAudioMuted(muted)
            }

            override fun onIngestEndpointChanged(endpoint: IngestEndpoint, state: IngestEndpointState) {
                updateIngestBadge(endpoint, state)
                streamStateListener?.onIngestEndpointChanged(endpoint, state)
            }
        }

        binding.switchCamera.setOnClickListener {
            streamHandler.switchCamera()
        }

        binding.startStop.setOnClickListener {
            if (!streamHandler.isStreaming()) {
                // Resolved here rather than when the view is built. A view inflated from XML is
                // constructed with its layout, which can happen before the SDK is initialised;
                // reading the library id then used to freeze "no library yet" into the view for
                // its whole life, and every recording afterwards went nowhere without an error.
                if (bunny == null && !BunnyStreamApi.isInitialized()) {
                    Log.e(TAG, "Unable to start, call BunnyStreamApi.initialize(...) first")
                    return@setOnClickListener
                }
                val libraryId = (bunny ?: BunnyStreamApi.getInstance()).libraryId

                val streamId = liveStreamId
                if (streamId != null) {
                    streamHandler.startLiveStreaming(libraryId, streamId, liveIngestEndpoint)
                } else {
                    streamHandler.startStreaming(libraryId)
                }
            } else {
                AlertDialog.Builder(binding.root.context)
                    .setTitle(context.getString(R.string.dialog_end_stream_title))
                    .setMessage(context.getString(R.string.dialog_end_stream_text))
                    .setNegativeButton(context.getString(R.string.dialog_end_stream_negative), null)
                    .setPositiveButton(context.getString(R.string.dialog_end_stream_positive)) { _, _ ->
                        streamHandler.stopStreaming()
                    }
                    .show()
            }
        }

        binding.mute.setOnClickListener {
            streamHandler.setMuted(!streamHandler.isMuted())
        }

        binding.close.setOnClickListener(closeStreamClickListener)

        streamHandler.recordingDurationListener = object : RecordingDurationListener {
            override fun onDurationUpdated(durationMillis: Long, durationFormatted: String) {
                val recText = context.getString(statusLabelRes())
                binding.status.text = "$recText  •  $durationFormatted"
                streamDurationListener?.onDurationUpdated(durationMillis, durationFormatted)
            }
        }
    }

    override fun startPreview() {
        val cameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val micPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)

        val camGranted = cameraPermission == PackageManager.PERMISSION_GRANTED
        val micGranted = micPermission == PackageManager.PERMISSION_GRANTED

        if (camGranted && micGranted) {
            streamHandler.initialize(binding.surfaceViewContainer, defaultCamera)
        } else {
            Log.w(
                TAG, "Couldn't initialize preview, " +
                        "missing android.permission.CAMERA and " +
                        "android.permission.RECORD_AUDIO permissions"
            )
        }
    }

    override fun stopRecording() {
        streamHandler.stopStreaming()
    }

    override fun switchCamera() {
        streamHandler.switchCamera()
    }

    override fun setAudioMuted(muted: Boolean) {
        streamHandler.setMuted(muted)
    }

    override fun isRecording(): Boolean {
        return streamHandler.isStreaming()
    }

    private fun setRecording() {
        binding.startStop.isActivated = true
        binding.startStop.visibility = View.VISIBLE
        binding.progress.isVisible = false

        binding.status.isActivated = true
        binding.status.setText(statusLabelRes())

        binding.close.visibility = View.INVISIBLE
    }

    private fun setPreparing() {
        binding.startStop.visibility = View.INVISIBLE
        binding.progress.isVisible = true
        // Live broadcast: reveal the Primary/Backup badges; the handler drives their colours as
        // each ingest connects (both can be live at once in dual-publish mode).
        if (liveStreamId != null) {
            binding.ingestBadges.isVisible = true
            updateIngestBadge(IngestEndpoint.PRIMARY, IngestEndpointState.OFFLINE)
            updateIngestBadge(IngestEndpoint.BACKUP, IngestEndpointState.OFFLINE)
        }
    }

    private fun setNotRecording() {
        binding.startStop.isActivated = false
        binding.startStop.visibility = View.VISIBLE
        binding.progress.isVisible = false

        binding.status.isActivated = false
        binding.status.setText(R.string.rec_status_ready)

        binding.close.visibility = View.VISIBLE
        binding.ingestBadges.isVisible = false
    }

    /** "LIVE" while broadcasting to a live stream, otherwise "Recording" (VOD capture). */
    private fun statusLabelRes(): Int =
        if (liveStreamId != null) R.string.rec_status_live else R.string.rec_status_recording

    /**
     * Tints one ingest dot by its [state]: green = live, amber = connecting, grey = offline/standby.
     * Each endpoint is independent, so in dual-publish mode both dots can be green at once. Mirrors
     * the web / iOS ingest badges.
     */
    private fun updateIngestBadge(endpoint: IngestEndpoint, state: IngestEndpointState) {
        val colorRes = when (state) {
            IngestEndpointState.LIVE -> R.color.ingest_active
            IngestEndpointState.CONNECTING -> R.color.ingest_connecting
            IngestEndpointState.OFFLINE -> R.color.ingest_standby
        }
        val dot = if (endpoint == IngestEndpoint.PRIMARY) binding.primaryDot else binding.backupDot
        dot.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
    }

    private fun showStreamConnectionErrorDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.stream_connection_error))
            .setMessage(message)
            .setNeutralButton(R.string.dialog_ok, null)
            .show()
    }

    private fun showStreamAuthErrorDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.stream_auth_error))
            .setNeutralButton(R.string.dialog_ok, null)
            .show()
    }

    private fun extractAttrs(attrs: AttributeSet?) {
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.BunnyRecordingView).use {
                hideDefaultControls =
                    it.getBoolean(R.styleable.BunnyRecordingView_brvHideDefaultControls, false)
                val cam = it.getInt(R.styleable.BunnyRecordingView_brvDefaultCamera, 0)
                defaultCamera = if (cam == 0) {
                    DeviceCamera.BACK
                } else {
                    DeviceCamera.FRONT
                }
            }
        }
    }
}