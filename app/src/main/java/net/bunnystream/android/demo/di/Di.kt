package net.bunnystream.android.demo.di

import android.annotation.SuppressLint
import android.content.Context
import net.bunny.android.demo.library.DefaultVideoUploadService
import net.bunny.android.demo.settings.LocalPrefs
import net.bunny.android.demo.settings.ResumePositionPreferences
import net.bunny.api.BunnyStreamApi
import net.bunny.api.StreamApi

@SuppressLint("StaticFieldLeak")
class Di(val context: Context) {

    private val prefs = context.getSharedPreferences("", Context.MODE_PRIVATE)
    private val resumePrefs = context.getSharedPreferences("resume_position_prefs", Context.MODE_PRIVATE)

    val localPrefs = LocalPrefs(prefs)
    val resumePositionPrefs = ResumePositionPreferences(resumePrefs) // Add this line

    init {
        BunnyStreamApi.initialize(context, localPrefs.accessKey, localPrefs.libraryId)
    }

    var streamSdk: StreamApi = BunnyStreamApi.getInstance()
        private set

    var videoUploadService = DefaultVideoUploadService(streamSdk.videoUploader)

    var tusVideoUploadService = DefaultVideoUploadService(streamSdk.tusVideoUploader)

    fun updateKeys(accessKey: String, libraryId: Long) {
        localPrefs.accessKey = accessKey
        localPrefs.libraryId = libraryId
        BunnyStreamApi.initialize(context, accessKey, libraryId)
        streamSdk = BunnyStreamApi.getInstance()
        videoUploadService = DefaultVideoUploadService(streamSdk.videoUploader)
        tusVideoUploadService = DefaultVideoUploadService(streamSdk.tusVideoUploader)
    }
}