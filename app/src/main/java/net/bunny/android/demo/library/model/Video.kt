package net.bunny.android.demo.library.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Video(
    val id: String,
    val name: String,
    val duration: String,
    val status: VideoStatus,
    val size: Double,
    val viewCount: String,
    val thumbnailUrl: String? = null
): Parcelable

enum class VideoStatus(private val value: Int) {
    CREATED(0),
    UPLOADED(1),
    PROCESSING(2),
    TRANSCODING(3),
    FINISHED(4),
    ERROR(5),
    UPLOAD_FAILED(6)
}
