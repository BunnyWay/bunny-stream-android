package net.bunny.bunnystreamcameraupload

/**
 * Which RTMP ingest endpoint a live broadcast is currently publishing to. Bunny exposes a
 * [PRIMARY] and a [BACKUP] host (same stream key); the SDK publishes to the primary and fails
 * over to the backup if the primary becomes unreachable.
 *
 * Surfaced via [RecordingStateListener.onIngestEndpointChanged] so a host UI can show which
 * endpoint is live (mirrors the "Primary / Backup" badges in the web / iOS players).
 */
enum class IngestEndpoint { PRIMARY, BACKUP }
