package net.bunny.bunnystreamcameraupload.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These URLs get logged, and `Log.d` survives into release builds, so a credential printed here
 * ends up on the user's device. Each test asserts the secret is *absent*, not merely shortened —
 * a redaction that leaves enough characters to reconstruct the key is no redaction.
 */
class RedactSecretsTest {

    @Test
    fun `the library access key does not survive redaction`() {
        val redacted = VOD_INGEST.redactSecrets()

        assertFalse("the key must not appear anywhere", redacted.contains(ACCESS_KEY))
        assertFalse(redacted.contains(ACCESS_KEY.substring(ACCESS_KEY.length - 8)))
    }

    @Test
    fun `redaction keeps what a support ticket needs`() {
        // Host, video and library survive: without them a log line cannot be tied to a recording.
        assertEquals(
            "rtmp://ingest.example.net/??vid=$VIDEO_ID&accessKey=79fa…&lib=694192",
            VOD_INGEST.redactSecrets(),
        )
    }

    @Test
    fun `a live stream key is dropped to its first characters`() {
        // Different shape: the live ingest URL carries the key as its last path segment.
        val redacted = "rtmp://ingest.example.net/live/$STREAM_KEY".redactSecrets()

        assertEquals("rtmp://ingest.example.net/live/019f…", redacted)
        assertFalse(redacted.contains(STREAM_KEY))
    }

    @Test
    fun `a parameter after the key is left alone`() {
        // The regex has to stop at the separator; swallowing the rest would hide the library id
        // and make the line useless.
        assertTrue(VOD_INGEST.redactSecrets().endsWith("&lib=694192"))
    }

    @Test
    fun `a url carrying no credential is left readable`() {
        assertEquals("rtmp://ingest.example.net/live/", "rtmp://ingest.example.net/live/".redactSecrets())
    }

    private companion object {
        const val ACCESS_KEY = "79fa25ad-b6dd-47ac-8a405b7c9e51-a9ed-4cc3"
        const val VIDEO_ID = "280896fc-7fe9-48c4-a7ca-d30cbda54e10"
        const val STREAM_KEY = "019f5bd8-90f3-7429-b452-296a732df321"
        const val VOD_INGEST =
            "rtmp://ingest.example.net/??vid=$VIDEO_ID&accessKey=$ACCESS_KEY&lib=694192"
    }
}
