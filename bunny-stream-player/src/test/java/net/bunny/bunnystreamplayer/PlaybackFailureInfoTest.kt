package net.bunny.bunnystreamplayer

import android.net.StubUri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.drm.DrmSession
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * Pins the rule behind "Video is not available": any HTTP 403 counts as blocked wherever media3
 * buried it in the cause chain, nothing else does, and a broken chain can't hang the engine's
 * error callback.
 *
 * [HttpDataSource.InvalidResponseCodeException] is built for real — it is final and its only
 * constructor wants a [DataSpec]; [StubUri] is what makes that possible on the plain JVM.
 */
@OptIn(UnstableApi::class)
class PlaybackFailureInfoTest {

    @Test
    fun `finds the status on the throwable itself`() {
        assertEquals(403, httpError(403).httpStatusCode())
    }

    @Test
    fun `finds the status one level down`() {
        // The shape media3 actually raises: PlaybackException -> InvalidResponseCodeException.
        val error = PlaybackException(
            "Source error", httpError(403), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )
        assertEquals(403, error.httpStatusCode())
    }

    @Test
    fun `finds the status three levels down`() {
        val error = IOException("d0", IOException("d1", IOException("d2", httpError(403))))
        assertEquals(403, error.httpStatusCode())
    }

    @Test
    fun `returns null without an http cause`() {
        val error = PlaybackException(
            "Decoder init failed",
            IllegalStateException("codec"),
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        )
        assertNull(error.httpStatusCode())
        assertNull(IOException("plain").httpStatusCode())
    }

    @Test
    fun `gives up after ten links`() {
        // Depth 9 is the last link inspected; a status at depth 10 is out of reach by design.
        assertEquals(403, buried(httpError(403), depth = 9).httpStatusCode())
        assertNull(buried(httpError(403), depth = 10).httpStatusCode())
    }

