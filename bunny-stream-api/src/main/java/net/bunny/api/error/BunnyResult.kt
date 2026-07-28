package net.bunny.api.error

/**
 * The result envelope for the SDK's management calls: either an [Ok] carrying the value, or an
 * [Err] carrying a typed [BunnyError].
 *
 * ```kotlin
 * when (val result = repo.getLiveStream(streamId)) {
 *     is BunnyResult.Ok -> render(result.value)
 *     is BunnyResult.Err -> if (result.isTerminal) giveUp(result.message) else retryLater()
 * }
 * ```
 *
 * Why a dedicated type rather than the two obvious candidates:
 *
 *  * `kotlin.Result` requires the failure to be a [Throwable] and exposes it as such, so the
 *    typed [BunnyError] taxonomy would be lost behind casts at every call site.
 *  * Arrow's `Either` would tie the SDK's public API to a third-party library. Arrow is no longer
 *    used anywhere in the SDK, so integrators need nothing extra on their classpath to read an
 *    error.
 *
 * The shape follows the polling result the live player has used since the live release
 * (success/failure with an HTTP status and a derivable terminal flag), promoted from one method
 * to the whole management surface.
 */
public sealed class BunnyResult<out T> {

    /** The call succeeded. */
    public data class Ok<out T>(public val value: T) : BunnyResult<T>()

    /**
     * The call failed. [httpStatus], [message] and [isTerminal] mirror [error] so the common
     * "log and decide whether to retry" path needs no second unwrap.
     */
    public data class Err(public val error: BunnyError) : BunnyResult<Nothing>() {
        public val httpStatus: Int get() = error.httpStatus
        public val message: String get() = error.message
        public val isTerminal: Boolean get() = error.isTerminal
    }
}

/** The value when this is [BunnyResult.Ok], `null` otherwise. */
public fun <T> BunnyResult<T>.getOrNull(): T? = (this as? BunnyResult.Ok)?.value

/** The error when this is [BunnyResult.Err], `null` otherwise. */
public fun <T> BunnyResult<T>.errorOrNull(): BunnyError? = (this as? BunnyResult.Err)?.error

/** Transforms the value of an [BunnyResult.Ok]; an [BunnyResult.Err] passes through unchanged. */
public inline fun <T, R> BunnyResult<T>.map(transform: (T) -> R): BunnyResult<R> = when (this) {
    is BunnyResult.Ok -> BunnyResult.Ok(transform(value))
    is BunnyResult.Err -> this
}

/** Collapses the result into a single value by applying the matching side. */
public inline fun <T, R> BunnyResult<T>.fold(
    onOk: (T) -> R,
    onErr: (BunnyError) -> R,
): R = when (this) {
    is BunnyResult.Ok -> onOk(value)
    is BunnyResult.Err -> onErr(error)
}
