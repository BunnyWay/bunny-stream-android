# Manage videos and collections

Read, edit, organise and clean up the videos in a library through `videoRepository` and
`collectionRepository`.

## Prerequisites

- `net.bunny:api` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)
- All repository methods are `suspend` - call them from a coroutine

```kotlin
val videos = BunnyStreamApi.getInstance().videoRepository
val collections = BunnyStreamApi.getInstance().collectionRepository
```

Every method returns `BunnyResult<T>` — `Ok` with the value or `Err` with a typed `BunnyError`.
See [Handle errors](handle-errors.md) for the taxonomy and the terminal/transient split.

To put a video *into* the library in the first place, see [Upload videos](upload-videos.md).

## List and read

```kotlin
videos.listVideos(
    libraryId = 12345L,
    page = 1,
    itemsPerPage = 20,
    search = "keynote",       // optional, matches the title
    orderBy = "date",         // or "title"
    collectionId = null,      // restrict to one collection
).fold(
    onOk = { page -> render(page.items, page.totalItems) },
    onErr = { error -> showError(error.message) },
)

videos.getVideo(libraryId, videoId)
```

A `Video` carries everything the API knows about it: `status`, `lengthSeconds`, `width`/`height`,
`availableResolutions`, `captions`, `chapters`, `moments`, `metaTags`, view counts and watch time.
Two conveniences over the raw API:

- `availableResolutions` and `outputCodecs` arrive as `List<String>`, already split.
- `width`, `height` and `framerate` are `null` until transcoding has measured them, where the API
  reports `0`. A layout will not compute an aspect ratio from a placeholder.

### Waiting for a video to finish encoding

`status` walks `CREATED → UPLOADED → PROCESSING → TRANSCODING → FINISHED`, and a freshly uploaded
video is not playable until it gets there. Poll `getVideo` while it is still in flight:

```kotlin
val settled = setOf(
    VideoModelStatus.FINISHED,
    VideoModelStatus.ERROR,
    VideoModelStatus.UPLOAD_FAILED,
)
while (video.status !in settled) {
    delay(5_000)
    video = videos.getVideo(libraryId, videoId).getOrNull() ?: break
}
```

`encodeProgress` is a percentage you can show while waiting.

## Play data

`fetchVideoPlayData` returns the URLs plus the library's player configuration in one call. This is
what the player uses internally; reach for it when you drive playback yourself.

```kotlin
videos.fetchVideoPlayData(
    libraryId, videoId,
    token = signedToken,   // required when the library has token authentication on
    expires = expiryEpochSeconds,
)
```

See [Secure playback](secure-playback.md) for how tokens are produced. Sign them on your server,
never in the app.

## Edit metadata

```kotlin
videos.updateVideo(
    libraryId, videoId,
    UpdateVideoRequest(
        title = "Keynote, final cut",
        collectionId = "collection-guid",
        chapters = listOf(Chapter(title = "Intro", startSeconds = 0, endSeconds = 42)),
    ),
)
```

Only the fields you set are sent; everything left `null` keeps its current value.

## Thumbnails

```kotlin
videos.setThumbnail(libraryId, videoId, thumbnailUrl)   // an image already reachable by URL
videos.uploadThumbnail(libraryId, videoId, imageFile)   // or one from the device
```

## Captions

```kotlin
videos.addCaption(
    libraryId, videoId,
    AddCaptionRequest(languageCode = "en", label = "English", captionsFileBase64 = vttAsBase64),
)
videos.deleteCaption(libraryId, videoId, languageCode = "en")
```

