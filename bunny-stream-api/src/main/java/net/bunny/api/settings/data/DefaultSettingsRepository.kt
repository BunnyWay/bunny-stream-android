package net.bunny.api.settings.data

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BunnyStreamApi
import net.bunny.api.settings.data.model.PlayerSettingsResponse
import net.bunny.api.settings.domain.SettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings

class DefaultSettingsRepository(
    private val httpClient: HttpClient,
    private val coroutineDispatcher: CoroutineDispatcher
) : SettingsRepository {

    override suspend fun fetchSettings(libraryId: Long, videoId: String, token: String?, expires: Long?):
            Either<String, PlayerSettings> = withContext(coroutineDispatcher) {
        val endpoint = buildString {
            append("${BunnyStreamApi.baseApi}/library/$libraryId/videos/$videoId/play")
            val params = mutableListOf<String>()
            if (token != null) params.add("token=$token")
            if (expires != null) params.add("expires=$expires")
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }

        return@withContext try {
            val response = httpClient.get(endpoint)
            when (response.status.value) {
                HttpStatusCode.OK.value -> {
                    val result: PlayerSettingsResponse = response.body()
                    Either.Right(result.toModel())
                }
                HttpStatusCode.Unauthorized.value -> Either.Left("Authorization required Unauthorized")
                HttpStatusCode.Forbidden.value -> Either.Left("Forbidden")
                HttpStatusCode.NotFound.value -> Either.Left("Not Found")
                else -> Either.Left("Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Either.Left("Unknown exception: ${e.message}")
        }
    }
}