package net.bunnystream.api.upload.service.tus

import android.util.Log
import io.tus.java.client.TusUploader
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.bunny.api.upload.service.UploadListener
import net.bunny.api.upload.service.UploadRequest

class TusUploadRequest(
    override val libraryId: Long,
    override val videoId: String,
    private val uploader: TusUploader,
    private val listener: UploadListener,
    private val pauseListener: (Boolean) -> Unit,
) : UploadRequest(libraryId, videoId) {

    @Suppress("PrintStackTrace")
    override suspend fun cancel() {
        withContext(NonCancellable) {
            Log.d("TusUploadRequest", "cancel")
            try {
                pauseListener(false)
                uploader.finish()
            } catch (e: Exception) {
                Log.e("TusUploadRequest", "cancel exception: ${e.message}")
                e.printStackTrace()
            }
            listener.onUploadCancelled(videoId)
        }
    }

    override suspend fun pause() {
        Log.d("TusUploadRequest", "pause")
        try {
            pauseListener(true)
        } catch (e: Exception){
            Log.e("TusUploadRequest", "pause exception: ${e.message}")
            e.printStackTrace()
        }
    }

    override suspend fun resume() {
        Log.d("TusUploadRequest", "resume")
        try {
            pauseListener(false)
        } catch (e: Exception){
            Log.e("TusUploadRequest", "pause exception: ${e.message}")
            e.printStackTrace()
        }
    }
}