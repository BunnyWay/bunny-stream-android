package net.bunny.api.livestream.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI

/**
 * Extracts the CDN base (`scheme://host[:port]`) from a Bunny playback URL, e.g.
 * `https://vz-xxx.b-cdn.net/live/<id>/live.m3u8` → `https://vz-xxx.b-cdn.net`.
 * Returns null when the URL is blank or has no scheme/host.
 */
internal fun cdnBaseFromPlaybackUrl(playbackUrl: String?): String? {
    if (playbackUrl.isNullOrBlank()) return null
    return try {
        val uri = URI(playbackUrl)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port != -1) ":${uri.port}" else ""
        "$scheme://$host$port"
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolves a possibly-relative live-stream thumbnail path against [cdnBase].
 *
 * The Get-Thumbnails endpoint returns paths *relative* to the library CDN host
 * (e.g. `<streamId>/thumbs/…/x.jpg`), which are not loadable on their own.
 * - blank/null → null
 * - already absolute (`http(s)://`) → returned unchanged
 * - relative + known [cdnBase] → `"$cdnBase/$path"` (collapsing the join to a single slash)
 * - relative + unknown [cdnBase] → returned unchanged (graceful fallback)
 */
internal fun resolveThumbnailUrl(cdnBase: String?, url: String?): String? {
    val path = url?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    if (path.startsWith("http://", ignoreCase = true) ||
        path.startsWith("https://", ignoreCase = true)
    ) {
        return path
    }
    val base = cdnBase?.trim()?.trimEnd('/')?.takeUnless { it.isEmpty() } ?: return path
    return "$base/${path.trimStart('/')}"
}

/**
 * Builds the "Set Thumbnail" request URL: `{baseUrl}/library/{lib}/live/{stream}/thumbnail`
 * with the target image URL carried as the `thumbnailUrl` query parameter (OkHttp encodes it).
 */
internal fun setThumbnailRequestUrl(
    baseUrl: String,
    libraryId: Long,
    streamId: String,
    thumbnailUrl: String,
): String =
    "$baseUrl/library/$libraryId/live/$streamId/thumbnail".toHttpUrl()
        .newBuilder()
        .addQueryParameter("thumbnailUrl", thumbnailUrl)
        .build()
        .toString()
