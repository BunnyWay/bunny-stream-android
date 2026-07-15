package net.bunny.api.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [BunnyError] taxonomy and the [BunnyResult] envelope. The contract under test is
 * the one the cross-platform error model relies on: every variant exposes a numeric [httpStatus]
 * (`0` = no usable HTTP response), and terminality is derivable as exactly {401, 403, 404, 410}.
 */
class BunnyErrorTaxonomyTest {

    // region — httpStatus derivation

    @Test
    fun `network and decode carry no http status`() {
        assertEquals(0, BunnyError.Network("timeout").httpStatus)
        assertEquals(0, BunnyError.Decode("bad json").httpStatus)
    }

    @Test
    fun `not found is always 404 and auth keeps its code`() {
        assertEquals(404, BunnyError.NotFound("Not Found").httpStatus)
        assertEquals(401, BunnyError.Auth(401, "Unauthorized").httpStatus)
        assertEquals(403, BunnyError.Auth(403, "Forbidden").httpStatus)
    }

    @Test
    fun `auth rejects non-auth status codes`() {
        var thrown = false
        try {
            BunnyError.Auth(500, "nope")
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    // endregion

    // region — terminality: exactly {401, 403, 404, 410}

    @Test
    fun `auth not-found and gone are terminal`() {
        assertTrue(BunnyError.Auth(401, "").isTerminal)
        assertTrue(BunnyError.Auth(403, "").isTerminal)
        assertTrue(BunnyError.NotFound("").isTerminal)
        assertTrue(BunnyError.Http(410, "library deleted").isTerminal)
    }

    @Test
    fun `server errors transport and decode failures are transient`() {
        assertFalse(BunnyError.Http(500, "").isTerminal)
        assertFalse(BunnyError.Http(503, "").isTerminal)
        assertFalse(BunnyError.Http(422, "").isTerminal)
        assertFalse(BunnyError.Http(429, "").isTerminal)
        assertFalse(BunnyError.Network("dns").isTerminal)
        assertFalse(BunnyError.Decode("shape").isTerminal)
    }

    // endregion

    // region — BunnyResult envelope

    @Test
    fun `err mirrors the error's status message and terminality`() {
        val err = BunnyResult.Err(BunnyError.Auth(403, "Forbidden"))

        assertEquals(403, err.httpStatus)
        assertEquals("Forbidden", err.message)
        assertTrue(err.isTerminal)
    }

    @Test
    fun `getOrNull and errorOrNull unwrap the matching side only`() {
        val ok: BunnyResult<Int> = BunnyResult.Ok(7)
        val err: BunnyResult<Int> = BunnyResult.Err(BunnyError.Network("down"))

        assertEquals(7, ok.getOrNull())
        assertNull(ok.errorOrNull())
        assertNull(err.getOrNull())
        assertEquals(BunnyError.Network("down"), err.errorOrNull())
    }

    @Test
    fun `map transforms ok and passes err through untouched`() {
        val ok: BunnyResult<Int> = BunnyResult.Ok(21)
        val err: BunnyResult<Int> = BunnyResult.Err(BunnyError.NotFound("Not Found"))

        assertEquals(BunnyResult.Ok(42), ok.map { it * 2 })
        assertEquals(err, err.map { it * 2 })
    }

    @Test
    fun `fold applies the matching side`() {
        val ok: BunnyResult<String> = BunnyResult.Ok("value")
        val err: BunnyResult<String> = BunnyResult.Err(BunnyError.Http(500, "boom"))

        assertEquals("value", ok.fold(onOk = { it }, onErr = { "error" }))
        assertEquals("boom", err.fold(onOk = { it }, onErr = { it.message }))
    }

    // endregion
}
