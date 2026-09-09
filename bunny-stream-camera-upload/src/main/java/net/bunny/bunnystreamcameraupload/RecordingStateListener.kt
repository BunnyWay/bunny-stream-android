package net.bunny.bunnystreamcameraupload

interface RecordingStateListener {
    /**
     * Called stream is being initialized
     */
    fun onStreamInitializing()

    /**
     * Called when stream is connected to server, effectively making the stream live
     */
    fun onStreamConnected()

    /**
     * Called when stream is stopped
     */
    fun onStreamStopped()

    /**
     * Called when stream is disconnected
     */
    fun onStreamDisconnected()

    /**
     * Called when stream authentication fails
     */
    fun onStreamAuthError()

    /**
     * Called when stream connection fails
     * @param message reason for connection failure
     */
    fun onStreamConnectionFailed(message: String)

    /**
     * Called when camera changes
     * @param deviceCamera now active camera
     * @see DeviceCamera
     */
    fun onCameraChanged(deviceCamera: DeviceCamera)

    /**
     * Called when audio mute status changes
     * @param muted true if audio is now muted
     */
    fun onAudioMuted(muted: Boolean)

    /**
     * Called while broadcasting to a live stream when a RTMP ingest endpoint's connection state
     * changes. Fired per endpoint, so with dual-publish both [IngestEndpoint.PRIMARY] and
     * [IngestEndpoint.BACKUP] can independently be [IngestEndpointState.LIVE]. Not fired for VOD
     * recording. Default no-op for backward compatibility.
     *
     * @see IngestEndpoint
     * @see IngestEndpointState
     */
    fun onIngestEndpointChanged(endpoint: IngestEndpoint, state: IngestEndpointState) {}
}