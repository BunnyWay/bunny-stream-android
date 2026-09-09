package net.bunny.bunnystreamcameraupload

/**
 * Connection state of a single RTMP ingest endpoint ([IngestEndpoint]) while broadcasting.
 *
 * Reported per endpoint via [RecordingStateListener.onIngestEndpointChanged]. In single-publish
 * mode exactly one endpoint is ever [LIVE] at a time (the other stays [OFFLINE] as a standby that
 * only lights up after a failover). In dual-publish mode both endpoints can be [LIVE] at once.
 */
enum class IngestEndpointState {
    /** (Re)connecting to this endpoint — not yet live. */
    CONNECTING,

    /** Publishing to this endpoint. */
    LIVE,

    /** Not in use / disconnected (standby, or gave up). */
    OFFLINE,
}
