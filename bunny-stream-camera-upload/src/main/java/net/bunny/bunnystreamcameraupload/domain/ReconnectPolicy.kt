package net.bunny.bunnystreamcameraupload.domain

/**
 * Single-publish reconnect policy, kept in sync with the iOS SDK's broadcaster:
 * up to [MAX_SINGLE_RETRIES] attempts since the last successful connect, exponential backoff
 * capped at 8 s (1, 2, 4, 8, 8 …), alternating primary <-> backup on every attempt when a backup
 * ingest is configured. Pure functions so the policy is unit-testable without RTMP.
 *
 * Dual-publish (simultaneous primary + backup) deliberately does NOT use this policy — each
 * output keeps reconnecting to its own host on a fixed delay, unchanged.
 */
internal object ReconnectPolicy {

    /** Max reconnect attempts since the last successful connect before giving up (matches iOS). */
    const val MAX_SINGLE_RETRIES = 5

    /** Exponential backoff capped at 8 s: attempts 1..5 -> 1, 2, 4, 8, 8 seconds (matches iOS). */
    fun reconnectDelayMs(attempt: Int): Long =
        (1L shl (attempt - 1).coerceIn(0, 3)).coerceAtMost(8L) * 1_000L

    /** Whether the next attempt should target the backup ingest (alternates when one exists). */
    fun nextUsesBackup(currentlyUsingBackup: Boolean, hasBackup: Boolean): Boolean =
        if (hasBackup) !currentlyUsingBackup else false
}
