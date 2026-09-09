package net.bunny.bunnystreamcameraupload.util

private const val ACCESS_KEY_PARAM = "accessKey="

/** `accessKey=<value>`, up to the next parameter separator or the end of the string. */
private val ACCESS_KEY = Regex("${ACCESS_KEY_PARAM}([^&]*)")

/** How much of a credential survives redaction — enough to tell two of them apart, no more. */
private const val KEPT_CHARS = 4

/**
 * Strips the credential out of an RTMP ingest URL before it reaches a log.
 *
 * These URLs carry a credential: for a live broadcast the stream key is the last path segment,
 * and for a camera upload the library's access key rides along as an `accessKey=` parameter.
 * Neither belongs in a log.
 *
 * What survives is what a support ticket actually needs: the host, the video and library ids, and
 * the first few characters of the key so two of them can be told apart.
 */
internal fun String.redactSecrets(): String {
    if (contains(ACCESS_KEY_PARAM)) {
        return replace(ACCESS_KEY) { "$ACCESS_KEY_PARAM${it.groupValues[1].take(KEPT_CHARS)}…" }
    }

    // Otherwise the last path segment is the stream key. An empty one means there is no key to
    // hide, and marking it elided would claim something was removed when nothing was.
    val key = substringAfterLast('/')
    if (key.isEmpty()) return this
    return substringBeforeLast('/') + "/" + key.take(KEPT_CHARS) + "…"
}
