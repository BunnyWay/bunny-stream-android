# Migrating to 4.0.0

> **Draft.** 4.0.0 is in development. The "4.0.0" snippets below show the planned API shape and
> may still change before the release is tagged; the 3.x snippets reflect the published SDK.

4.0.0 adds live streaming and reworks a few core APIs. This page lists what an app built against
3.x has to change. The [changelog](CHANGELOG.md) lists what changed; this page shows how to move
your code.

<!-- TODO before the 4.0.0 release: verify every before/after snippet against the final API
     and remove the draft note above. -->

## Summary

| You use | Impact |
|---|---|
| `playVideo`, the player view, `PlayerIconSet` | no changes |
| Camera recording (`BunnyStreamCameraUpload`) | no changes for recording; live broadcast is new |
| `initialize(context, accessKey, libraryId)` | replaced by a per-instance client, same three parameters (section 1); `accessKey` is now non-null (a null key never worked) |
| `videosApi` / `collectionsApi` raw clients | replaced by domain repositories |
| Repository calls returning `Either<String, T>` | replaced by a result envelope with HTTP status codes |
| Upload listeners (`UploadListener`) | replaced by a Flow-based API |
| One `BunnyStreamApi` singleton per process | replaced by per-instance clients (multi-library apps become possible) |

## 1. Session: singleton to instance

3.x kept one global session per process; the last `initialize` call won. 4.0.0 hands you an
instance instead, so two libraries can coexist and tests can inject their own configuration.

```kotlin
// 3.x
BunnyStreamApi.initialize(context, accessKey, libraryId)
val api = BunnyStreamApi.getInstance()

// 4.0.0
val bunny = BunnyStream.create(context, accessKey, libraryId)
```

The player and camera views resolve the session the same way as before; apps with a single
library mostly change the one initialization site.

## 2. Errors: strings to a result envelope

3.x repositories returned `Either<String, T>` with a message and no status code. 4.0.0 returns a
result carrying the HTTP status, a message and whether the failure is terminal:

```kotlin
// 3.x
repo.getLiveStream(libraryId, streamId).fold(
    { message -> show(message) },
    { stream -> render(stream) },
)

// 4.0.0
when (val result = repo.getLiveStream(streamId)) {
    is Ok -> render(result.value)
    is Err -> if (result.isTerminal) giveUp(result.message) else retryLater()
}
```

Terminal means 401, 403, 404 or 410: retrying will not help. Everything else (5xx, transport
errors reported as status 0) is worth a retry. See
[Handle errors](docs/guides/handle-errors.md).

## 3. Generated REST types are gone from the public API

Calls that went through the generated clients move to repositories with hand-written models:

```kotlin
// 3.x
val response = BunnyStreamApi.getInstance().videosApi.videoList(libraryId)

// 4.0.0
val result = bunny.videos.list(page = 1)
```

If your code imported anything from `org.openapitools.client.*`, replace those types with their
domain counterparts.

## 4. Uploads: callbacks to Flow

```kotlin
// 3.x
uploader.uploadVideo(libraryId, uri, object : UploadListener { /* five callbacks */ })

// 4.0.0
bunny.uploads.upload(uri).collect { state ->
    // queued / uploading(progress) / done / failed - one sealed type
}
```

Pause, resume and cancel stay available; they hang off the returned upload handle.

## What's new: live streaming

New capability, no migration needed - it is additive. Three pieces:

- [Play a live stream](docs/guides/play-a-live-stream.md) - `BunnyLiveStreamPlayer`, a composable
  that handles countdown, trailer, DVR and the switch to the recording by itself.
- [Go live from the camera](docs/guides/go-live-from-the-camera.md) - the camera view you already
  know broadcasts to a live stream once you set `liveStreamId`.
- [Manage live streams](docs/guides/manage-live-streams.md) - create, schedule, start, stop,
  thumbnails, trailer.

## Checklist

1. Update the dependency versions to 4.0.0.
2. Replace the initialization site (section 1).
3. Compile; every remaining error points at sections 2-4.
4. If you loaded Bunny thumbnails with your own image loader, nothing changes - keep sending the
   Referer header ([Secure playback](docs/guides/secure-playback.md)).
