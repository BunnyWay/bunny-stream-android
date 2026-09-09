package net.bunny.android.demo.livestream.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI-facing view of a live stream. Trimmed down from the SDK's domain model so the screen only
 * carries the fields it actually renders or edits.
 */
@Parcelize
data class LiveStreamUiModel(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,
    val isPublic: Boolean,
    val dvrEnabled: Boolean,
    val recordVod: Boolean,
    val scheduledStartTime: String?,
    val streamKey: String?,
    val playbackUrlHls: String?,
) : Parcelable
