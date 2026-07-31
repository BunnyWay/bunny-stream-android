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
        // Release builds ship without demo credentials and the settings screen starts empty, so
        // there is nothing to initialise with until the user enters a key. The SDK rejects blank
        // credentials rather than pretending to be configured, and every screen already checks
        // BunnyStreamApi.isInitialized() before it calls anything.
        if (hasCredentials) {
            BunnyStreamApi.initialize(context, localPrefs.accessKey, localPrefs.libraryId)
        }
    }

    /** True once the user (or a debug build's `local.properties`) has supplied a key and library. */
    val hasCredentials: Boolean
        get() = localPrefs.accessKey.isNotBlank() && localPrefs.libraryId > 0

    // Uploaders are reached straight off the SDK (streamSdk.videoUploader /
    // streamSdk.tusVideoUploader). Before 4.0.0 a demo-side wrapper existed to hold the single
    // UploadListener every screen had to share; the upload Flow made both the wrapper and that
    // shared mutable listener unnecessary.
    //
    // Resolved on each use rather than held: re-entering credentials replaces the SDK's default
    // instance, and a cached handle would keep pointing at the released one.
    val streamSdk: StreamApi
        get() = BunnyStreamApi.getInstance()

    /**
     * The configured library, or `-1` when the SDK has no instance yet.
     *
     * The library id lives on the instance now, so there is nothing to read before one exists.
     * Screens use this to decide whether to load at all, which they have to do before touching
     * [streamSdk] anyway.
     */
    val libraryId: Long
        get() = if (BunnyStreamApi.isInitialized()) streamSdk.libraryId else -1L

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
        if (hasCredentials) {
            BunnyStreamApi.initialize(context, accessKey, libraryId)
        } else {
            BunnyStreamApi.release()
        }
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