package net.bunny.api.error

import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException

/**
 * Tests for [BunnyErrorMapper] — the single exception-to-[BunnyError] mapping. Priorities:
 *
 *  1. Status routing: 401/403 -> [BunnyError.Auth], 404 -> [BunnyError.NotFound], everything
 *     else with a real status -> [BunnyError.Http]. A wrong bucket flips terminal/transient and
 *     with it the polling loops' give-up-vs-retry decision.
 *  2. Transport and decode failures land on `httpStatus = 0` variants (transient).
 *  3. Message vocabulary stays byte-identical with the 3.x `Either<String, T>` surface.
 */
class BunnyErrorMapperTest {

    // region — generated client exceptions: status routing

    @Test
    fun `401 and 403 map to Auth with the 3x vocabulary`() {
        val unauthorized = BunnyErrorMapper.map(ClientException("ignored", 401))
        val forbidden = BunnyErrorMapper.map(ClientException("ignored", 403))

        assertEquals(BunnyError.Auth(401, "Authorization required Unauthorized"), unauthorized)
        assertEquals(BunnyError.Auth(403, "Forbidden"), forbidden)
    }

    @Test
    fun `404 maps to NotFound`() {
        assertEquals(
            BunnyError.NotFound("Not Found"),
            BunnyErrorMapper.map(ClientException("ignored", 404)),
        )
    }

    @Test
    fun `410 maps to a terminal Http error`() {
        val gone = BunnyErrorMapper.map(ClientException("Gone", 410))

        assertEquals(BunnyError.Http(410, "Gone"), gone)
        assertTrue(gone.isTerminal)
    }

    @Test
    fun `other 4xx map to transient Http with the fallback message`() {
        val unprocessable = BunnyErrorMapper.map(ClientException("Validation failed", 422))

        assertEquals(BunnyError.Http(422, "Validation failed"), unprocessable)
        assertFalse(unprocessable.isTerminal)
    }

    @Test
    fun `5xx map to transient Http`() {
        val internal = BunnyErrorMapper.map(ServerException("Server error", 500))
        val unavailable = BunnyErrorMapper.map(ServerException(null, 503))

        assertEquals(BunnyError.Http(500, "Server error"), internal)
        assertEquals(BunnyError.Http(503, "Error: 503"), unavailable)
        assertFalse(internal.isTerminal)
        assertFalse(unavailable.isTerminal)
    }

    @Test
    fun `client exception without a status code maps to Network`() {
        // The generated constructor defaults statusCode to -1; no usable response existed.
        val noStatus = BunnyErrorMapper.map(ClientException("connection reset"))

        assertEquals(0, noStatus.httpStatus)
        assertTrue(noStatus is BunnyError.Network)
    }

    // endregion

    // region — transport and decode

    @Test
    fun `io exceptions map to Network with the cause preserved`() {
        val timeout = SocketTimeoutException("timeout")
        val mapped = BunnyErrorMapper.map(timeout)

        assertTrue(mapped is BunnyError.Network)
        assertEquals(0, mapped.httpStatus)
        assertFalse(mapped.isTerminal)
        assertSame(timeout, (mapped as BunnyError.Network).cause)
    }

    @Test
    fun `the whole io exception family lands on Network`() {
        listOf(
            UnknownHostException("video.bunnycdn.com"),
            ConnectException("refused"),
            IOException("dropped"),
        ).forEach { exception ->
            assertTrue(
                "${exception::class.simpleName} should map to Network",
                BunnyErrorMapper.map(exception) is BunnyError.Network,
            )
        }
    }

    @Test
    fun `gson parse failures map to Decode with the cause preserved`() {
        val parse = JsonSyntaxException("Expected an int but was BEGIN_OBJECT")
        val mapped = BunnyErrorMapper.map(parse)

        assertTrue(mapped is BunnyError.Decode)
        assertEquals(0, mapped.httpStatus)
        assertFalse(mapped.isTerminal)
        assertSame(parse, (mapped as BunnyError.Decode).cause)
    }

    @Test
    fun `unrecognized exceptions map to transient Network keeping the cause`() {
        val unexpected = IllegalStateException("mapper must be total")
        val mapped = BunnyErrorMapper.map(unexpected)

        assertTrue(mapped is BunnyError.Network)
        assertFalse(mapped.isTerminal)
        assertSame(unexpected, (mapped as BunnyError.Network).cause)
    }

    // endregion

    @Test
    fun `fromHttpStatus is usable directly for manual call sites`() {
        assertEquals(
            BunnyError.Auth(401, "Authorization required Unauthorized"),
            BunnyErrorMapper.fromHttpStatus(401, null),
        )
        assertEquals(BunnyError.Http(500, "boom"), BunnyErrorMapper.fromHttpStatus(500, "boom"))
        assertTrue(BunnyErrorMapper.fromHttpStatus(-1, null) is BunnyError.Network)
    }
}
