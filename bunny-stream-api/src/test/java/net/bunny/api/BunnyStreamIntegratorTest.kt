package net.bunny.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BunnyStreamIntegratorTest {

    @Test
    fun `valid integrator renders as name slash version`() {
        val integrator = BunnyStreamIntegrator("bunny-stream-react-native", "0.1.1")
        assertEquals("bunny-stream-react-native/0.1.1", integrator.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank name is rejected`() {
        BunnyStreamIntegrator("", "1.0.0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank version is rejected`() {
        BunnyStreamIntegrator("name", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace in name is rejected`() {
        BunnyStreamIntegrator("my integrator", "1.0.0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace in version is rejected`() {
        BunnyStreamIntegrator("name", "1 0 0")
    }

    @Test
    fun `config without integrator keeps native user agent`() {
        val config = BunnyStreamConfig("key", 1L)
        assertEquals(BuildConfig.USER_AGENT, config.userAgent)
        assertNull(config.integrator)
    }

    @Test
    fun `config with integrator appends suffix`() {
        val config = BunnyStreamConfig(
            "key",
            1L,
            integrator = BunnyStreamIntegrator("bunny-stream-react-native", "0.1.1"),
        )
        assertEquals("${BuildConfig.USER_AGENT} bunny-stream-react-native/0.1.1", config.userAgent)
    }
}
