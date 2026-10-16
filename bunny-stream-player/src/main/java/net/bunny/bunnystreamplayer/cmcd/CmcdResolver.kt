package net.bunny.bunnystreamplayer.cmcd

import android.annotation.SuppressLint
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/**
 * Appends a CMCD v2 `CMCD=` query parameter to every media request (manifest + segments) as
 * ExoPlayer opens it. Wrapping the shared HTTP data source with a [ResolvingDataSource] covers both
 * VOD and live, which share the same playback path. Query is the only transmission mode — the SDK
 * does not expose a toggle (mirrors the iOS player's fixed-transport design).
 *
 * Only requests to [contentHost] (the host the manifest is served from) are tagged. Ad creatives
 * load through the same data source, and they live on the ad server's CDN: the CMCD payload names
 * the Bunny video and session, which is not the ad server's business, and an ad request counted as
 * the first media segment would end the `su` (startup) flag before the content even started.
 */
@SuppressLint("UnsafeOptInUsageError")
internal class CmcdResolver(
    private val session: CmcdSession,
    private val contentHost: String? = null,
) : ResolvingDataSource.Resolver {

    @SuppressLint("UnsafeOptInUsageError")
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (!isCmcdHost(dataSpec.uri.host, contentHost)) return dataSpec
        return dataSpec.withUri(session.appendCmcdQuery(dataSpec.uri))
    }
}

/**
 * Whether a request to [requestHost] carries CMCD. An unknown [contentHost] keeps the old behaviour
 * of tagging everything, so a manifest URL that does not parse never switches CMCD off.
 */
internal fun isCmcdHost(requestHost: String?, contentHost: String?): Boolean =
    contentHost.isNullOrEmpty() || requestHost.equals(contentHost, ignoreCase = true)
