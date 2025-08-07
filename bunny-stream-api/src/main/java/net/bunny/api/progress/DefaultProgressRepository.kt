package net.bunny.api.progress

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BunnyStreamApi

class DefaultProgressRepository(
    private val httpClient: HttpClient,
    private val coroutineDispatcher: CoroutineDispatcher
) : ProgressRepository {

    override suspend fun saveProgress(libraryId: Long, videoId: String, position: Long): Either<String, Unit> = 
        withContext(coroutineDispatcher) {
            try {
                val endpoint = "${BunnyStreamApi.baseApi}/library/$libraryId/videos/$videoId/progress"
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("position" to position))
                }
                Either.Right(Unit)
            } catch (e: Exception) {
                Either.Left("Failed to save progress: ${e.message}")
            }
        }

    override suspend fun getProgress(libraryId: Long, videoId: String): Either<String, Long> = 
        withContext(coroutineDispatcher) {
            try {
                val endpoint = "${BunnyStreamApi.baseApi}/library/$libraryId/videos/$videoId/progress"
                val response = httpClient.get(endpoint)
                // Parse response to get position
                Either.Right(0L) // Replace with actual parsing
            } catch (e: Exception) {
                Either.Left("Failed to get progress: ${e.message}")
            }
        }

    override suspend fun clearProgress(libraryId: Long, videoId: String): Either<String, Unit> = 
        withContext(coroutineDispatcher) {
            try {
                val endpoint = "${BunnyStreamApi.baseApi}/library/$libraryId/videos/$videoId/progress"
                httpClient.delete(endpoint)
                Either.Right(Unit)
            } catch (e: Exception) {
                Either.Left("Failed to clear progress: ${e.message}")
            }
        }
}
