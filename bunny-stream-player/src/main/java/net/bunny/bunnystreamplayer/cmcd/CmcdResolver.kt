package net.bunny.bunnystreamplayer.cmcd

import android.annotation.SuppressLint
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/**
 * Appends a CMCD v2 `CMCD=` query parameter to every media request (manifest + segments) as
 * ExoPlayer opens it. Wrapping the shared HTTP data source with a [ResolvingDataSource] covers both
 * VOD and live, which share the same playback path. Query is the only transmission mode — the SDK
 * does not expose a toggle (mirrors the iOS player's fixed-transport design).
 */
@SuppressLint("UnsafeOptInUsageError")
internal class CmcdResolver(private val session: CmcdSession) : ResolvingDataSource.Resolver {

    @SuppressLint("UnsafeOptInUsageError")
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec =
        dataSpec.withUri(session.appendCmcdQuery(dataSpec.uri))
}
