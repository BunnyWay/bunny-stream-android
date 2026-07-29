package net.bunny.api.error

import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.StatusModel

/**
 * Bunny's management APIs answer `HTTP 200` with `success = false` when a mutation is rejected for
 * a reason that is not an HTTP-level failure — a validation error, a resource in the wrong state.
 * A successful HTTP exchange is therefore not the same as a successful mutation, and a repository
 * that only checks the status code reports those rejections as `BunnyResult.Ok`.
 *
 * Throws [ClientException] so [bunnyCatching] translates it through the shared taxonomy, keeping
 * the error-message vocabulary consistent with everything else.
 */
internal fun StatusModel.requireSuccess(failureMessage: String = "The request was rejected") {
    if (success != true) {
        throw ClientException(
            message = message?.takeIf { it.isNotBlank() } ?: failureMessage,
            statusCode = statusCode ?: 0,
        )
    }
}
