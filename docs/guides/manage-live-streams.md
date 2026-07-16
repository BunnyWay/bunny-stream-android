# Manage live streams

Create, schedule, start, stop and decorate live streams from your app through
`liveStreamRepository`.

## Prerequisites

- `net.bunny:api` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)
- All repository methods are `suspend` - call them from a coroutine

```kotlin
val repo = BunnyStreamApi.getInstance().liveStreamRepository
```

<!-- TODO before the 4.0.0 release: result handling below uses the current Either-based
     returns; swap the fold() calls to the final 4.0.0 result type. Method names and
     parameters stay as they are. -->

## Create a stream

```kotlin
val result = repo.createLiveStream(
    libraryId = 12345L,
    request = LiveStreamCreateRequest(
        title = "Friday Q&A",                           // required by the API
        scheduledStartTime = "2026-08-01T18:00:00Z",    // optional, ISO 8601
        enableCountdown = true,                         // show a countdown before the start
        dvrEnabled = true,                              // viewers can pause and seek back
        dvrWindowSeconds = 1800,                        // how far back
        recordVod = true,                               // keep a recording after the stream ends
        preStreamTrailerVideoId = "trailer-video-guid", // optional, loops before the start
    ),
)
result.fold(
    { error -> /* show error */ },
    { stream -> /* stream.id, stream.streamKey, stream.primaryIngestUrl */ },
)
```

The created stream carries everything a broadcaster needs: `streamKey`, `primaryIngestUrl`,
`backupIngestUrl` and up to four restream outputs (`rtmpOutputs`).

## List, read, update, delete

```kotlin
repo.listLiveStreams(libraryId, page = 1, itemsPerPage = 20, search = "q&a")
repo.getLiveStream(libraryId, streamId)
// Update reuses the create request; only non-null fields are sent, the rest stays unchanged.
repo.updateLiveStream(libraryId, streamId, LiveStreamCreateRequest(title = "New title"))
repo.deleteLiveStream(libraryId, streamId)
```

Deleting is permanent, but a recorded VOD survives the stream that produced it.

## Start and stop

```kotlin
repo.startLiveStream(libraryId, streamId)  // PREVIEW -> RUNNING, viewers can watch
repo.stopLiveStream(libraryId, streamId)   // ends the stream; converts to VOD when recordVod is set
```

You only call these when driving a broadcast yourself (for example with an external encoder).
The camera view from [Go live from the camera](go-live-from-the-camera.md) starts and stops the
stream for you.

A stream's life: created (or scheduled) -> an encoder connects (PREVIEW) -> started (RUNNING) ->
stopped (ENDED, then VOD_PROCESSING when it records). An ended stream cannot be restarted.

## Watching the status

Poll with `pollLiveStream` when you need the state cheaply and repeatedly (a lobby screen, a
"stream starts soon" page):

```kotlin
when (val result = repo.pollLiveStream(libraryId, streamId)) {
    is LiveStreamPollResult.Success -> render(result.stream.status)
    is LiveStreamPollResult.Failure ->
        if (result.isTerminal()) stopPolling()   // 401/403/404/410: gone for good
        else Unit                                // transient (5xx, network): keep polling
}
```

The players do this internally; you rarely need it next to a player.

## Thumbnails

```kotlin
repo.listLiveStreamThumbnails(libraryId, streamId, limit = 24)  // generated preview thumbnails
repo.setLiveStreamThumbnail(libraryId, streamId, url)           // pick one
repo.uploadLiveStreamThumbnail(libraryId, streamId, bytes, "image/jpeg")  // or upload your own
repo.deleteLiveStreamThumbnail(libraryId, streamId, restoreLibraryDefault = true)
```

The thumbnail shows in the player before the stream starts and when the encoder disconnects.

## Pre-stream trailer

The trailer is a normal video from your library, referenced at create or update time through
`preStreamTrailerVideoId`. The player loops it (muted) before the stream starts; it takes
priority over the thumbnail.

## Gotchas

- `title` is required on create.
- Dates are ISO 8601 strings; include a timezone (`Z` or an offset).
- Restream outputs (`rtmpOutputs`, up to 4) are accepted by the API but the feature may not be
  enabled for your account yet; a non-empty list can be rejected server-side.

Working example: `LiveStreamsScreen` and `LiveStreamEditorScreen` in the
[demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
