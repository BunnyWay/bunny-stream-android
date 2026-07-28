package net.bunny.api.settings.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.bunny.api.BunnyStreamApi
import net.bunny.api.error.BunnyErrorMapper
import net.bunny.api.error.BunnyResult
import net.bunny.api.error.bunnyCatching
import net.bunny.api.settings.data.model.PlayerSettingsResponse
import net.bunny.api.settings.domain.SettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings

internal class DefaultSettingsRepository(
    private val httpClient: HttpClient,
    private val coroutineDispatcher: CoroutineDispatcher
) : SettingsRepository {

    override suspend fun fetchSettings(
        libraryId: Long,
        videoId: String,
        token: String?,
        expires: Long?,
    ): BunnyResult<PlayerSettings> = withContext(coroutineDispatcher) {
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

        // Transport failures from the Ktor engine map here; a non-OK status goes through the
        // shared status routing; a malformed 200 body surfaces as Decode from [bunnyCatching].
        val response = try {
            httpClient.get(endpoint)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            return@withContext BunnyResult.Err(BunnyErrorMapper.map(e))
        }

        if (response.status.value != HttpStatusCode.OK.value) {
            return@withContext BunnyResult.Err(
                BunnyErrorMapper.fromHttpStatus(response.status.value, null)
            )
        }

        bunnyCatching { response.body<PlayerSettingsResponse>().toModel() }
    }
}
