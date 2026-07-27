package net.bunny.android.demo.di

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import net.bunny.android.demo.settings.DualPublishPreferences
import net.bunny.android.demo.settings.LocalPrefs
import net.bunny.android.demo.settings.ResumePositionPreferences
import net.bunny.api.BunnyStreamApi
import net.bunny.api.StreamApi

@SuppressLint("StaticFieldLeak")
class Di(val context: Context) {

    private val prefs = context.getSharedPreferences("", Context.MODE_PRIVATE)
    private val resumePrefs = context.getSharedPreferences("resume_position_prefs", Context.MODE_PRIVATE)
    private val dualPublishPrefs = context.getSharedPreferences("dual_publish_prefs", Context.MODE_PRIVATE)

    val localPrefs = LocalPrefs(prefs)
    val resumePositionPrefs = ResumePositionPreferences(resumePrefs) // Add this line
    val dualPublishPreferences = DualPublishPreferences(dualPublishPrefs)

    init {
        BunnyStreamApi.initialize(context, localPrefs.accessKey, localPrefs.libraryId)
    }

    // Uploaders are reached straight off the SDK (streamSdk.videoUploader /
    // streamSdk.tusVideoUploader). Before 4.0.0 a demo-side wrapper existed to hold the single
    // UploadListener every screen had to share; the upload Flow made both the wrapper and that
    // shared mutable listener unnecessary.
    var streamSdk: StreamApi = BunnyStreamApi.getInstance()
        private set

    /**
     * The upload currently in flight, if any.
     *
     * Held at application scope on purpose: the transfer lives inside the SDK, so the screen that
     * started it can be destroyed and rebuilt while it keeps going. This is the "keep the upload id
     * somewhere that outlives the screen" contract from
     * [net.bunny.api.upload.VideoUploader] in its smallest possible form — a real app would persist
     * it rather than hold it in memory.
     */
    var activeUpload: ActiveUpload? = null

    /**
     * The trailer upload in flight, if any. Separate from [activeUpload] because the library screen
     * and the live-stream editor upload independently and must not steal each other's handle.
     */
    var activeTrailerUpload: String? = null

    fun updateKeys(accessKey: String, libraryId: Long) {
        localPrefs.accessKey = accessKey
        localPrefs.libraryId = libraryId
        // Re-initialising stops any in-flight upload, so the handles to them are dead too. The SDK
        // gives each one a terminal event first, so any screen still observing is told.
        activeUpload = null
        activeTrailerUpload = null
        BunnyStreamApi.initialize(context, accessKey, libraryId)
        streamSdk = BunnyStreamApi.getInstance()
    }
}

/**
 * What a caller has to remember about an upload to survive its screen: the id to address it by,
 * enough to re-observe it, and — once known — the video id needed to continue it after a failure.
 */
data class ActiveUpload(
    val uploadId: String,
    val libraryId: Long,
    val videoUri: Uri,
    val useTus: Boolean,
    val videoId: String? = null,
)