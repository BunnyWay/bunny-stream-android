package net.bunny.api.upload.model

/**
 * Class describing video upload failures
 */
sealed class UploadError {

    /**
     * AccessKey is not valid
     */
    object Unauthorized : UploadError()

    /**
     * Video cannot be uploaded since target video ID is not found.
     * Happens when upload is attempted without creating a video first
     */
    object VideoNotFound : UploadError()

    /**
     * General server error
     */
    object ServerError : UploadError()

    /**
     * Video cannot be created for upload
     */
    object ErrorCreating : UploadError()

    /**
     * Selected video file cannot be read (e.g. when file gets deleted or becomes inaccessible)
     */
    object ErrorReadingFile : UploadError()

    /**
     * Unknown error
     * @param message String describing the error
     */
    data class UnknownError(val message: String) : UploadError()

}
