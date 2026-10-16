package net.bunny.api

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.model.LiveStreamStatus
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
 * An app that only plays must not need the library's access key.
 *
 * Bunny serves every playback call from public `/play` endpoints that take the library id, the
 * video or stream id and, on a token-authenticated library, a signed token. Up to 4.1.x the SDK
 * could not even be initialised without a key, so a viewer-facing app had to ship one, and anyone
 * who unpacked the APK could manage the library with it.
 *
 * These tests pin what reaches the wire from an instance created without a key: playback goes out
 * unauthenticated, and everything else is refused on the device with a terminal error that names
 * the fix, instead of a 401 from the server or, worse, a transient error a polling loop retries
 * forever.
 */
class PlaybackOnlyInstanceTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        BunnyStreamApi.release()
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() {
        BunnyStreamApi.release()
        server.shutdown()
    }

    // region — creating one

    @Test
    fun `a config without a key is a playback-only config`() {
        val config = BunnyStreamConfig(libraryId = LIBRARY)

        assertNull(config.accessKey)
        assertTrue(config.isPlaybackOnly)
        assertFalse(BunnyStreamConfig(KEY, LIBRARY).isPlaybackOnly)
    }

    @Test
    fun `a playback-only config renders without pretending to have a key`() {
        val rendered = BunnyStreamConfig(libraryId = LIBRARY).toString()

        assertTrue(rendered, rendered.contains("accessKey=null"))
        assertTrue(rendered.contains("libraryId=$LIBRARY"))
    }

    @Test
    fun `the default instance can be initialised with a library id only`() {
        BunnyStreamApi.initialize(fakeContext(), LIBRARY)

        val instance = BunnyStreamApi.getInstance()
        assertTrue(instance.config.isPlaybackOnly)
        assertEquals(LIBRARY, instance.libraryId)
    }

    @Test
    fun `a null key through the three-argument initialize is a playback-only instance`() {
        // What a bridge passes when the key is optional on its side (React Native, Flutter), and
        // what 3.x code passed back when the parameter was nullable but crashed anyway.
        BunnyStreamApi.initialize(fakeContext(), null, LIBRARY)

        assertTrue(BunnyStreamApi.getInstance().config.isPlaybackOnly)
    }

    @Test
    fun `create has a playback-only form too`() {
        val instance = BunnyStreamApi.create(fakeContext(), LIBRARY)

        assertTrue(instance.config.isPlaybackOnly)
        instance.release()
    }

    // endregion

    // region — playback goes out, without a key

    @Test
    fun `video play data reaches the server without an AccessKey header`() {
        server.enqueue(json(VIDEO_PLAY_DATA))

        val result = runBlocking {
            playbackOnly().videoRepository.fetchVideoPlayData(LIBRARY, VIDEO_ID)
        }

        assertTrue("expected Ok, was $result", result is BunnyResult.Ok)
        val request = server.takeRequest()
        assertEquals("/library/$LIBRARY/videos/$VIDEO_ID/play", request.path)
        assertNull(request.getHeader("AccessKey"))
        assertEquals(BunnyCdn.REFERER, request.getHeader("Referer"))
    }

    @Test
    fun `player settings reach the server without an AccessKey header`() {
        // Player settings travel through the SDK's second HTTP stack (Ktor), which has a guard of
        // its own; this is the call every VOD playback makes before it starts.
        server.enqueue(json(VIDEO_PLAY_DATA))

        val result = runBlocking {
            playbackOnly().fetchPlayerSettings(LIBRARY, VIDEO_ID, token = "signed", expires = 42L)
        }

        assertTrue("expected Ok, was $result", result is BunnyResult.Ok)
        val request = server.takeRequest()
        assertEquals("/library/$LIBRARY/videos/$VIDEO_ID/play?token=signed&expires=42", request.path)
        assertNull(request.getHeader("AccessKey"))
    }

    @Test
    fun `live play data and the status poll both use the public endpoint`() {
        server.enqueue(json(LIVE_PLAY_DATA))
        server.enqueue(json(LIVE_PLAY_DATA))

        val sdk = playbackOnly()
        val playData = runBlocking { sdk.liveStreamRepository.fetchLiveStreamPlayData(LIBRARY, STREAM_ID) }
        val polled = runBlocking {
            sdk.liveStreamRepository.pollLiveStream(LIBRARY, STREAM_ID, token = "signed", expires = 42L)
        }

        assertTrue("expected Ok, was $playData", playData is BunnyResult.Ok)
        assertEquals(LiveStreamStatus.RUNNING, (polled as BunnyResult.Ok).value.status)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/library/$LIBRARY/live/$STREAM_ID/play", first.path)
        assertEquals("/library/$LIBRARY/live/$STREAM_ID/play?token=signed&expires=42", second.path)
        assertNull(first.getHeader("AccessKey"))
        assertNull(second.getHeader("AccessKey"))
    }

    @Test
    fun `the heatmap comes from the public endpoint, with the token`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain").setBody("100,64"))

        val result = runBlocking {
            playbackOnly().videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID, token = "signed", expires = 42L)
        }

        assertEquals(mapOf("0" to 100, "1" to 64), (result as BunnyResult.Ok).value)
        val request = server.takeRequest()
        assertEquals("/library/$LIBRARY/videos/$VIDEO_ID/play/heatmap?token=signed&expires=42", request.path)
        assertNull(request.getHeader("AccessKey"))
    }

    @Test
    fun `an empty public heatmap answer is an empty heatmap, not an error`() {
        // What Bunny actually answers for a video without heatmap data: 200, empty text/plain.
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain"))

        val result = runBlocking { playbackOnly().videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID) }

        assertEquals(emptyMap<String, Int>(), (result as BunnyResult.Ok).value)
    }

    @Test
    fun `the public heatmap request carries the Referer the play endpoints need`() {
        server.enqueue(MockResponse().setResponseCode(200))

        runBlocking { playbackOnly().videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID) }

        val request = server.takeRequest()
        assertEquals(BunnyCdn.REFERER, request.getHeader("Referer"))
        // Asked for JSON, the server wraps the values in a JSON string; plain text is the format.
        assertEquals("text/plain", request.getHeader("Accept"))
    }

    @Test
    fun `a public heatmap body that is not a list of numbers is a decode error`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>oops</html>"))

        val result = runBlocking { playbackOnly().videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID) }

        assertTrue("expected Decode, was $result", (result as BunnyResult.Err).error is BunnyError.Decode)
    }

    @Test
    fun `public heatmap failures keep their http status`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))

        val sdk = playbackOnly()
        val missing = runBlocking { sdk.videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID) }
        val down = runBlocking { sdk.videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID) }

        assertTrue((missing as BunnyResult.Err).error is BunnyError.NotFound)
        assertEquals(503, (down as BunnyResult.Err).error.httpStatus)
        assertFalse(down.error.isTerminal)
    }

    @Test
    fun `an instance with a key reads the heatmap from the public endpoint too`() {
        // Playback never depends on the management API, key or no key.
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain").setBody("100"))

        val result = runBlocking { withKey().videoRepository.fetchVideoHeatmap(LIBRARY, VIDEO_ID) }

        assertEquals(mapOf("0" to 100), (result as BunnyResult.Ok).value)
        assertEquals("/library/$LIBRARY/videos/$VIDEO_ID/play/heatmap", server.takeRequest().path)
    }

    // endregion

    // region — management is refused on the device

    @Test
    fun `listing videos is refused before it reaches the network`() {
        val result = runBlocking { playbackOnly().videoRepository.listVideos(LIBRARY) }

        assertRefusedLocally(result)
    }

    @Test
    fun `reading a live stream through the management api is refused before it reaches the network`() {
        val result = runBlocking { playbackOnly().liveStreamRepository.getLiveStream(LIBRARY, STREAM_ID) }

        assertRefusedLocally(result)
    }

    @Test
    fun `creating a live stream is refused before it reaches the network`() {
        val result = runBlocking {
            playbackOnly().liveStreamRepository.createLiveStream(
                LIBRARY,
                LiveStreamCreateRequest(title = "never sent"),
            )
        }

        assertRefusedLocally(result)
    }

    @Test
    fun `listing collections is refused before it reaches the network`() {
        val result = runBlocking { playbackOnly().collectionRepository.listCollections(LIBRARY) }

        assertRefusedLocally(result)
    }

    @Test
    fun `an instance with a key still authenticates management calls`() {
        server.enqueue(json("""{"totalItems":0,"currentPage":1,"itemsPerPage":10,"items":[]}"""))

        val result = runBlocking { withKey().videoRepository.listVideos(LIBRARY) }

        assertTrue("expected Ok, was $result", result is BunnyResult.Ok)
        assertEquals(KEY, server.takeRequest().getHeader("AccessKey"))
    }

    // endregion

    private fun assertRefusedLocally(result: BunnyResult<*>) {
        val error = (result as? BunnyResult.Err)?.error
        assertTrue("expected InvalidState, was $result", error is BunnyError.InvalidState)
        // Terminal, so the player's and the broadcaster's loops stop instead of retrying a call
        // that can never succeed on this instance.
        assertTrue(error!!.isTerminal)
        assertTrue(error.message, error.message.contains("access key"))
        assertTrue(error.message, error.message.contains("initialize(context, accessKey, libraryId)"))
        assertEquals("nothing may reach the server", 0, server.requestCount)
    }

    private fun playbackOnly(): StreamApi =
        BunnyStreamApi.create(fakeContext(), BunnyStreamConfig(libraryId = LIBRARY, baseApi = baseApi()))

    private fun withKey(): StreamApi =
        BunnyStreamApi.create(fakeContext(), BunnyStreamConfig(KEY, LIBRARY, baseApi = baseApi()))

    private fun baseApi(): String = server.url("/").toString().trimEnd('/')

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun fakeContext(): Context {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
        return context
    }

    private companion object {
        const val KEY = "8f2c1a94-0e3d-4b77-9a51-6c8de2f04b13"
        const val LIBRARY = 694192L
        const val VIDEO_ID = "6c12dcc5-e1ca-4c02-a62d-f4d21c9d1b5f"
        const val STREAM_ID = "01a085f1-2440-779f-a603-5acacaf8a710"

        val VIDEO_PLAY_DATA = """
            {"video":{"guid":"$VIDEO_ID","videoLibraryId":$LIBRARY,"title":"clip","status":4},
             "videoPlaylistUrl":"https://cdn.example.net/$VIDEO_ID/playlist.m3u8",
             "thumbnailUrl":"https://cdn.example.net/$VIDEO_ID/thumbnail.jpg",
             "captionsPath":"/$VIDEO_ID/captions/","seekPath":"/$VIDEO_ID/seek/",
             "controls":"play,progress","playbackSpeeds":"0.5,1,2","playerKeyColor":"#ff7755",
             "captionsFontSize":20,"captionsFontColor":"#fff","captionsBackground":"#000",
             "uiLanguage":"en","showHeatmap":false,"fontFamily":"rubik","enableDRM":false,
             "vastTagUrl":null}
        """.trimIndent()

        val LIVE_PLAY_DATA = """
            {"liveStream":{"guid":"$STREAM_ID","videoLibraryId":$LIBRARY,"title":"live","status":4,
             "recordVod":true},
             "videoPlaylistUrl":"https://cdn.example.net/live/$STREAM_ID/playlist.m3u8",
             "controls":"play","playbackSpeeds":"1"}
        """.trimIndent()
    }
}
