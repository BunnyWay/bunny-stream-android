package net.bunny.android.demo.library

import android.net.Uri
import net.bunny.api.upload.service.UploadListener

interface VideoUploadService {

    var uploadListener: UploadListener?

    fun uploadVideo(libraryId: Long, videoUri: Uri)

    fun cancelUpload(uploadId: String)

    fun pauseUpload(uploadId: String)

    fun resumeUpload(uploadId: String)
}