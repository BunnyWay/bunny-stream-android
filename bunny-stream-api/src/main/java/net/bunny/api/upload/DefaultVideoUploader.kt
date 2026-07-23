package net.bunny.api.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.bunnyCatching
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.upload.service.UploadControl
import net.bunny.api.upload.service.UploadService
import org.openapitools.client.models.VideoCreateVideoRequest
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Owns uploads as long-lived, addressable things: their coroutine scope, their registry, the
 * event stream each one broadcasts, and the video-record bookkeeping around the transfer itself.
 * The bytes go through an [UploadService] — plain or resumable — chosen at construction.
 *
 * Uploads run on [uploadScope], not on the caller's coroutine, so a screen that starts one and
 * then goes away does not take it down. That scope belongs to the owning
 * [net.bunny.api.BunnyStreamApi] instance and is torn down with it via [shutdown]; nothing here
 * outlives the SDK instance that created it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultVideoUploader(
    private val context: Context,
    private val videoUploadService: UploadService,
    private val ioDispatcher: CoroutineDispatcher,
    private val videosApi: ManageVideosApi,
) : VideoUploader {

    private companion object {
        private const val TAG = "DefaultVideoUploader"

        /** Progress events buffered per upload before the oldest are coalesced away. */
        private const val EVENT_BUFFER = 64

        /**
         * How many uploads stay addressable. Finished ones are kept so a screen returning just
         * after a transfer ended can still read its outcome instead of getting `null`; beyond this
         * many, the oldest finished entries are forgotten.
         */
        private const val MAX_REMEMBERED = 32
    }

    private val uploadScope = CoroutineScope(
        ioDispatcher + SupervisorJob() + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "upload coroutine failed: $error")
        },
    )

    private val lock = Any()

    /** Insertion-ordered so eviction can drop the oldest finished uploads first. */
    private val uploads = LinkedHashMap<String, Upload>()

    private class Upload(
        val libraryId: Long,
        val control: UploadControl,
        val events: MutableSharedFlow<UploadEvent>,
    ) {
        @Volatile
        var videoId: String? = null

        @Volatile
        var finished: Boolean = false

        suspend fun finishWith(event: UploadEvent) {
            events.emit(event)
            finished = true
        }
    }

    override fun startUpload(libraryId: Long, videoUri: Uri): String =
        launchUpload(libraryId, videoUri, existingVideoId = null)

    override fun continueUpload(libraryId: Long, videoId: String, videoUri: Uri): String =
        launchUpload(libraryId, videoUri, existingVideoId = videoId)

    private fun launchUpload(libraryId: Long, videoUri: Uri, existingVideoId: String?): String {
        val uploadId = UUID.randomUUID().toString()
        val upload = Upload(
            libraryId = libraryId,
            control = UploadControl(),
            events = MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = EVENT_BUFFER,
                // A slow observer must never slow the transfer down, and losing an intermediate
                // percentage costs nothing. The terminal event is always the most recent value,
                // so replay keeps it reachable even when older ones were coalesced away.
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            ),
        ).apply { videoId = existingVideoId }

        synchronized(lock) {
            evictFinished()
            uploads[uploadId] = upload
        }

        uploadScope.launch { runUpload(uploadId, upload, videoUri, existingVideoId) }
        return uploadId
    }

    @Suppress("ReturnCount")
    private suspend fun runUpload(
        uploadId: String,
        upload: Upload,
        videoUri: Uri,
        existingVideoId: String?,
    ) {
        if (existingVideoId != null && !videoUploadService.supportsResuming) {
            upload.finishWith(
                UploadEvent.Failed(
                    BunnyError.InvalidState(
                        "This uploader cannot continue an interrupted upload. Use the resumable " +
                            "(TUS) uploader, or start a new upload instead.",
                    ),
                    videoId = existingVideoId,
                ),
            )
            return
        }

        val fileInfo = when (val opened = openFile(videoUri)) {
            is BunnyResult.Err -> {
                upload.finishWith(UploadEvent.Failed(opened.error, existingVideoId))
                return
            }
            is BunnyResult.Ok -> opened.value
        }

        try {
            val videoId = existingVideoId
                ?: when (val created = createVideo(upload.libraryId, fileInfo.fileName)) {
                    is BunnyResult.Err -> {
                        upload.finishWith(UploadEvent.Failed(created.error, videoId = null))
                        return
                    }
                    is BunnyResult.Ok -> created.value
                }
            upload.videoId = videoId

            // Cancelling during video creation is a real race: the record now exists but nobody
            // asked for it. Clean it up rather than leaving an empty video in the library.
            if (upload.control.isCancelled) {
                uploadScope.launch { deleteVideo(upload.libraryId, videoId) }
                upload.finishWith(UploadEvent.Cancelled(videoId))
                return
            }

            upload.events.emit(UploadEvent.Started(uploadId, videoId))
            videoUploadService
                .upload(upload.libraryId, videoId, fileInfo, upload.control)
                .collect { event -> upload.events.emit(event) }
        } finally {
            fileInfo.inputStream.closeQuietly()
            upload.finished = true
        }
    }

    override fun observeUpload(uploadId: String): Flow<UploadEvent>? {
        val upload = synchronized(lock) { uploads[uploadId] } ?: return null
        return upload.events.transformWhile { event ->
            emit(event)
            !event.isTerminal
        }
    }

    override fun pauseUpload(uploadId: String) {
        find(uploadId, "pause")?.control?.pause()
    }

    override fun resumeUpload(uploadId: String) {
        find(uploadId, "resume")?.control?.resume()
    }

    override fun cancelUpload(uploadId: String) {
        val upload = find(uploadId, "cancel") ?: return
        upload.control.cancel()
        // Null while the video record is still being created; the race is handled in runUpload,
        // which deletes it as soon as it exists.
        val videoId = upload.videoId ?: return
        uploadScope.launch { deleteVideo(upload.libraryId, videoId) }
    }

    /**
     * Stops every upload and tears down the scope. Called when the owning
     * [net.bunny.api.BunnyStreamApi] instance is replaced or released, so uploads can never
     * outlive the SDK instance that started them.
     */
    fun shutdown() {
        synchronized(lock) {
            uploads.values.forEach { it.control.cancel() }
            uploads.clear()
        }
        uploadScope.cancel()
    }

    private fun find(uploadId: String, action: String): Upload? {
        val upload = synchronized(lock) { uploads[uploadId] }
        if (upload == null || upload.finished) {
            Log.w(TAG, "cannot $action, upload id $uploadId is not in flight")
            return null
        }
        return upload
    }

    /** Drops the oldest finished uploads once the registry grows past [MAX_REMEMBERED]. */
    private fun evictFinished() {
        if (uploads.size < MAX_REMEMBERED) return
        val entries = uploads.entries.iterator()
        while (entries.hasNext() && uploads.size >= MAX_REMEMBERED) {
            if (entries.next().value.finished) entries.remove()
        }
    }

    /**
     * Creates the video record the bytes will be attached to.
     *
     * A `2xx` with no guid is treated as [BunnyError.Decode]: the call succeeded but the response
     * did not carry what the contract promises, which is exactly what that variant is for.
     */
    private suspend fun createVideo(libraryId: Long, title: String): BunnyResult<String> {
        val created = bunnyCatching {
            videosApi.videoCreateVideo(
                libraryId = libraryId,
                videoCreateVideoRequest = VideoCreateVideoRequest(title = title),
            ).guid
        }

        return when (created) {
            is BunnyResult.Err -> created
            is BunnyResult.Ok -> created.value
                ?.takeIf { it.isNotEmpty() }
                ?.let { BunnyResult.Ok(it) }
                ?: BunnyResult.Err(
                    BunnyError.Decode("Video was created but the response carried no video id"),
                )
        }
    }

    private suspend fun deleteVideo(libraryId: Long, videoId: String) {
        when (val result = bunnyCatching { videosApi.videoDeleteVideo(libraryId, videoId) }) {
            is BunnyResult.Err ->
                Log.w(TAG, "could not delete cancelled video $videoId: ${result.message}")
            is BunnyResult.Ok ->
                Log.d(TAG, "deleted cancelled video $videoId")
        }
    }

    /**
     * Resolves name, size and a readable stream for [uri].
     *
     * Metadata is read before the stream is opened: the other order leaks a file handle whenever
     * the content resolver has the file but no metadata for it.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun openFile(uri: Uri): BunnyResult<FileInfo> {
        var stream: InputStream? = null
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
                ?: return unreadable("no metadata available for $uri")

            val fileName: String
            val fileSize: Long
            cursor.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (!it.moveToFirst() || nameIndex < 0 || sizeIndex < 0) {
                    return unreadable("incomplete metadata for $uri")
                }
                fileName = it.getString(nameIndex)
                fileSize = it.getLong(sizeIndex)
            }

            stream = context.contentResolver.openInputStream(uri)
                ?: return unreadable("cannot open $uri for reading")

            BunnyResult.Ok(FileInfo(fileName, fileSize, stream))
        } catch (e: Exception) {
            stream.closeQuietly()
            BunnyResult.Err(
                BunnyError.LocalFile(
                    message = "Cannot read the selected file: ${e.message ?: e::class.simpleName}",
                    cause = e,
                ),
            )
        }
    }

    private fun unreadable(detail: String): BunnyResult<FileInfo> =
        BunnyResult.Err(BunnyError.LocalFile("Cannot read the selected file: $detail"))
}

/** Whether this event ends the upload, so an observer's flow can complete on it. */
private val UploadEvent.isTerminal: Boolean
    get() = this is UploadEvent.Completed ||
        this is UploadEvent.Cancelled ||
        this is UploadEvent.Failed

private fun Closeable?.closeQuietly() {
    try {
        this?.close()
    } catch (e: IOException) {
        Log.w("DefaultVideoUploader", "could not close file stream: ${e.message}")
    }
}
