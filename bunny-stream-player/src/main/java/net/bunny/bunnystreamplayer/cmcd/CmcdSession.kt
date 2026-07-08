package net.bunny.bunnystreamplayer.cmcd

import android.net.Uri
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CMCD `st` (stream type). Mirrors the iOS `CMCDSession.StreamType`: a DVR-enabled live stream is
 * reported as [EVENT], a plain live stream as [LIVE], and a recording/VOD as [VOD].
 */
internal enum class CmcdStreamType(val code: String) {
    LIVE("l"),
    VOD("v"),
    EVENT("e"),
}

/** CMCD `ot` (object type) derived from the request URL. */
internal enum class CmcdObjectType(val code: String) {
    MANIFEST("m"),
    VIDEO("v"),
    AUDIO("a"),
    INIT("i"),
    OTHER("o"),
}

/** Point-in-time player buffer state feeding the dynamic CMCD keys (`bl`, `bs`). */
internal data class CmcdPlayerSnapshot(
    val bufferLengthMs: Long = 0L,
    val bufferStarved: Boolean = false,
)

/**
 * Builds the CMCD **v2** `CMCD=` query payload for a single playback session. The wire-string logic
 * is pure (the [Uri] entry point delegates to a `*ForSegment` helper that takes just the last path
 * segment), so it is fully unit-testable. The CMCD field set matches the iOS `CMCDHeaderBuilder`
 * (`sid`, `cid`, `sf=h`, `st`, `v=2`, `bl`, `su`, `bs`, `ot`; only `sid`/`cid` quoted); keys are
 * flattened and sorted alphabetically per CTA-5004 query encoding.
 *
 * @param contentId the CMCD `cid` — the video/stream guid.
 * @param streamType the CMCD `st`. See [CmcdStreamType].
 * @param sessionId the CMCD `sid`. Defaults to a random UUID, stable for the session.
 * @param snapshotProvider supplies the live buffer state at request time. Must be thread-safe: it is
 *   invoked from ExoPlayer loader threads.
 */
internal class CmcdSession(
    val contentId: String,
    val streamType: CmcdStreamType,
    val sessionId: String = UUID.randomUUID().toString(),
    private val snapshotProvider: () -> CmcdPlayerSnapshot = { CmcdPlayerSnapshot() },
) {
    /** `su` is emitted until the first media segment (non-manifest) request is seen. */
    private val startup = AtomicBoolean(true)

    /**
     * Returns [uri] with a `CMCD=` query parameter carrying the v2 payload. Source URIs never carry
     * CMCD, and [ResolvingDataSource][androidx.media3.datasource.ResolvingDataSource] resolves each
     * DataSpec once (redirects are followed inside the HTTP data source, not re-resolved), so a plain
     * append is idempotent.
     */
    fun appendCmcdQuery(uri: Uri): Uri {
        val value = queryValueForSegment(uri.lastPathSegment)
        return uri.buildUpon().appendQueryParameter(QUERY_KEY, value).build()
    }

    /**
     * The `CMCD=` value for a request whose last path segment is [lastSegment]. Advances the startup
     * state when the segment is a media segment, so `su` appears on the initial manifests and the
     * first segment, then drops — matching iOS.
     */
    internal fun queryValueForSegment(lastSegment: String?): String {
        val ot = objectTypeForSegment(lastSegment)
        val value = queryValue(ot, snapshotProvider())
        if (ot != CmcdObjectType.MANIFEST) startup.set(false)
        return value
    }

    /**
     * The value of the `CMCD=` query argument: every key as `key=value` (or a bare token for
     * booleans), sorted alphabetically by key and comma-joined. Percent-encoding is applied by the
     * URI builder.
     */
    internal fun queryValue(ot: CmcdObjectType, snapshot: CmcdPlayerSnapshot): String {
        val pairs = buildList {
            add("bl" to "bl=${snapshot.bufferLengthMs}")
            if (snapshot.bufferStarved) add("bs" to "bs")
            add("cid" to "cid=\"$contentId\"")
            add("ot" to "ot=${ot.code}")
            add("sf" to "sf=h")
            add("sid" to "sid=\"$sessionId\"")
            add("st" to "st=${streamType.code}")
            if (startup.get()) add("su" to "su")
            add("v" to "v=2")
        }
        return pairs.sortedBy { it.first }.joinToString(",") { it.second }
    }

    internal fun objectTypeForSegment(lastSegment: String?): CmcdObjectType {
        val last = lastSegment?.lowercase() ?: return CmcdObjectType.OTHER
        return when (last.substringAfterLast('.', "")) {
            "m3u8" -> CmcdObjectType.MANIFEST
            "ts", "m4s" -> CmcdObjectType.VIDEO
            "aac", "m4a" -> CmcdObjectType.AUDIO
            "mp4" -> if (last.contains("init")) CmcdObjectType.INIT else CmcdObjectType.VIDEO
            else -> CmcdObjectType.OTHER
        }
    }

    private companion object {
        const val QUERY_KEY = "CMCD"
    }
}
