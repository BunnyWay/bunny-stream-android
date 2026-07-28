package net.bunny.api.upload.model

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.InputStream

/**
 * Streams [inputStream] as the request body.
 *
 * [contentLength] is passed in rather than taken from `inputStream.available()`: `available()`
 * only promises what can be read without blocking, so for a large or slow-backed stream it
 * under-reports the real size — and since it is what upload progress is divided by, the percentage
 * would race past 100 and the request would declare a length shorter than the body it sends.
 * The caller has the authoritative size from the content resolver.
 */
internal class StreamContent(
    private val inputStream: InputStream,
    override val contentLength: Long,
) : OutgoingContent.ReadChannelContent() {

    override fun readFrom(): ByteReadChannel = inputStream.toByteReadChannel()

    override val contentType = ContentType.Application.OctetStream
}
