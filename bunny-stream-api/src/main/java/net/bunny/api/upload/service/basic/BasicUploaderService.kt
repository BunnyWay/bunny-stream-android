package net.bunny.api.upload.service.basic

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.preparePut
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import net.bunny.api.BuildConfig
import net.bunny.api.error.BunnyErrorMapper
import net.bunny.api.upload.model.FileInfo
import net.bunny.api.upload.model.PauseState
import net.bunny.api.upload.model.StreamContent
import net.bunny.api.upload.model.UploadEvent
import net.bunny.api.upload.service.UploadControl
import net.bunny.api.upload.service.UploadService
import kotlin.time.Duration

/**
 * Sends the whole file in a single `PUT`.
 *
 * Simple and fast, but the transfer cannot be held: there is no chunk boundary to stop at, so
 * every [UploadEvent.Progress] reports [PauseState.Unsupported] and pause/resume are no-ops.
 * A dropped connection restarts the upload from zero. Use
 * [net.bunny.api.upload.service.tus.TusUploaderService] when either matters.
 */
internal class BasicUploaderService(
    private val httpClient: HttpClient,
    private val coroutineDispatcher: CoroutineDispatcher,
) : UploadService {

    private companion object {
        private const val TAG = "BasicUploaderService"
    }

    /** One request, no offset to come back to. */
    override val supportsResuming: Boolean = false

    override fun upload(
        libraryId: Long,
        videoId: String,
        fileInfo: FileInfo,
        control: UploadControl,
    ): Flow<UploadEvent> = channelFlow {
        val terminal: UploadEvent? = try {
            coroutineScope {
                val watcher = launch {
                    control.awaitCancellation()
                    // There is no chunk boundary to poll, so the only way to stop a PUT that is
                    // already streaming is to cancel the coroutine running it.
                    this@coroutineScope.cancel()
                }
                val event = transfer(libraryId, videoId, fileInfo, this@channelFlow)
                watcher.cancel()
                event
            }
        } catch (e: CancellationException) {
            // Distinguishes "the caller cancelled this upload" from "our collector went away".
            // The first is a Cancelled event; the second must propagate, or structured concurrency
            // silently stops meaning anything. Read the control rather than a mirror flag written
            // from the watcher coroutine — that flag was a plain var shared across threads with no
            // happens-before edge, and reading it stale would drop the terminal event entirely.
            if (!control.isCancelled) throw e
            Log.d(TAG, "upload cancelled by caller")
            null
        }

        send(terminal ?: UploadEvent.Cancelled(videoId))
    }.flowOn(coroutineDispatcher)

    /**
     * Runs the request and returns the terminal event, pushing [UploadEvent.Progress] onto
     * [events] as the body drains. Never throws except for cancellation.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun transfer(
        libraryId: Long,
        videoId: String,
        fileInfo: FileInfo,
        events: SendChannel<UploadEvent>,
    ): UploadEvent {
        val url = "${BuildConfig.BASE_API}/library/$libraryId/videos/$videoId"
        var lastPercentage = -1

        return try {
            val statement = httpClient.preparePut(url) {
                contentType(ContentType.Application.OctetStream)
                setBody(StreamContent(fileInfo.inputStream, fileInfo.size))
                timeout {
                    requestTimeoutMillis = Duration.INFINITE.inWholeMilliseconds
                }
                onUpload { bytesSentTotal, contentLength ->
                    // contentLength is null for a stream of unknown length, and dividing by it
                    // unguarded is how this used to report Infinity percent.
                    val total = contentLength ?: 0L
                    if (total <= 0L) return@onUpload

                    val percentage = ((bytesSentTotal.toDouble() / total) * PERCENT)
                        .toInt()
                        .coerceIn(0, PERCENT)
                    if (percentage != lastPercentage) {
                        lastPercentage = percentage
                        events.send(
                            UploadEvent.Progress(percentage, videoId, PauseState.Unsupported),
                        )
                    }
                }
            }

            val response = statement.execute()

            if (response.status.isSuccess()) {
                UploadEvent.Completed(videoId)
            } else {
                // Before 4.0.0 this branch built an Either.Left and dropped it on the floor, so a
                // rejected upload reported nothing at all and the UI sat at its last percentage.
                UploadEvent.Failed(
                    error = BunnyErrorMapper.fromHttpStatus(
                        statusCode = response.status.value,
                        fallbackMessage = response.status.description,
                    ),
                    videoId = videoId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "error uploading: ${e.message}")
            UploadEvent.Failed(BunnyErrorMapper.map(e), videoId)
        }
    }
}

private const val PERCENT = 100