Bunny can also produce them for you, see [AI features](#ai-features) below.

## Import from a URL

Instead of uploading bytes from the device, have Bunny fetch the file:

```kotlin
videos.fetchNewVideo(
    libraryId,
    FetchVideoRequest(url = "https://example.com/source.mp4", title = "Imported"),
    collectionId = "collection-guid",
    thumbnailTime = 5_000,       // milliseconds into the video
)

// Replace an existing video's content from a new source URL
videos.refetchVideo(libraryId, videoId, FetchVideoRequest(url = newUrl))
```

The call returns as soon as Bunny accepts the job; the video then goes through the normal encoding
states.

## Re-encode, repackage, reclaim storage

```kotlin
videos.reencodeVideo(libraryId, videoId)                          // library's current settings
videos.reencodeUsingCodec(libraryId, videoId, VideoCodec.HEVC)    // a specific codec
videos.repackageVideo(libraryId, videoId, keepOriginalFiles = true)
```

Deleting renditions is how you reclaim storage, and it is **irreversible** — the only way back is a
re-encode, which needs the original file. Ask the server what would happen first:

```kotlin
val preview = videos.deleteResolutions(
    libraryId, videoId,
    resolutions = listOf("240p", "360p"),
    dryRun = true,          // report what would be deleted, delete nothing
)
```

Then run it for real. Each flag deletes something different:

| flag | removes |
|---|---|
| `resolutions` | the renditions you name |
| `deleteAllResolutions` | every rendition, ignoring `resolutions` |
| `deleteNonConfiguredResolutions` | renditions the library no longer produces |
| `deleteMp4Files` | the MP4 fallback files - playback falls back to these when HLS is unavailable |
| `deleteOriginal` | the uploaded master, after which the video can never be re-encoded |

`fetchVideoResolutions` tells you what exists before you decide, and `fetchVideoStorageSize` breaks
down where the storage is going.

## Statistics and the retention heatmap

```kotlin
videos.fetchVideoStatistics(libraryId, videoId, dateFrom = "2026-07-01", dateTo = "2026-07-31")
videos.fetchVideoHeatmap(libraryId, videoId)       // Map<offset, viewers>
videos.fetchVideoHeatmapData(libraryId, videoId)   // play data with the heatmap included
```

Pass `videoId = null` to `fetchVideoStatistics` for library-wide numbers, and `hourly = true` to
aggregate per hour instead of per day.

## AI features

```kotlin
videos.smartGenerate(
    libraryId, videoId,
    SmartGenerateRequest(
        generateTitle = true,
        generateDescription = true,
        generateChapters = true,
        generateMoments = true,
        sourceLanguage = "en",
    ),
)
videos.transcribeVideo(
    libraryId, videoId,
    TranscribeVideoRequest(sourceLanguage = "en", targetLanguages = listOf("de", "fr")),
    force = false,    // true re-runs it on a video that already has a transcription
)
```

Both are asynchronous on Bunny's side. Poll `getVideo` and read `smartGenerateStatus` and the
`captions` list to see the results land.

## Delete

```kotlin
videos.deleteVideo(libraryId, videoId)
```

Permanent, and it takes everything derived from the video with it.

## Collections

Collections are named groupings a video belongs to, through its `collectionId`.

```kotlin
collections.listCollections(libraryId, page = 1, itemsPerPage = 20, includeThumbnails = true)
collections.getCollection(libraryId, collectionId)
collections.createCollection(libraryId, name = "Conference 2026")
collections.updateCollection(libraryId, collectionId, name = "Conference 2026 - day 1")
collections.deleteCollection(libraryId, collectionId)
```

A `VideoCollection` reports `videoCount`, `totalSizeBytes` and `previewVideoIds`; ask for
`includeThumbnails` to also get `previewImageUrls` for a grid.

Deleting a collection does not delete its videos, they just stop belonging to it.

## Gotchas

- A mutation can fail with HTTP 200. Bunny answers `success = false` for validation failures and
  wrong-state requests; the repository turns that into an `Err`, so check the result rather than
  assuming a call that did not throw did what you asked.
- `deleteResolutions` and `deleteVideo` cannot be undone. Use `dryRun` first.
- Statistics dates are `yyyy-MM-dd` strings.
- A video that is still encoding has no `availableResolutions` and no dimensions yet. Do not build
  a quality picker from a video that has not reached `FINISHED`.

Working example: `LibraryScreen` and `PlayerScreen` in the
[demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
