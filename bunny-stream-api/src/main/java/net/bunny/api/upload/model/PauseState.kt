package net.bunny.api.upload.model

/**
 * Whether an upload in flight is currently transferring, held, or cannot be held at all.
 *
 * Reported on every [UploadEvent.Progress] so a UI can render the pause control without tracking
 * the state itself. Pausing is a property of the transfer protocol, not of the SDK: only the
 * resumable TUS path can hold a transfer and pick it up later, so uploads that went through the
 * plain path always report [Unsupported].
 */
public sealed class PauseState {

    /**
     * This upload cannot be paused. Reported by the plain (non-resumable) upload path, where the
     * body is streamed in a single request and holding it would only stall the connection.
     * [net.bunny.api.upload.VideoUploader.pauseUpload] is a no-op for such uploads.
     */
    public object Unsupported : PauseState()

    /** The transfer is held and will not progress until resumed. */
    public object Paused : PauseState()

    /** The transfer is running. */
    public object Uploading : PauseState()
}
