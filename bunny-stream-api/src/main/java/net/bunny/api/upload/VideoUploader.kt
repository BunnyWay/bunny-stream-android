package net.bunny.api.upload

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import net.bunny.api.upload.model.UploadEvent

/**
 * Uploads video files to a Bunny library.
 *
 * Reach an instance through [net.bunny.api.BunnyStreamApi]: `videoUploader` sends the file in one
 * request, `tusVideoUploader` sends it in resumable chunks and is the one to use for large files,
 * unreliable networks, or any UI offering a pause button.
 *
 * ## An upload is a thing, not a call
 *
 * [startUpload] begins a transfer and hands back an **upload id**. Everything else — watching it,
 * pausing it, cancelling it, continuing it after a failure — is addressed by that id. The transfer
 * runs inside the SDK and does not belong to whoever started it, so closing the screen that began
 * an upload does not stop it.
 *
 * That makes the id the one thing a caller must not lose. Keep it somewhere that outlives the
 * screen — a repository, a saved-state store, an app-scoped holder — and re-attach with
 * [observeUpload] when the screen comes back:
 *
 * ```kotlin
 * // starting
 * val uploadId = uploader.startUpload(libraryId, uri)
 * store.activeUpload = uploadId
 * observe(uploadId)
 *
 * // returning to a screen while it is still running
 * store.activeUpload?.let(::observe)
 *
 * private fun observe(id: String) = viewModelScope.launch {
 *     uploader.observeUpload(id)?.collect { event -> render(event) }
 * }
 * ```
 *
 * Uploads survive navigation, not the process. If the app is killed the transfer stops; use
 * [continueUpload] to pick it up again on the resumable path, or a foreground service /
 * `WorkManager` if it must keep running while the app is away.
 */
public interface VideoUploader {

    /**
     * Creates a video record and starts uploading [videoUri] into it.
     *
     * Returns immediately with the id used to address this upload; the transfer proceeds in the
     * background. Progress and the outcome arrive through [observeUpload] — an upload nobody
     * observes still runs to completion.
     *
     * @param libraryId library the video is created in.
     * @param videoUri content URI of the file; the SDK reads its name and size and closes the
     *   stream when the transfer ends.
     * @return the upload id. Keep it: it is the only handle to this upload.
     */
    public fun startUpload(libraryId: Long, videoUri: Uri): String

    /**
     * Picks up an interrupted upload for a video that already exists, instead of creating a new one.
     *
     * Use it after [UploadEvent.Failed] with a non-terminal error: pass the `videoId` from
     * [UploadEvent.Started] together with the same [videoUri], and the transfer continues from the
     * offset the server already has rather than re-sending what arrived before.
     *
     * Only the resumable (TUS) uploader can do this. On the plain uploader the returned upload
     * fails immediately with [net.bunny.api.error.BunnyError.InvalidState] rather than silently
     * re-sending the whole file.
     *
     * @return a new upload id for this attempt; the previous one is finished and no longer valid.
     */
    public fun continueUpload(libraryId: Long, videoId: String, videoUri: Uri): String

    /**
     * Events for the upload identified by [uploadId], or `null` if no such upload is known —
     * either it never existed, or it finished long enough ago to have been forgotten.
     *
     * The returned flow is cold and safe to collect from several places at once; each collector
     * receives the most recent event immediately, then everything that follows, and the flow
     * **completes on the terminal event** ([UploadEvent.Completed], [UploadEvent.Cancelled] or
     * [UploadEvent.Failed]). Collecting does not start, stop or affect the transfer, and
     * abandoning a collector leaves it running — use [cancelUpload] to actually stop it.
     *
     * A collector joins at "now", not at the beginning: it starts from the most recent event, so
     * one attaching to an upload already in flight will not see [UploadEvent.Started]. That is
     * deliberate — a returning screen wants the current state, not a replay of a transfer it
     * missed. Every event carries the `videoId`, so read it from whichever arrives first rather
     * than only from [UploadEvent.Started]. Under load intermediate [UploadEvent.Progress] values
     * may be coalesced; the terminal event is always delivered.
     */
    public fun observeUpload(uploadId: String): Flow<UploadEvent>?

    /**
     * Holds the upload at the next chunk boundary.
     *
     * Only resumable (TUS) uploads can be held; on the plain path this is a no-op, which is what
     * [net.bunny.api.upload.model.PauseState.Unsupported] on [UploadEvent.Progress] tells a UI
     * before it offers the control. Unknown or finished upload ids are ignored.
     */
    public fun pauseUpload(uploadId: String)

    /** Releases a hold set by [pauseUpload]. Ignored for uploads that are not paused. */
    public fun resumeUpload(uploadId: String)

    /**
     * Stops the upload and deletes the partially uploaded video from the library.
     *
     * The upload's flow emits [UploadEvent.Cancelled] and completes. Deletion happens in the
     * background and its outcome is not reported: the upload is over either way. Unknown upload
     * ids are ignored.
     *
     * This is the difference between "stop watching" and "stop": abandoning an [observeUpload]
     * collector does neither.
     */
    public fun cancelUpload(uploadId: String)
}
