package net.bunny.api.upload.service

import kotlinx.coroutines.flow.Flow
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.UploadEvent

/**
 * Transfers the bytes of one already-created video to Bunny.
 *
 * Two implementations exist: the plain single-request path
 * ([net.bunny.api.upload.service.basic.BasicUploaderService]) and the resumable TUS path
 * ([net.bunny.api.upload.service.tus.TusUploaderService]). They differ in whether an upload can
 * be held and picked up again; everything else about the contract is identical.
 *
 * Internal on purpose: integrators reach uploads through
 * [net.bunny.api.upload.VideoUploader], which owns video creation, upload ids and the registry of
 * in-flight transfers. This interface only moves bytes.
 */
internal interface UploadService {

    /**
     * Whether a transfer interrupted partway can be picked up where it stopped.
     *
     * `true` only for the chunked TUS path, which records an offset the server agrees on. The
     * plain path streams one request and has nothing to resume from, so
     * [net.bunny.api.upload.VideoUploader.continueUpload] refuses rather than silently restarting
     * from zero — re-sending an entire file when the caller asked to continue one is the kind of
     * surprise that shows up as a data bill.
     */
    val supportsResuming: Boolean

    /**
     * Returns a cold [Flow] that performs the transfer when collected.
     *
     * The returned stream emits zero or more [UploadEvent.Progress] and then exactly one terminal
     * event — [UploadEvent.Completed], [UploadEvent.Cancelled] or [UploadEvent.Failed] — after
     * which it completes normally. It never emits [UploadEvent.Started]; the caller owns upload
     * ids and emits that itself.
     *
     * Implementations must:
     *  * run the transfer in the collector's coroutine, so cancelling collection cancels the
     *    upload and no work outlives its consumer;
     *  * rethrow [kotlinx.coroutines.CancellationException] rather than reporting it as a failure —
     *    a cancelled collector is not a failed upload;
     *  * emit [UploadEvent.Cancelled] only for a cancellation requested through [control];
     *  * map every other failure through [net.bunny.api.error.BunnyErrorMapper] into
     *    [UploadEvent.Failed] instead of throwing.
     *
     * @param libraryId target library.
     * @param videoId id of the video record the bytes belong to; it must already exist.
     * @param fileInfo name, size and an open stream over the content to send.
     * @param control cooperative pause/resume/cancel channel for this upload.
     */
    fun upload(
        libraryId: Long,
        videoId: String,
        fileInfo: FileInfo,
        control: UploadControl,
    ): Flow<UploadEvent>
}
