package net.bunny.api.http

import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.ClientException

/**
 * Escape hatch for the handful of endpoints the OpenAPI generator cannot call.
 *
 * The generator models an optional `application/octet-stream` body as `body: File? = null` but
 * still sends `Content-Type: application/octet-stream`. When that body is left null, its
 * `requestBody` helper matches no branch and throws
 * `UnsupportedOperationException("requestBody currently only supports JSON body, byte body and
 * File body.")` before anything reaches the network — so the "set the thumbnail from a URL"
 * variant of those endpoints can never succeed through the generated client.
 *
 * Rather than each repository re-deriving the workaround, they issue the request here. Everything
 * is taken from the generated client that would otherwise have made the call — same base URL,
 * same [okhttp3.OkHttpClient], same `AccessKey` — so timeouts, interceptors and logging behave
 * exactly as they do for every other call.
 *
 * These run inside a `bunnyCatching` block, so the [ClientException] thrown on a non-2xx is
 * translated by the shared error mapper like any generated failure.
 */
internal fun ApiClient.postExpectingSuccess(
    url: String,
    body: RequestBody = ByteArray(0).toRequestBody(null),
    failureMessage: String,
) {
    val builder = Request.Builder()
        .url(url)
        .post(body)
        .header("Accept", "application/json")

    // No AccessKey here: the client this runs on is the instance's, and its interceptor
    // authenticates every request that goes through it.
    client.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            throw ClientException(
                message = response.body?.string()?.takeIf { it.isNotBlank() } ?: failureMessage,
                statusCode = response.code,
            )
        }
    }
}
