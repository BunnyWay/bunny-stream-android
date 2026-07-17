package net.bunny.api.error

import com.google.gson.JsonParseException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import net.bunny.api.error.BunnyError.Companion.HTTP_FORBIDDEN
import net.bunny.api.error.BunnyError.Companion.HTTP_NOT_FOUND
import net.bunny.api.error.BunnyError.Companion.HTTP_UNAUTHORIZED
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException

/**
 * The single place where everything the HTTP stack can throw is turned into a [BunnyError].
 *
 * Inputs, in matching order:
 *
 *  * generated-client [ClientException]/[ServerException] — carry the HTTP status code; routed
 *    by [fromHttpStatus]. The repositories' manual OkHttp calls throw the same exception types
 *    on non-success responses, so they need no extra case here.
 *  * Gson [JsonParseException] (generated client) and Ktor [ContentConvertException] (settings
 *    path) — the response arrived but the body did not match the expected shape:
 *    [BunnyError.Decode].
 *  * [IOException] and subclasses — OkHttp's transport failures (DNS, connect, socket timeout,
 *    TLS, dropped connections): [BunnyError.Network].
 *  * anything else is treated as [BunnyError.Network] with `httpStatus = 0` too, which is what
 *    the SDK's error handling has always done with unrecognized exceptions: transient, safe to
 *    retry, cause preserved for logging.
 *
 * [kotlinx.coroutines.CancellationException] must never reach this mapper — cancellation is not
 * an error. [bunnyCatching] rethrows it before mapping; any other call site has to do the same.
 */
public object BunnyErrorMapper {

    /** Maps [throwable] to the matching [BunnyError]; total — never throws. */
    public fun map(throwable: Throwable): BunnyError = when (throwable) {
        is ClientException -> fromHttpStatus(throwable.statusCode, throwable.message)
        is ServerException -> fromHttpStatus(throwable.statusCode, throwable.message)
        is JsonParseException -> BunnyError.Decode(
            message = throwable.message ?: "Malformed response body",
            cause = throwable,
        )
        is ContentConvertException -> BunnyError.Decode(
            message = throwable.message ?: "Malformed response body",
            cause = throwable,
        )
        is IOException -> BunnyError.Network(
            message = "Network error: ${throwable.message ?: throwable::class.simpleName}",
            cause = throwable,
        )
        else -> BunnyError.Network(
            message = "Unexpected error: ${throwable.message ?: throwable::class.simpleName}",
            cause = throwable,
        )
    }

    /**
     * Maps an HTTP status to the matching [BunnyError] variant: `401`/`403` to [BunnyError.Auth],
     * `404` to [BunnyError.NotFound], any other valid status to [BunnyError.Http]. A status
     * outside `100..599` (e.g. the generated client's `-1` default when an exception was built
     * without one) means there was no usable response, so it maps to [BunnyError.Network].
     *
     * Messages keep the vocabulary the `Either<String, T>` surface has used since 3.x, so error
     * strings shown by existing integrations do not change wording mid-migration.
     */
    public fun fromHttpStatus(statusCode: Int, fallbackMessage: String?): BunnyError = when {
        statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> BunnyError.Auth(
            httpStatus = statusCode,
            message = httpErrorMessage(statusCode, fallbackMessage),
        )
        statusCode == HTTP_NOT_FOUND -> BunnyError.NotFound(
            message = httpErrorMessage(statusCode, fallbackMessage),
        )
        statusCode in VALID_HTTP_STATUS_RANGE -> BunnyError.Http(
            httpStatus = statusCode,
            message = httpErrorMessage(statusCode, fallbackMessage),
        )
        else -> BunnyError.Network(
            message = fallbackMessage ?: "HTTP failure without a status code",
        )
    }

    /** Status-to-message vocabulary shared with the pre-4.0.0 `Either<String, T>` surface. */
    private fun httpErrorMessage(statusCode: Int, fallback: String?): String = when (statusCode) {
        HTTP_UNAUTHORIZED -> "Authorization required Unauthorized"
        HTTP_FORBIDDEN -> "Forbidden"
        HTTP_NOT_FOUND -> "Not Found"
        else -> fallback ?: "Error: $statusCode"
    }

    private val VALID_HTTP_STATUS_RANGE = 100..599
}
