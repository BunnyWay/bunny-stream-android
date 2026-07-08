package net.bunny.api.livestream.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the thumbnail URL helpers backing the two thumbnail fixes:
 *  - [setThumbnailRequestUrl] — "set thumbnail by URL" (was broken via the generated client).
 *  - [cdnBaseFromPlaybackUrl] + [resolveThumbnailUrl] — turn the Get-Thumbnails endpoint's
 *    relative paths into absolute, loadable CDN URLs.
 */
class ThumbnailUrlsTest {

    // region — cdnBaseFromPlaybackUrl

    @Test fun `cdnBase extracts scheme and host from a live playback url`() {
        assertEquals(
            "https://vz-3e957751-a9d.b-cdn.net",
            cdnBaseFromPlaybackUrl("https://vz-3e957751-a9d.b-cdn.net/live/abc/live.m3u8"),
        )
    }

    @Test fun `cdnBase keeps an explicit port`() {
        assertEquals(
            "https://cdn.example.com:8443",
            cdnBaseFromPlaybackUrl("https://cdn.example.com:8443/live/abc/live.m3u8"),
        )
    }

    @Test fun `cdnBase is null for blank or relative input`() {
        assertNull(cdnBaseFromPlaybackUrl(null))
        assertNull(cdnBaseFromPlaybackUrl(""))
        assertNull(cdnBaseFromPlaybackUrl("abc/thumbs/x.jpg"))
    }

    // region — resolveThumbnailUrl

    @Test fun `resolve prepends base to a relative thumbnail path`() {
        assertEquals(
            "https://vz-x.b-cdn.net/abc/thumbs/20260707/t1.jpg",
            resolveThumbnailUrl("https://vz-x.b-cdn.net", "abc/thumbs/20260707/t1.jpg"),
        )
    }

    @Test fun `resolve collapses the join to a single slash`() {
        assertEquals(
            "https://vz-x.b-cdn.net/abc/t.jpg",
            resolveThumbnailUrl("https://vz-x.b-cdn.net/", "/abc/t.jpg"),
        )
    }

    @Test fun `resolve passes through an already-absolute url`() {
        assertEquals(
            "https://cdn.example.com/a.jpg",
            resolveThumbnailUrl("https://vz-x.b-cdn.net", "https://cdn.example.com/a.jpg"),
        )
    }

    @Test fun `resolve returns null for blank url`() {
        assertNull(resolveThumbnailUrl("https://vz-x.b-cdn.net", null))
        assertNull(resolveThumbnailUrl("https://vz-x.b-cdn.net", "   "))
    }

    @Test fun `resolve falls back to the relative path when base is unknown`() {
        assertEquals("abc/t.jpg", resolveThumbnailUrl(null, "abc/t.jpg"))
    }

    // region — setThumbnailRequestUrl

    @Test fun `setThumbnailRequestUrl builds the path and carries the url as a query param`() {
        val target = "https://ex.com/img a.jpg?x=1"
        val built = setThumbnailRequestUrl("https://video.bunnycdn.com", 694192L, "abc-123", target)
        val parsed = built.toHttpUrl()

        assertEquals("/library/694192/live/abc-123/thumbnail", parsed.encodedPath)
        // The value round-trips through OkHttp's encoding back to the original string.
        assertEquals(target, parsed.queryParameter("thumbnailUrl"))
    }
}
