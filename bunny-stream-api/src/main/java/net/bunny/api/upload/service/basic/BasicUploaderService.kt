package net.bunny.api.upload.service.basic

import android.util.Log
import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.preparePut
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.bunny.api.BuildConfig
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.HttpStatusCodes
import net.bunny.api.upload.model.StreamContent
import net.bunny.api.upload.model.UploadError
import net.bunny.api.upload.service.PauseState
import net.bunny.api.upload.service.UploadListener
import net.bunny.api.upload.service.UploadRequest
import net.bunny.api.upload.service.UploadService
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

class BasicUploaderService(
    private val httpClient: HttpClient,
    private val coroutineDispatcher: CoroutineDispatcher
): UploadService {

    companion object {
        private const val TAG = "BasicUploaderService"
    }

    private val superVisorJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        exception.printStackTrace()
        Log.d(TAG, "CoroutineExceptionHandler: context=$context exception=$exception")
    }
    private val scope = CoroutineScope(coroutineDispatcher + exceptionHandler + superVisorJob)

    override suspend fun upload(
        libraryId: Long, videoId: String, fileInfo: FileInfo, listener: UploadListener
    ): UploadRequest {
        val uploadJob = scope.launch {
            val url = "${BuildConfig.BASE_API}/library/$libraryId/videos/$videoId"
            var uploadProcess = 0
            val request = httpClient.preparePut(url) {
                contentType(ContentType.Application.OctetStream)
                setBody(StreamContent(fileInfo.inputStream))
                timeout {
                    requestTimeoutMillis = Duration.INFINITE.inWholeMilliseconds
                }
                onUpload { bytesSentTotal, contentLength ->
                    val percentage = ((bytesSentTotal / (contentLength?.toFloat() ?: 0f)) * 100).toInt()
                    if(percentage != uploadProcess) {
                        uploadProcess = percentage
                        listener.onProgressUpdated(percentage, videoId, PauseState.Unsupported)
                    }
                }
            }

            try {
                val response = request.execute()

                if(response.status.isSuccess()) {
                    listener.onUploadDone(videoId)
                } else {
                    when (response.status.value) {
                        HttpStatusCodes.UNAUTHORIZED -> Either.Left(UploadError.Unauthorized)
                        HttpStatusCodes.NOT_FOUND -> Either.Left(UploadError.VideoNotFound)
                        HttpStatusCodes.SERVER_ERROR -> Either.Left(UploadError.ServerError)
                        else -> Either.Left(UploadError.UnknownError(response.status.toString()))
                    }
                }

            } catch (e: Exception) {
                if(e is CancellationException){
                    Log.d(TAG, "upload cancelled")
                    listener.onUploadCancelled(videoId)
                } else {
                    Log.w(TAG, "error uploading: ${e.message}")
                    e.printStackTrace()
                    listener.onUploadError(UploadError.UnknownError(e.message ?: e.toString()), videoId)
                }
            }
        }

        return BasicUploadRequest(libraryId, videoId, uploadJob, listener)
    }
}