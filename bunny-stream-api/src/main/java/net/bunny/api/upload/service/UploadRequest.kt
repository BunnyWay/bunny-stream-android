package net.bunny.api.upload.service

/**
 * Class containing info about video that is submitted for upload
 * @param libraryId Library ID
 * @param videoId Video ID
 */
abstract class UploadRequest(
    open val libraryId: Long,
    open val videoId: String,
) {
    /**
     * Cancels upload
     */
    abstract suspend fun cancel()

    /**
     * Pauses upload
     */
    abstract suspend fun pause()

    /**
     * Resumes upload
     */
    abstract suspend fun resume()
}