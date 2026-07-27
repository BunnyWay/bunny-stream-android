package net.bunny.api.upload.model

import net.bunny.api.error.BunnyError

/**
 * One observation about an upload in flight, emitted on the [kotlinx.coroutines.flow.Flow]
 * returned by [net.bunny.api.upload.VideoUploader.observeUpload].
 *
 * The stream is ordered and finite. It opens with one [Started] — carrying the `videoId` that
 * [continueUpload][net.bunny.api.upload.VideoUploader.continueUpload] needs to pick this upload up
 * after a failure — then emits zero or more [Progress], then closes with exactly one terminal
 * event: [Completed], [Cancelled] or [Failed].
 *
 * Two things can shorten that. An upload stopped before the transfer began — an unreadable file,
 * a video record that could not be created, or a cancel arriving while it was being created —
 * emits its terminal event alone, with no [Started]. And a collector attaching to an upload
 * already in flight joins at the most recent event, so it will not see the [Started] that came
 * before it. Read the `videoId` off whichever event arrives first rather than off [Started] only.
 *
 * After the terminal event the flow completes. Failures arrive as a [Failed] value rather than a
 * thrown exception, so a `collect` cannot miss one by forgetting a `catch`:
 *
 * ```kotlin
 * uploader.observeUpload(uploadId)?.collect { event ->
 *     when (event) {
 *         is UploadEvent.Started   -> rememberVideoId(event.videoId)
 *         is UploadEvent.Progress  -> showProgress(event.percentage, event.pauseState)
 *         is UploadEvent.Completed -> showDone(event.videoId)
 *         is UploadEvent.Cancelled -> dismiss()
 *         is UploadEvent.Failed    -> showError(event.error.message)
 *     }
 * }
 * ```
 *
 * Observing is separate from running: abandoning a collector leaves the transfer going, and a new
 * collector can attach at any time and will see the most recent event first. To actually stop an
 * upload, call [cancelUpload][net.bunny.api.upload.VideoUploader.cancelUpload].
 */
public sealed class UploadEvent {

    /**
     * The video record exists server-side and the transfer has begun. The first event of an upload
     * that got that far, and the one a late-attaching collector may miss.
     *
     * @property uploadId the id this upload is addressed by — the same value
     *   [startUpload][net.bunny.api.upload.VideoUploader.startUpload] returned. Repeated here so
     *   an observer that attached without it can still pause or cancel.
     * @property videoId id of the created video: usable for playback once [Completed] arrives, and
     *   the value to pass to [continueUpload][net.bunny.api.upload.VideoUploader.continueUpload]
     *   if the upload fails partway. Worth persisting alongside the upload id.
     */
    public data class Started(
        public val uploadId: String,
        public val videoId: String,
    ) : UploadEvent()

    /**
     * Transfer progress. Emitted only when something about it actually changes — the percentage,
     * or the [pauseState] (holding an upload changes the state without moving the number) — so a
     * UI can bind to it directly without throttling.
     *
     * @property percentage `0..100`.
     * @property pauseState whether the transfer is running, held, or cannot be held at all.
     */
    public data class Progress(
        public val percentage: Int,
        public val videoId: String,
        public val pauseState: PauseState,
    ) : UploadEvent()

    /** The whole file reached Bunny. Terminal. The video is queued for encoding. */
    public data class Completed(
        public val videoId: String,
    ) : UploadEvent()

    /**
     * The upload stopped because [net.bunny.api.upload.VideoUploader.cancelUpload] was called, or
     * because the SDK instance running it was released. Terminal.
     *
     * Deletion of the half-uploaded video is requested in the background and its outcome is not
     * reported — this event does not promise the record is already gone.
     */
    public data class Cancelled(
        public val videoId: String,
    ) : UploadEvent()

    /**
     * The upload failed. Terminal.
     *
     * @property error typed cause. [BunnyError.LocalFile] means the device could not read the
     *   file; [BunnyError.Auth] a bad access key; [BunnyError.Network] a transport failure that
     *   may be worth retrying — [BunnyError.isTerminal] answers that directly.
     * @property videoId id of the video record, when one had been created before the failure.
     *   `null` when the upload failed before that point.
     */
    public data class Failed(
        public val error: BunnyError,
        public val videoId: String?,
    ) : UploadEvent()
}
