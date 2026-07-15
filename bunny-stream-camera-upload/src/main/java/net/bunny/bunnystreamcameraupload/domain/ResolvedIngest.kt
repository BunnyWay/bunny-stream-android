package net.bunny.bunnystreamcameraupload.domain

/**
 * A resolved RTMP publish target. [primaryUrl] is the host we publish to; [backupUrl] is an
 * optional failover host (same stream key, different ingest server) used when the primary keeps
 * failing. VOD recording has no backup, so [backupUrl] is `null` there.
 */
internal data class ResolvedIngest(
    val primaryUrl: String,
    val backupUrl: String? = null,
)
