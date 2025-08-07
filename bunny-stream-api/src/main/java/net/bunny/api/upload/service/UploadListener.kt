package net.bunny.api.upload.service

import net.bunny.api.upload.model.UploadError

/**
 * Collection of callbacks to get info about video uploads
 */
interface UploadListener {
    /**
     * Called when video uplaod fails
     * @param error
     * @see UploadError
     * @param videoId ID of video failed uploading
     */
    fun onUploadError(error: UploadError, videoId: String?)

    /**
     * Called when video upload is finished
     * @param videoId ID of video that is done uploading
     */
    fun onUploadDone(videoId: String)

    /**
     * Called when video upload starts
     * @param uploadId ID of the upload that can be used to cancel the upload
     * @param videoId ID of video that is being uploaded
     */
    fun onUploadStarted(uploadId: String, videoId: String)

    /**
     * Called when upload progress changes
     * @param videoId ID of video that is being uploaded
     */
    fun onProgressUpdated(percentage: Int, videoId: String, pauseState: PauseState)

    /**
     * Called when upload gets cancelled
     * @param videoId ID of video that upload is canceled for
     */
    fun onUploadCancelled(videoId: String)
}