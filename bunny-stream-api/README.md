# bunny-stream-api

Core module of the Bunny Stream Android SDK (`net.bunny:api`). REST API access for the videos,
collections and live streams of your library, video uploads (including chunked TUS with
mid-upload pause and resume), playback
settings and resume-position storage. The player and camera modules build on it.

## Installation

```kotlin
implementation("net.bunny:api:latest.release")
```

Requires Android 8.0 (API 26) and the `INTERNET` permission in your manifest.

## Initialization

Call once, before anything else from the SDK - `Application.onCreate` is the usual place:

```kotlin
BunnyStreamApi.initialize(context, accessKey = "your-api-key", libraryId = 12345L)
```

`accessKey` is your library's API key (Bunny dashboard > Stream > your library > API). Keep it
out of source control.

## What you can do with it

Everything is reachable from `BunnyStreamApi.getInstance()`:

```kotlin
// Videos and collections (blocking calls - run them off the main thread)
val videos = BunnyStreamApi.getInstance().videosApi.videoList(libraryId = 12345L)

// Uploads (TUS, with pause and resume)
BunnyStreamApi.getInstance().tusVideoUploader.uploadVideo(libraryId, videoUri, listener)

// Live streams: create, schedule, start, stop, thumbnails, status
val repo = BunnyStreamApi.getInstance().liveStreamRepository
val created = repo.createLiveStream(libraryId, LiveStreamCreateRequest(title = "My stream"))
```

Step-by-step flows with prerequisites and gotchas:

- [Upload videos](../docs/guides/upload-videos.md)
- [Manage live streams](../docs/guides/manage-live-streams.md)
- [Handle errors](../docs/guides/handle-errors.md)
- [Secure playback](../docs/guides/secure-playback.md) (tokens, Referer)

## Reference

- [API reference](https://bunnyway.github.io/bunny-stream-android/api/) (generated from the source)
- Generated REST endpoint docs: [Videos](../docs/ManageVideosApi.md),
  [Collections](../docs/ManageCollectionsApi.md),
  [Live streams](../docs/ManageLiveStreamsApi.md)

## License

Bunny Stream Android is licensed under the [MIT License](../LICENSE).