    @Test
    fun `terminates on a cyclic cause chain`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b) // a -> b -> a
        assertNull(a.httpStatusCode())
    }

    @Test
    fun `isBlocked is true for any 403 regardless of error code`() {
        assertTrue(info(httpStatus = 403).isBlocked)
        assertFalse(info(httpStatus = 401).isBlocked)
        assertFalse(info(httpStatus = 404).isBlocked)
        assertFalse(info(httpStatus = null).isBlocked)
        // A 403 media3 filed under another code (a refused license) is still blocked: per Bunny's
        // decision every 403 shows the generic copy, whatever code carries it.
        assertTrue(
            info(
                httpStatus = 403,
                errorCode = PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            ).isBlocked,
        )
    }

    @Test
    fun `a 403 on the license request is a blocked stream too`() {
        // The shape media3 raises for a refused Widevine license: PlaybackException(DRM code) ->
        // DrmSessionException -> MediaDrmCallbackException -> InvalidResponseCodeException(403).
        // Bunny's rule is "whenever 403", so the status alone decides — this reads as blocked.
        val license = DataSpec(StubUri("https://video.bunnycdn.com/WidevineLicense/1/v"))
        val error = PlaybackException(
            "DRM license acquisition failed",
            DrmSession.DrmSessionException(
                MediaDrmCallbackException(license, license.uri, emptyMap(), 0L, httpError(403)),
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            ),
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        )

        var resolved = false
        val info = PlaybackFailureInfo.from(error) { resolved = true; "Video is not available" }
        assertEquals(403, info.httpStatus)
        assertTrue(info.isBlocked)
        assertEquals("Video is not available", info.userMessage)
        assertTrue(resolved)
    }

    @Test
    fun `from swaps in the viewer copy for a blocked stream only`() {
        val blocked = PlaybackFailureInfo.from(
            PlaybackException(
                "Source error", httpError(403), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            ),
        ) { "Video is not available" }
        assertTrue(blocked.isBlocked)
        assertEquals(403, blocked.httpStatus)
        assertEquals("ERROR_CODE_IO_BAD_HTTP_STATUS: Source error", blocked.rawMessage)
        assertEquals("Video is not available", blocked.userMessage)

        var resolved = false
        val notFound = PlaybackFailureInfo.from(
            PlaybackException(
                "Source error", httpError(404), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            ),
        ) { resolved = true; "Video is not available" }
        assertFalse(notFound.isBlocked)
        assertEquals(404, notFound.httpStatus)
        // Anything but a 403 keeps today's message byte for byte and never touches resources.
        assertEquals(notFound.rawMessage, notFound.userMessage)
        assertFalse(resolved)
    }

    @Test
    fun `a dns sinkhole connection failure is a blocked stream`() {
        // The shape media3 raises when Bunny's "Blocked countries" rejects the host at the DNS
        // level: the CDN name resolves to 127.0.0.1, the socket connect is refused and nothing
        // ever answers with a status code.
        val error = PlaybackException(
            "Source error",
            HttpDataSource.HttpDataSourceException(
                connectError("vz-test.b-cdn.net/127.0.0.1:443"),
                DataSpec(StubUri("https://vz-test.b-cdn.net/s/playlist.m3u8")),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            ),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )

        var resolved = false
        val info = PlaybackFailureInfo.from(error) { resolved = true; "Video is not available" }
        assertNull(info.httpStatus)
        assertEquals("127.0.0.1", info.sinkholeAddress)
        assertTrue(info.isBlocked)
        assertEquals("Video is not available", info.userMessage)
        assertTrue(resolved)
    }

    @Test
    fun `a connection failure to a real address is not a blocked stream`() {
        // A genuine outage: the host resolved to a public edge but the socket never came up. It
        // must stay transient, so live keeps retrying and the raw message is kept.
        val error = PlaybackException(
            "Source error",
            connectError("vz-test.b-cdn.net/185.59.220.199:443"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )

        var resolved = false
        val info = PlaybackFailureInfo.from(error) { resolved = true; "Video is not available" }
        assertNull(info.sinkholeAddress)
        assertFalse(info.isBlocked)
        assertEquals(info.rawMessage, info.userMessage)
        assertFalse(resolved)
    }

    @Test
    fun `sinkholeAddress recognises the unspecified and ipv6 loopback forms`() {
        assertEquals("0.0.0.0", connectError("h/0.0.0.0:443").sinkholeAddress())
        assertEquals("::1", connectError("h/[::1]:443").sinkholeAddress())
        assertEquals("::1", connectError("h/::1:443").sinkholeAddress())
        assertEquals(
            "127.0.0.53",
            buried(connectError("h/127.0.0.53:443"), depth = 3).sinkholeAddress(),
        )
    }

    @Test
    fun `sinkholeAddress is null without a connect failure or without an address`() {
        assertNull(UnknownHostException("vz-test.b-cdn.net").sinkholeAddress())
        assertNull(ConnectException("Connection refused").sinkholeAddress())
        assertNull(httpError(403).sinkholeAddress())
        assertNull(IOException("plain").sinkholeAddress())
    }

    // region — Fixtures

    /** The message Android's OkHttp puts on a refused socket connect. */
    private fun connectError(socketAddress: String) =
        ConnectException("Failed to connect to $socketAddress")

    private fun httpError(status: Int) = HttpDataSource.InvalidResponseCodeException(
        status,
        null,
        null,
        emptyMap(),
        DataSpec(StubUri("https://vz-test.b-cdn.net/live/s/playlist.m3u8")),
        ByteArray(0),
    )

    /** Wraps [cause] in [depth] plain exceptions so it sits exactly [depth] links from the top. */
    private fun buried(cause: Throwable, depth: Int): Throwable =
        (1..depth).fold(cause) { inner, i -> RuntimeException("link $i", inner) }

    private fun info(
        httpStatus: Int?,
        errorCode: Int = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    ) = PlaybackFailureInfo(
        errorCode = errorCode,
        errorCodeName = PlaybackException.getErrorCodeName(errorCode),
        httpStatus = httpStatus,
        rawMessage = "ERROR_CODE_IO_BAD_HTTP_STATUS: Source error",
        userMessage = "ERROR_CODE_IO_BAD_HTTP_STATUS: Source error",
    )

    // endregion
}
