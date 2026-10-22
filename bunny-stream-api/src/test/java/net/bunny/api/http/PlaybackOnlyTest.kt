package net.bunny.api.http

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.runBlocking
import net.bunny.api.BunnyCdn
import net.bunny.api.ktor.initHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The allow-list a playback-only instance lives by. Too narrow and a player breaks without a key;
 * too wide and a management call goes out unauthenticated and comes back as a 401 nobody explains.
 */
class PlaybackOnlyTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `the public playback endpoints are allowed`() {
        listOf(
            "/library/694192/videos/6c12dcc5-e1ca-4c02-a62d-f4d21c9d1b5f/play",
            "/library/694192/videos/6c12dcc5-e1ca-4c02-a62d-f4d21c9d1b5f/play/heatmap",
            "/library/694192/live/01a085f1-2440-779f-a603-5acacaf8a710/play",
            // A base URL with a path prefix still matches on the tail.
            "/stream-proxy/library/1/videos/v/play",
        ).forEach { assertTrue(it, isPublicPlaybackPath(it)) }
    }

    @Test
    fun `everything else is management and is not allowed`() {
        listOf(
            "/library/1/videos",
            "/library/1/videos/v",
            "/library/1/videos/v/heatmap",
            "/library/1/videos/v/play/extra",
            "/library/1/videos/v/playback",
            "/library/1/live",
            "/library/1/live/s",
            "/library/1/live/s/status",
            "/library/1/live/s/play/heatmap",
            "/library/1/live/s/start",
            "/library/1/collections",
            "/play",
            "/videos/v/play",
        ).forEach { assertFalse(it, isPublicPlaybackPath(it)) }
    }

    @Test
    fun `the ktor client without a key sends playback calls unauthenticated`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = initHttpClient(accessKey = null)

        runBlocking { client.get(server.url("/library/1/videos/v/play").toString()) }
        client.close()

        val request = server.takeRequest()
        assertNull(request.getHeader("AccessKey"))
        assertEquals(BunnyCdn.REFERER, request.getHeader("Referer"))
    }

    @Test
    fun `the ktor client sends the Referer only to the playback endpoints`() {
        // Same rule as the OkHttp client: the referrer is for /play, not for the management API.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = initHttpClient(accessKey = "key")

        runBlocking {
            client.get(server.url("/library/1/videos/v/play").toString())
            client.get(server.url("/library/1/videos/v").toString())
        }
        client.close()

        assertEquals(BunnyCdn.REFERER, server.takeRequest().getHeader("Referer"))
        assertNull(server.takeRequest().getHeader("Referer"))
    }

    @Test
    fun `the ktor client without a key refuses a management call before it is sent`() {
        val client = initHttpClient(accessKey = null)

        val error = runCatching<HttpResponse> {
            runBlocking { client.get(server.url("/library/1/videos/v").toString()) }
        }.exceptionOrNull()
        client.close()

        assertTrue("expected MissingAccessKeyException, was $error", error is MissingAccessKeyException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the ktor client with a key authenticates every call`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = initHttpClient(accessKey = "key")

        runBlocking { client.get(server.url("/library/1/videos/v").toString()) }
        client.close()

        assertEquals("key", server.takeRequest().getHeader("AccessKey"))
    }
}
