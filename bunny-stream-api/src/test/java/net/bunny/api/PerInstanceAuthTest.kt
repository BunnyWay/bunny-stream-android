package net.bunny.api

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Authentication used to be process-wide: the generated client reads its key from a static map on
 * `ApiClient`, and the SDK filled that map in on every `initialize`. Two libraries could not be
 * addressed at once — whichever instance was created last authenticated for all of them, silently.
 *
 * The key now rides on an interceptor attached to each instance's own OkHttp client, so these tests
 * assert what actually reaches the wire rather than what the SDK intended to send.
 */
class PerInstanceAuthTest {

    private lateinit var firstLibrary: MockWebServer
    private lateinit var secondLibrary: MockWebServer

    @Before
    fun startServers() {
        BunnyStreamApi.release()
        firstLibrary = MockWebServer().apply { start() }
        secondLibrary = MockWebServer().apply { start() }
    }

    @After
    fun stopServers() {
        BunnyStreamApi.release()
        firstLibrary.shutdown()
        secondLibrary.shutdown()
    }

    @Test
    fun `each instance authenticates with its own key`() {
        firstLibrary.enqueue(jsonResponse())
        secondLibrary.enqueue(jsonResponse())

        val first = instanceFor(firstLibrary, KEY_A, LIBRARY_A)
        val second = instanceFor(secondLibrary, KEY_B, LIBRARY_B)

        // Interleaved on purpose: creating the second instance must not re-point the first, which
        // is exactly what the shared static key did.
        runBlocking { first.videoRepository.listVideos(LIBRARY_A) }
        runBlocking { second.videoRepository.listVideos(LIBRARY_B) }

        assertEquals(KEY_A, firstLibrary.takeRequest().getHeader("AccessKey"))
        assertEquals(KEY_B, secondLibrary.takeRequest().getHeader("AccessKey"))
    }

    @Test
    fun `an instance keeps its key after another one is created`() {
        firstLibrary.enqueue(jsonResponse())
        firstLibrary.enqueue(jsonResponse())

        val first = instanceFor(firstLibrary, KEY_A, LIBRARY_A)
        runBlocking { first.videoRepository.listVideos(LIBRARY_A) }
        val beforeSecondExists = firstLibrary.takeRequest().getHeader("AccessKey")

        instanceFor(secondLibrary, KEY_B, LIBRARY_B)
        runBlocking { first.videoRepository.listVideos(LIBRARY_A) }
        val afterSecondExists = firstLibrary.takeRequest().getHeader("AccessKey")

        assertEquals(KEY_A, beforeSecondExists)
        assertEquals(KEY_A, afterSecondExists)
        assertNotEquals(KEY_B, afterSecondExists)
    }

    @Test
    fun `the default instance authenticates like any other`() {
        firstLibrary.enqueue(jsonResponse())

        BunnyStreamApi.initialize(
            fakeContext(),
            BunnyStreamConfig(KEY_A, LIBRARY_A, baseApi = firstLibrary.url("/").toString().trimEnd('/')),
        )
        runBlocking { BunnyStreamApi.getInstance().videoRepository.listVideos(LIBRARY_A) }

        assertEquals(KEY_A, firstLibrary.takeRequest().getHeader("AccessKey"))
    }

    @Test
    fun `an instance talks only to the host it was configured with`() {
        firstLibrary.enqueue(jsonResponse())

        val first = instanceFor(firstLibrary, KEY_A, LIBRARY_A)
        runBlocking { first.videoRepository.listVideos(LIBRARY_A) }

        // baseApi used to be a compile-time constant, so pointing an instance at another host —
        // a staging deployment, or a second server in a test like this one — was impossible.
        assertEquals(1, firstLibrary.requestCount)
        assertEquals(0, secondLibrary.requestCount)
    }

    @Test
    fun `every request carries the SDK user agent alongside the key`() {
        firstLibrary.enqueue(jsonResponse())

        val first = instanceFor(firstLibrary, KEY_A, LIBRARY_A)
        runBlocking { first.videoRepository.listVideos(LIBRARY_A) }

        val request = firstLibrary.takeRequest()
        assertEquals(KEY_A, request.getHeader("AccessKey"))
        assertEquals(BuildConfig.USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun `integrator suffix is appended to the user agent`() {
        firstLibrary.enqueue(jsonResponse())

        val integrator = BunnyStreamIntegrator(name = "bunny-stream-react-native", version = "0.1.1")
        val first = BunnyStreamApi.create(
            fakeContext(),
            BunnyStreamConfig(
                accessKey = KEY_A,
                libraryId = LIBRARY_A,
                baseApi = firstLibrary.url("/").toString().trimEnd('/'),
                integrator = integrator,
            ),
        )
        runBlocking { first.videoRepository.listVideos(LIBRARY_A) }

        val request = firstLibrary.takeRequest()
        val userAgent = request.getHeader("User-Agent")!!
        assertTrue(userAgent.startsWith(BuildConfig.USER_AGENT))
        assertTrue(userAgent.endsWith("bunny-stream-react-native/0.1.1"))
    }

    @Test
    fun `each instance keeps its own integrator suffix`() {
        firstLibrary.enqueue(jsonResponse())
        secondLibrary.enqueue(jsonResponse())

        val first = BunnyStreamApi.create(
            fakeContext(),
            BunnyStreamConfig(
                accessKey = KEY_A,
                libraryId = LIBRARY_A,
                baseApi = firstLibrary.url("/").toString().trimEnd('/'),
                integrator = BunnyStreamIntegrator("integrator-a", "1.0.0"),
            ),
        )
        val second = BunnyStreamApi.create(
            fakeContext(),
            BunnyStreamConfig(
                accessKey = KEY_B,
                libraryId = LIBRARY_B,
                baseApi = secondLibrary.url("/").toString().trimEnd('/'),
                integrator = BunnyStreamIntegrator("integrator-b", "2.0.0"),
            ),
        )

        runBlocking {
            first.videoRepository.listVideos(LIBRARY_A)
            second.videoRepository.listVideos(LIBRARY_B)
        }

        assertTrue(firstLibrary.takeRequest().getHeader("User-Agent")!!.endsWith("integrator-a/1.0.0"))
        assertTrue(secondLibrary.takeRequest().getHeader("User-Agent")!!.endsWith("integrator-b/2.0.0"))
    }

    private fun instanceFor(server: MockWebServer, accessKey: String, libraryId: Long): StreamApi =
        BunnyStreamApi.create(
            fakeContext(),
            BunnyStreamConfig(accessKey, libraryId, baseApi = server.url("/").toString().trimEnd('/')),
        )

    private fun jsonResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"totalItems":0,"currentPage":1,"itemsPerPage":10,"items":[]}""")

    private fun fakeContext(): Context {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
        return context
    }

    private companion object {
        const val KEY_A = "8f2c1a94-0e3d-4b77-9a51-6c8de2f04b13"
        const val KEY_B = "b41e7d62-95af-4c08-8e33-1d7a6f9c2054"
        const val LIBRARY_A = 694192L
        const val LIBRARY_B = 111222L
    }
}
