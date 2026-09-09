package net.bunny.api.livestream.domain.model

/**
 * Lightweight live-stream ingest status from `GET …/live/{streamId}/status` — the endpoint suited
 * for frequent polling (the full stream model does not expose per-ingest liveness).
 *
 * @property readyToStart whether the stream is ready to be started based on ingest liveness.
 * @property primaryLive whether the primary ingest is currently receiving data.
 * @property backupLive whether the backup ingest is currently receiving data.
 * @property lastPingAgoMs milliseconds since the ingest last saw data, when available.
 * @property durationSeconds current stream duration in seconds, when available.
 */
data class LiveStreamIngestStatus(
    val readyToStart: Boolean,
    val primaryLive: Boolean,
    val backupLive: Boolean,
    val lastPingAgoMs: Long?,
    val durationSeconds: Int?,
)
