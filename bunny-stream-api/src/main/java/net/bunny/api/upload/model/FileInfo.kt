package net.bunny.api.upload.model

import java.io.InputStream

/**
 * Name, size and an open stream over the file picked for upload.
 *
 * Internal: integrators hand [net.bunny.api.upload.VideoUploader] a content URI and the SDK
 * resolves this itself. The stream is owned by the upload flow and closed when it completes.
 */
internal data class FileInfo(
    val fileName: String,
    val size: Long,
    val inputStream: InputStream
)
