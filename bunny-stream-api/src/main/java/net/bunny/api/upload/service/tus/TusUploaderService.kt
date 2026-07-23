package net.bunny.api.upload.service.tus

import android.content.SharedPreferences
import android.util.Log
import io.tus.android.client.TusPreferencesURLStore
import io.tus.java.client.TusClient
import io.tus.java.client.TusUpload
import io.tus.java.client.TusUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.bunny.api.BuildConfig
import net.bunny.api.error.BunnyErrorMapper
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.PauseState
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.upload.service.UploadControl
import net.bunny.api.upload.service.UploadService
import java.net.URL
import java.security.MessageDigest

/**
 * Sends the file in chunks over the TUS resumable protocol.
 *
 * Chunking is what makes pause and resume possible: the transfer stops at a chunk boundary and the
 * server remembers the offset, so a held — or interrupted — upload picks up where it left off
 * instead of starting over. This is the path to use for large files and unreliable networks.
 */
internal class TusUploaderService(
    private val preferences: SharedPreferences,
    private val chunkSize: Int,
    private val accessKey: String,
    private val dispatcher: CoroutineDispatcher,
) : UploadService {

    private companion object {
        private const val TAG = "TusUploaderService"

        /** How long to sleep between checks while an upload is held. */
        private const val PAUSE_POLL_MILLIS = 250L

        /** `uploadChunk()` returns this once there is nothing left to send. */
        private const val NO_MORE_CHUNKS = -1

        private const val PERCENT = 100
        private const val SIGNATURE_VALIDITY_SECONDS = 3600L
    }

    /**
     * The point of the chunked path: the server remembers the offset, and [buildUpload] files it
     * under a key derived from the video's identity, so a later attempt on the same video finds it.
     */
    override val supportsResuming: Boolean = true

    override fun upload(
        libraryId: Long,
        videoId: String,
        fileInfo: FileInfo,
        control: UploadControl,
    ): Flow<UploadEvent> = flow {
        val upload = buildUpload(libraryId, videoId, fileInfo)

        val uploader = try {
            createUploader(libraryId, videoId, upload)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "could not start upload: ${e.message}")
            emit(UploadEvent.Failed(BunnyErrorMapper.map(e), videoId))
            return@flow
        }

        var lastProgress: UploadEvent.Progress? = null

        try {
            var chunkNumber = 0
            while (chunkNumber > NO_MORE_CHUNKS) {
                if (control.isCancelled) {
                    Log.d(TAG, "upload cancelled by caller")
                    releaseQuietly(uploader)
                    emit(UploadEvent.Cancelled(videoId))
                    return@flow
                }

                val progress = UploadEvent.Progress(
                    percentage = percentageOf(uploader.offset, upload.size),
                    videoId = videoId,
                    pauseState = if (control.isPaused) PauseState.Paused else PauseState.Uploading,
                )
                // Dedupe on the whole event, not just the percentage: a pause that happens between
                // two chunks changes the state without moving the number, and the UI needs it.
                if (progress != lastProgress) {
                    lastProgress = progress
                    emit(progress)
                }

                if (control.isPaused) {
                    delay(PAUSE_POLL_MILLIS)
                } else {
                    chunkNumber = uploader.uploadChunk()
                }
            }

            uploader.finish()
            Log.d(TAG, "upload done")
            emit(UploadEvent.Completed(videoId))
        } catch (e: CancellationException) {
            // The collector went away. Release the connection, but do not turn it into an event:
            // nobody is listening, and reporting a cancelled collector as a failed upload would be
            // a lie.
            releaseQuietly(uploader)
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "error uploading: ${e.message}")
            releaseQuietly(uploader)
            emit(UploadEvent.Failed(BunnyErrorMapper.map(e), videoId))
        }
    }.flowOn(dispatcher)

    private fun buildUpload(libraryId: Long, videoId: String, fileInfo: FileInfo): TusUpload =
        TusUpload().apply {
            size = fileInfo.size
            inputStream = fileInfo.inputStream
            metadata = mapOf(
                "filetype" to "video/*",
                "title" to videoId,
            )
            // The fingerprint is the key the URL store files this upload's offset under, so it has
            // to be derived from the upload's identity. It used to be a fresh random UUID, which
            // meant every attempt looked like a brand-new upload and resuming could never find the
            // stored offset — the resumable path was resumable in name only.
            fingerprint = "$libraryId-$videoId"
        }

    private fun createUploader(libraryId: Long, videoId: String, upload: TusUpload): TusUploader {
        val client = TusClient().apply {
            enableResuming(TusPreferencesURLStore(preferences))
            uploadCreationURL = URL(BuildConfig.TUS_UPLOAD_ENDPOINT)
            headers = signedHeaders(libraryId, videoId)
        }
        return client.resumeOrCreateUpload(upload).apply {
            chunkSize = this@TusUploaderService.chunkSize
        }
    }

    private fun signedHeaders(libraryId: Long, videoId: String): Map<String, String> {
        val expire = System.currentTimeMillis() / MILLIS_PER_SECOND + SIGNATURE_VALIDITY_SECONDS
        return mapOf(
            "AuthorizationSignature" to sha256("$libraryId$accessKey$expire$videoId"),
            "AuthorizationExpire" to expire.toString(),
            "LibraryId" to libraryId.toString(),
            "VideoId" to videoId,
            "User-Agent" to BuildConfig.USER_AGENT,
        )
    }

    private fun percentageOf(bytesUploaded: Long, total: Long): Int =
        if (total <= 0L) 0 else ((bytesUploaded.toDouble() / total) * PERCENT).toInt().coerceIn(0, PERCENT)

    /** Closes the connection and stream. Failing to release is not worth surfacing to the caller. */
    @Suppress("TooGenericExceptionCaught")
    private fun releaseQuietly(uploader: TusUploader) {
        try {
            uploader.finish()
        } catch (e: Exception) {
            Log.w(TAG, "could not release uploader: ${e.message}")
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private const val MILLIS_PER_SECOND = 1000L
