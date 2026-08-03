package net.bunny.api.upload.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Out-of-band control for an upload that is already in flight.
 *
 * The transfer runs on the SDK's own scope, so pause, resume and cancel cannot be ordinary calls
 * into the running code — they arrive from a different coroutine (a UI button, typically) and have
 * to be observed cooperatively at a point where stopping is safe. This class is that channel.
 *
 * [net.bunny.api.upload.DefaultVideoUploader] keeps one instance per in-flight upload, keyed by
 * upload id, and hands it to the [UploadService] that does the transfer.
 *
 * Cancellation is one-way and latching: once [cancel] is called the upload never resumes, and
 * [awaitCancellation] stays completed for any later observer. Pausing is a plain flag because
 * only the resumable path can act on it at all.
 */
internal class UploadControl {

    private val pausedState = MutableStateFlow(false)
    private val cancellation = CompletableDeferred<Unit>()

    /** `true` while the transfer should hold. Only the TUS path observes this. */
    val isPaused: Boolean get() = pausedState.value

    /** `true` once [cancel] has been called. Never returns to `false`. */
    val isCancelled: Boolean get() = cancellation.isCompleted

    /** Holds the transfer at the next chunk boundary. No-op once cancelled. */
    fun pause() {
        if (!isCancelled) pausedState.value = true
    }

    /** Releases a hold set by [pause]. No-op once cancelled. */
    fun resume() {
        if (!isCancelled) pausedState.value = false
    }

    /**
     * Stops the transfer for good. Clears any pause first, so a paused upload does not sit in its
     * delay loop instead of noticing it was cancelled.
     */
    fun cancel() {
        pausedState.value = false
        cancellation.complete(Unit)
    }

    /**
     * Suspends until [cancel] is called. Used by the non-resumable path, which has no chunk
     * boundary to poll and has to interrupt a single long request instead.
     */
    suspend fun awaitCancellation() {
        cancellation.await()
    }
}
