package net.bunny.api.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and wraps its outcome in a [BunnyResult]: the value in [BunnyResult.Ok], or any
 * thrown exception mapped through [BunnyErrorMapper] into [BunnyResult.Err].
 *
 * Every repository call in the SDK goes through it, so a caller sees one error shape:
 *
 * ```kotlin
 * override suspend fun getLiveStream(libraryId: Long, streamId: String): BunnyResult<LiveStream> =
 *     withContext(dispatcher) {
 *         bunnyCatching { api.liveStreamGetByStreamId(libraryId, streamId).toDomain() }
 *     }
 * ```
 *
 * Two deliberate choices:
 *
 *  * [CancellationException] is rethrown, never mapped. Swallowing it would break structured
 *    concurrency — a cancelled caller would receive a fake "error" instead of cancelling.
 *  * Only [Exception]s are caught. JVM [Error]s (OOM, stack overflow) keep crashing loudly
 *    instead of surfacing as a retriable [BunnyError.Network].
 *
 * Deliberately not `inline`: the body would then be compiled into the caller, which forces the
 * exception-mapping table it uses to be public API too. One suspend lambda per network call costs
 * nothing next to the request; advertising the SDK's internals costs a lot.
 */
public suspend fun <T> bunnyCatching(block: suspend () -> T): BunnyResult<T> =
    try {
        BunnyResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        BunnyResult.Err(BunnyErrorMapper.map(e))
    }
