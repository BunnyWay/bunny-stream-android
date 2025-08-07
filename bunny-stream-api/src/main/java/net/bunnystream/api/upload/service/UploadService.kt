package net.bunnystream.api.upload.service

import net.bunny.api.upload.model.FileInfo
import java.io.InputStream

/**
 * Component that does executes upload
 */
interface UploadService {

    /**
     * Uploads content in for of [InputStream] to provided url
     * @param libraryId target library ID
     * @param videoId target video ID
     * @param fileInfo details about the file to be uploaded, see [FileInfo]
     * @param listener interface to get notified about upload status changes, see [UploadListener]
     * @return [UploadRequest] containing info about submitted upload
     */
    suspend fun upload(
        libraryId: Long,
        videoId: String,
        fileInfo: FileInfo,
        listener: UploadListener
    ): UploadRequest
}