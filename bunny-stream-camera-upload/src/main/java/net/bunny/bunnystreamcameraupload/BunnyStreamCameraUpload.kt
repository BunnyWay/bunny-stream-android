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
import net.bunny.bunnystreamcameraupload.data.DefaultRecordingRepository
import net.bunny.bunnystreamcameraupload.domain.DefaultStreamHandler
import net.bunny.bunnystreamcameraupload.domain.RecordingRepository
import net.bunny.bunnystreamcameraupload.domain.StreamHandler
import net.bunny.recording.R
import net.bunny.recording.databinding.RecordingViewBinding

class BunnyStreamCameraUpload @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), StreamCameraUploadView {

    companion object {
        private const val TAG = "BunnyStreamCameraUpload"
    }

    private val binding = RecordingViewBinding.inflate(LayoutInflater.from(context), this)

    private val streamRepository: RecordingRepository = DefaultRecordingRepository(Dispatchers.IO)
    private val streamHandler: StreamHandler = DefaultStreamHandler(
        streamRepository = streamRepository,
        coroutineDispatcher = Dispatchers.IO
    )

    private val libraryId = BunnyStreamApi.libraryId

    override var hideDefaultControls: Boolean = false
        set(value) {
            field = value
            binding.streamControls.isVisible = !value
        }

    override var liveStreamId: String? = null

    override var liveIngestEndpoint: String? = null

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

            override fun onIngestEndpointChanged(endpoint: IngestEndpoint, connected: Boolean) {
                updateIngestBadges(endpoint, connected)
                streamStateListener?.onIngestEndpointChanged(endpoint, connected)
            }
        }

        binding.switchCamera.setOnClickListener {
            streamHandler.switchCamera()
        }

        binding.startStop.setOnClickListener {
            if (!streamHandler.isStreaming()) {
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
        // Live broadcast: reveal the Primary/Backup badges (default: connecting to primary).
        if (liveStreamId != null) {
            binding.ingestBadges.isVisible = true
            updateIngestBadges(IngestEndpoint.PRIMARY, connected = false)
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
     * Tints the Primary/Backup dots: the [active] endpoint is green when [connected] / amber while
     * (re)connecting, the other endpoint is grey (standby). Mirrors the web / iOS ingest badges.
     */
    private fun updateIngestBadges(active: IngestEndpoint, connected: Boolean) {
        val activeColor = if (connected) R.color.ingest_active else R.color.ingest_connecting
        fun tint(colorRes: Int) = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
        binding.primaryDot.imageTintList =
            tint(if (active == IngestEndpoint.PRIMARY) activeColor else R.color.ingest_standby)
        binding.backupDot.imageTintList =
            tint(if (active == IngestEndpoint.BACKUP) activeColor else R.color.ingest_standby)
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