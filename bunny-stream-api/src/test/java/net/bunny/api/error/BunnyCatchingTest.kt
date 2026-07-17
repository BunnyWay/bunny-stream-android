package net.bunny.api.error

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException

/**
 * Tests for [bunnyCatching] — the wrapper the repositories migrate to. The two behaviors that
 * must never regress: exceptions become mapped [BunnyResult.Err]s, and cancellation is rethrown
 * untouched (mapping it would break structured concurrency).
 */
class BunnyCatchingTest {

    @Test
    fun `success wraps the value in Ok`() = runTest {
        val result = bunnyCatching { "value" }

        assertEquals(BunnyResult.Ok("value"), result)
    }

    @Test
    fun `thrown exceptions come back as mapped Err`() = runTest {
        val result = bunnyCatching<Unit> { throw ClientException("ignored", 404) }

        assertEquals(BunnyResult.Err(BunnyError.NotFound("Not Found")), result)
    }

    @Test
    fun `err carries terminality for the polling loops`() = runTest {
        val terminal = bunnyCatching<Unit> { throw ClientException("ignored", 401) }
        val transient = bunnyCatching<Unit> { throw ClientException("down", 503) }

        assertTrue((terminal as BunnyResult.Err).isTerminal)
        assertTrue(!(transient as BunnyResult.Err).isTerminal)
    }

    @Test
    fun `cancellation is rethrown not mapped`() = runTest {
        try {
            bunnyCatching<Unit> { throw CancellationException("cancelled") }
            fail("CancellationException must propagate")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
