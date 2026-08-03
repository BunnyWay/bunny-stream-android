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
out of source control. Blank credentials are rejected here rather than failing later with a 401.

### More than one library

`initialize` registers a *default instance*. Each instance owns its credentials, HTTP client and
uploads, so you can hold one per library:

```kotlin
val marketing = BunnyStreamApi.create(context, BunnyStreamConfig(marketingKey, 12345L))
val training = BunnyStreamApi.create(context, BunnyStreamConfig(trainingKey, 67890L))
```

Nothing is registered globally, so keep the handles. Point a view at one through its `bunny`
property, and call `release()` when you are done with an instance — that stops its uploads and
frees its HTTP client, and leaves every other instance running. Do not use an instance after
releasing it.

## What you can do with it

Everything is reachable from `BunnyStreamApi.getInstance()`:

```kotlin
// Videos and collections - suspend calls returning BunnyResult
val videos = BunnyStreamApi.getInstance().videoRepository.listVideos(libraryId = 12345L)
val collections = BunnyStreamApi.getInstance().collectionRepository.listCollections(libraryId = 12345L)

// Uploads (TUS, with pause and resume) - addressed by the id startUpload returns
val uploader = BunnyStreamApi.getInstance().tusVideoUploader
val uploadId = uploader.startUpload(libraryId, videoUri)
uploader.observeUpload(uploadId)?.collect { event -> render(event) }

// Live streams: create, schedule, start, stop, thumbnails, status
val repo = BunnyStreamApi.getInstance().liveStreamRepository
val created = repo.createLiveStream(libraryId, LiveStreamCreateRequest(title = "My stream"))
```

Step-by-step flows with prerequisites and gotchas:

- [Manage videos and collections](../docs/guides/manage-videos.md)
- [Upload videos](../docs/guides/upload-videos.md)
- [Manage live streams](../docs/guides/manage-live-streams.md)
- [Handle errors](../docs/guides/handle-errors.md)
- [Secure playback](../docs/guides/secure-playback.md) (tokens, Referer)

## Reference

- [API reference](https://bunnyway.github.io/bunny-stream-android/api/) (generated from the source)
- [Bunny Stream REST API](https://docs.bunny.net/reference/api-overview) for the endpoints behind
  the repositories. The generated client that calls them is internal from 4.0.0 on; the endpoint
  docs under `docs/` describe it and are not the integration surface.

## License

Bunny Stream Android is licensed under the [MIT License](../LICENSE).
