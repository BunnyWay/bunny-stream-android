package net.bunny.android.demo.library

import android.net.Uri
import android.util.Log
import net.bunny.api.upload.VideoUploader
import net.bunny.api.upload.model.UploadError
import net.bunny.api.upload.service.PauseState
import net.bunny.api.upload.service.UploadListener

class DefaultVideoUploadService(
    private val videoUploader: VideoUploader
) : VideoUploadService {

    companion object {
        private const val TAG = "DefaultVideoUploadService"
    }

    override var uploadListener: UploadListener? = null

    override fun uploadVideo(libraryId: Long, videoUri: Uri) {
        Log.d(TAG, "uploadVideo videoUri=$videoUri videoUploader=$videoUploader")
        videoUploader.uploadVideo(libraryId, videoUri, object : UploadListener {
            override fun onUploadError(error: UploadError, videoId: String?) {
                Log.d(TAG, "onVideoUploadError: $error")
                uploadListener?.onUploadError(error, videoId)
            }

            override fun onUploadDone(videoId: String) {
                Log.d(TAG, "onVideoUploadDone")
                uploadListener?.onUploadDone(videoId)
            }

            override fun onUploadStarted(uploadId: String, videoId: String) {
                Log.d(TAG, "onVideoUploadStarted: uploadId=$uploadId")
                uploadListener?.onUploadStarted(uploadId, videoId)
            }

            override fun onProgressUpdated(
                percentage: Int,
                videoId: String,
                pauseState: PauseState
            ) {
                Log.d(TAG, "onUploadProgress: percentage=$percentage")
                uploadListener?.onProgressUpdated(percentage, videoId, pauseState)
            }

            override fun onUploadCancelled(videoId: String) {
                Log.d(TAG, "onUploadProgress: onVideoUploadCancelled")
                uploadListener?.onUploadCancelled(videoId)
            }
        })
    }

    override fun cancelUpload(uploadId: String) {
        Log.d(TAG, "cancelUpload videoUploader=$videoUploader")
        videoUploader.cancelUpload(uploadId)
    }

    override fun pauseUpload(uploadId: String) {
        Log.d(TAG, "pauseUpload videoUploader=$videoUploader")
        videoUploader.pauseUpload(uploadId)
    }

    override fun resumeUpload(uploadId: String) {
        Log.d(TAG, "resumeUpload videoUploader=$videoUploader")
        videoUploader.resumeUpload(uploadId)
    }
}