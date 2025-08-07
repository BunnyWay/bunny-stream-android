package net.bunny.api.upload.model

import java.io.InputStream

/**
 * Information about file selected for upload
 */
data class FileInfo(
    val fileName: String,
    val size: Long,
    val inputStream: InputStream
)
