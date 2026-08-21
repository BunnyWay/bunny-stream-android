package net.bunny.bunnystreamcameraupload.data

import com.pedro.common.UrlParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The VOD ingest server accepts a publish only as app="ingest" with stream name
 * "?vid=...&accessKey=...&lib=..." (leading '?' required) - verified against the live ingest
 * host: app="ingest?" or a stream name without the leading '?' are both rejected with
 * "Invalid stream data". These tests run the built URL through RootEncoder's own [UrlParser]
 * (the code that actually splits it on connect) so a URL-format regression fails here instead
 * of silently producing empty recordings.
 */
class VodIngestUrlTest {

    private val validSchemes = arrayOf("rtmp", "rtmps", "rtmpt", "rtmpts")

    @Test
    fun `built url parses to app 'ingest' and stream name with leading question mark`() {
        val url = DefaultRecordingRepository.buildVodIngestUrl(
            rtmpEndpoint = "rtmp://ingest.example.net/ingest",
            videoGuid = "a522aa26-1fdc-45a5-ba48-427547314210",
            accessKey = "test-access-key",
            libraryId = 694192L,
        )

        val parsed = UrlParser.parse(url, validSchemes)

        assertEquals("ingest", parsed.getAppName())
        assertEquals(
            "?vid=a522aa26-1fdc-45a5-ba48-427547314210&accessKey=test-access-key&lib=694192",
            parsed.getStreamName(),
        )
        assertEquals("rtmp://ingest.example.net/ingest", parsed.getTcUrl())
    }

    @Test
    fun `trailing slash on the endpoint does not double up`() {
        val url = DefaultRecordingRepository.buildVodIngestUrl(
            rtmpEndpoint = "rtmp://ingest.example.net/ingest/",
            videoGuid = "guid",
            accessKey = "key",
            libraryId = 1L,
        )

        val parsed = UrlParser.parse(url, validSchemes)

        assertEquals("ingest", parsed.getAppName())
        assertEquals("?vid=guid&accessKey=key&lib=1", parsed.getStreamName())
    }
}
