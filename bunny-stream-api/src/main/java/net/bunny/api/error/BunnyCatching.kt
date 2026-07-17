package net.bunny.api.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and wraps its outcome in a [BunnyResult]: the value in [BunnyResult.Ok], or any
 * thrown exception mapped through [BunnyErrorMapper] into [BunnyResult.Err].
 *
 * This is the entry point the repositories move to as they migrate off `Either<String, T>`:
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
 */
public suspend inline fun <T> bunnyCatching(crossinline block: suspend () -> T): BunnyResult<T> =
    try {
        BunnyResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        BunnyResult.Err(BunnyErrorMapper.map(e))
    }
