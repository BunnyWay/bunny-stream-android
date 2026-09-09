package net.bunny.bunnystreamplayer.livestream

/**
 * How close to the end of a recording still counts as "the viewer watched it to the end". The
 * engine's last reported position rarely lands exactly on the duration, so a strict `==` would
 * never fire.
 */
private const val END_OF_RECORDING_TOLERANCE_MS = 1_000L

/**
 * Position the ended stream's recording should start from when the player is rebuilt underneath
 * the viewer — the recovery loop after a playback failure re-issues the load on the same URL, and
 * a rebuilt engine starts at 00:00 unless it is told otherwise.
 *
 * Pure so the decision can be unit-tested without an engine: media3 keeps reporting the last
 * position after it errors out (the player sits in `STATE_IDLE`), which is exactly the position
 * the viewer is looking at on the frozen frame.
 *
 * @param currentMs the engine's last reported position; `0` or less means it never got anywhere
 *                  worth restoring (a failure during the very first load).
 * @param durationMs the recording's duration, or `0`/`C.TIME_UNSET` while it is still unknown.
 * @return the position to resume from, or `null` to let the rebuild start from the beginning —
 *         which is the right answer both when there is nothing to restore and when the viewer had
 *         already reached the end.
 */
internal fun resumePositionAfterRebuild(currentMs: Long, durationMs: Long): Long? {
    if (currentMs <= 0L) return null
    // Duration is only a veto when it is known: an unknown one (still loading, or a live window)
    // must not throw away a position we do have.
    if (durationMs > 0L && currentMs >= durationMs - END_OF_RECORDING_TOLERANCE_MS) return null
    return currentMs
}
