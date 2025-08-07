package net.bunnystream.api.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.HttpStatusCodes
import net.bunny.api.upload.model.UploadError
import net.bunny.api.upload.service.PauseState
import net.bunny.api.upload.service.UploadListener
import net.bunny.api.upload.service.UploadRequest
import net.bunny.api.upload.service.UploadService
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException
import org.openapitools.client.models.VideoCreateVideoRequest
import java.util.UUID

class DefaultVideoUploader(
    private val context: Context,
    private val videoUploadService: UploadService,
    ioDispatcher: CoroutineDispatcher,
    private val videosApi: ManageVideosApi
) : VideoUploader {

    companion object {
        private const val TAG = "DefaultVideoUploader"
    }

    private val supervisorJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        exception.printStackTrace()
        Log.d(TAG, "CoroutineExceptionHandler: context=$context exception=$exception")
    }
    private val scope = CoroutineScope(ioDispatcher + exceptionHandler + supervisorJob)

    private val lock = Any()
    private val uploadsInProgress: MutableMap<String, UploadRequest> = mutableMapOf()

    override fun uploadVideo(libraryId: Long, videoUri: Uri, listener: UploadListener) {
        Log.d(TAG, "uploadVideo libraryId=$libraryId videoUri=$videoUri")

        val fileInfo = getFileInfo(videoUri)

        if (fileInfo == null) {
            listener.onUploadError(UploadError.ErrorReadingFile, null)
            return
        }

        Log.d(TAG, "fileInfo: $fileInfo")

        scope.launch {
            val videoId: String?
            try {
                videoId = createVideo(libraryId, fileInfo.fileName)
            } catch (e: Exception) {
                Log.e(TAG, "could not create video: ${e.message}")
                e.printStackTrace()

                val error = when (e) {
                    is ClientException -> {
                        when (e.statusCode) {
                            HttpStatusCodes.UNAUTHORIZED -> UploadError.Unauthorized
                            HttpStatusCodes.NOT_FOUND -> UploadError.VideoNotFound
                            else -> UploadError.UnknownError("${e.statusCode} ${e.message}")
                        }
                    }

                    is ServerException -> UploadError.ServerError
                    else -> UploadError.UnknownError(e.message ?: e.toString())
                }
                listener.onUploadError(error, null)
                return@launch
            }

            if (videoId.isNullOrEmpty()) {
                listener.onUploadError(UploadError.ErrorCreating, videoId)
                return@launch
            }

            val uploadId = UUID.randomUUID().toString()

            val request = videoUploadService.upload(
                libraryId, videoId, fileInfo, object : UploadListener {
                    override fun onProgressUpdated(
                        percentage: Int,
                        videoId: String,
                        pauseState: PauseState
                    ) {
                        Log.d(TAG, "onProgressUpdated: $percentage")
                        listener.onProgressUpdated(percentage, videoId, pauseState)
                    }

                    override fun onUploadDone(videoId: String) {
                        Log.d(TAG, "onUploadDone")
                        listener.onUploadDone(videoId)
                    }

                    override fun onUploadStarted(uploadId: String, videoId: String) {
                        Log.d(TAG, "onUploadStarted uploadId=$uploadId")
                    }

                    override fun onUploadError(error: UploadError, videoId: String?) {
                        Log.d(TAG, "onUploadError: $error")
                        listener.onUploadError(error, videoId)
                    }

                    override fun onUploadCancelled(videoId: String) {
                        Log.d(TAG, "onUploadCancelled")
                        listener.onUploadCancelled(videoId)
                    }
                }
            )

            synchronized(lock) {
                uploadsInProgress[uploadId] = request
                listener.onUploadStarted(uploadId, videoId)
            }

            Log.d(TAG, "uploadsInProgress: $uploadsInProgress")
        }
    }

    override fun cancelUpload(uploadId: String) {
        Log.d(TAG, "cancelUpload: $uploadId")

        val request: UploadRequest?

        synchronized(lock) {
            request = uploadsInProgress.remove(uploadId)
        }

        if (request == null) {
            Log.w(TAG, "Cannot cancel, upload ID $uploadId not found")
            return
        }

        scope.launch { request.cancel() }

        scope.launch { deleteVideo(request.libraryId, request.videoId) }
    }

    override fun pauseUpload(uploadId: String) {
        Log.d(TAG, "pauseUpload: $uploadId")

        val request: UploadRequest?

        synchronized(lock) {
            request = uploadsInProgress.get(uploadId)
        }

        if (request == null) {
            Log.w(TAG, "Cannot pause, upload ID $uploadId not found")
            return
        }

        scope.launch { request.pause() }
    }

    override fun resumeUpload(uploadId: String) {
        Log.d(TAG, "resumeUpload: $uploadId")

        val request: UploadRequest?

        synchronized(lock) {
            request = uploadsInProgress.get(uploadId)
        }

        if (request == null) {
            Log.w(TAG, "Cannot resume, upload ID $uploadId not found")
            return
        }

        scope.launch { request.resume() }
    }

    private fun createVideo(
        libraryId: Long,
        title: String,
        collectionId: String? = null,
        thumbnailTime: Int? = null
    ): String? {
        val createVideoRequest = VideoCreateVideoRequest(
            title = title,
            collectionId = collectionId,
            thumbnailTime = thumbnailTime
        )
        val result = videosApi.videoCreateVideo(
            libraryId = libraryId,
            videoCreateVideoRequest = createVideoRequest
        )

        return result.guid
    }

    @Suppress("PrintStackTrace")
    private fun deleteVideo(libraryId: Long, videoId: String) {
        Log.d(TAG, "deleteVideo libraryId=$libraryId videoId=$videoId")
        try {
            val result = videosApi.videoDeleteVideo(libraryId, videoId)
            if (result.success == true) {
                Log.d(TAG, "Video deleted")
            } else {
                Log.e(TAG, "Error deleting video: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteVideo exception: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getFileInfo(uri: Uri): FileInfo? {

        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null

        cursor.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.moveToFirst()

            val name = it.getString(nameIndex)
            val size = it.getLong(sizeIndex)

            return FileInfo(name, size, inputStream)
        }
    }
}