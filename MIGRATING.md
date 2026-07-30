# Migrating to 4.0.0

4.0.0 changes two things about the SDK's public API, and both were long overdue.

**How results and failures are reported.** 3.x answered in three different shapes — Arrow's
`Either<String, T>` from repositories, callbacks from uploads, and raw generated types elsewhere —
and an error was a bare `String`, so telling a `401` apart from a lost connection meant matching on
message text. There is now one result envelope, one error taxonomy, and uploads are a `Flow`.

**What the API is made of.** 3.x handed you the OpenAPI generator's output: `videosApi` and
`collectionsApi` returned types from `org.openapitools.client.models`, which made Bunny's API spec
your compile-time dependency. Those are replaced by repositories speaking domain models, so a
change to our spec can no longer break your build.

Every change below is source-breaking, and **nothing you could do in 3.x is gone** — every method
and every field has an equivalent. The compiler points at each call site, and the fixes are
mechanical.

Before any of that, section 0 covers what has to change in your build file. Sections 1–3 cover
results and errors, section 4 the generated REST types, sections 5–7 the upload API.

---

## 0. Build requirements

Three of these are enforced by the build, so they surface as errors rather than as anything you
could miss.

| | 3.x | 4.0.0 |
|---|---|---|
| `compileSdk` | 35 | **36** or higher |
| Kotlin | 2.1 | **2.4** or newer |
| Core library desugaring | not needed | **required** for `net.bunny:player` |
| `minSdk` | 26 | 26, unchanged |
| JDK | 17 | 17, unchanged |

`compileSdk` below 36 fails in `checkAarMetadata`, naming the dependency that demands it. An older
Kotlin compiler reports the SDK's classes as having incompatible metadata - it cannot read them at
all, so there is no partial-use path. Without desugaring, the player's media3 dependency is
rejected outright:

```kotlin
// build.gradle.kts
android {
    compileSdk = 36

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
```

`minSdk` stays at 26, so the devices you reach do not change. Nothing here affects `targetSdk`
either - that stays your decision, and Google Play's requirement for it is unrelated to this
release.

---

## 1. Management calls return `BunnyResult<T>` instead of `Either<String, T>`

In 3.x exactly three things returned `Either<String, T>`:

- `BunnyStreamApi.fetchPlayerSettings` (and `StreamApi.fetchPlayerSettings`),
- `SettingsRepository.fetchSettings`,
- `RecordingRepository.prepareRecording` in the `:recording` module.

All three now return [`BunnyResult<T>`][result] — `Ok(value)` or `Err(BunnyError)` — as does the new
`LiveStreamRepository` that arrives with live streams in this release.

**Before**

```kotlin
BunnyStreamApi.getInstance().fetchPlayerSettings(libraryId, videoId).fold(
    ifLeft = { message -> showError(message) },
    ifRight = { settings -> apply(settings) },
)
```

**After**

```kotlin
BunnyStreamApi.getInstance().fetchPlayerSettings(libraryId, videoId).fold(
    onOk = { settings -> apply(settings) },
    onErr = { error -> showError(error.message) },
)
```

Note the argument names changed (`ifLeft`/`ifRight` → `onErr`/`onOk`) **and the order flipped** —
success comes first. If you used positional arguments, re-check each call; the types are often
compatible enough that a swapped pair still compiles.

`when` works too, and is usually clearer when you branch on the error:

```kotlin
when (val result = repository.fetchSettings(libraryId, videoId)) {
    is BunnyResult.Ok -> apply(result.value)
    is BunnyResult.Err -> if (result.isTerminal) giveUp(result.message) else retryLater()
}
```

Helpers: `getOrNull()`, `errorOrNull()`, `map { }`, `fold(onOk, onErr)`.

### `:recording` — `RecordingRepository` is now internal

`net.bunny.bunnystreamcameraupload.domain.RecordingRepository` and its implementation are
`internal` in 4.0.0. It was public by accident: nothing in the documented API ever took one or
handed one out, and its single 3.x method (`prepareRecording`) exists to serve
`StreamCameraUploadView`, which is the supported way to record.

If you called it directly, `StreamCameraUploadView` covers recording to a new video and
broadcasting to an existing live stream (see
[Go live from the camera](docs/guides/go-live-from-the-camera.md)). For anything it does not
cover, `videoRepository.createVideo` gives you the same video record the repository was creating.

### Arrow is no longer on your compile classpath

Arrow was never meant to be part of the contract. It is now gone from the SDK entirely — no module
declares it and no source imports it — so it cannot reach your classpath through us by any route.
If your code imported `arrow.core.Either` only to read an SDK result, drop the dependency. If you
use Arrow elsewhere, nothing changes — just convert at the boundary:

```kotlin
fun <T> BunnyResult<T>.toEither(): Either<String, T> =
    fold(onOk = { it.right() }, onErr = { it.message.left() })
```

---

## 2. Errors are typed: the `BunnyError` taxonomy

An error is no longer a `String`. Seven cases cover everything the SDK can fail with:

| Variant | When | `httpStatus` | `isTerminal` |
|---|---|---|---|
| `BunnyError.Auth` | `401`, `403` — key missing, expired, or not allowed for this library | `401`/`403` | `true` |
| `BunnyError.NotFound` | `404` — wrong video, stream or library id | `404` | `true` |
| `BunnyError.Http` | any other non-success status: `5xx`, rate limiting, validation | as returned | `true` only for `410` |
| `BunnyError.Network` | no usable response at all: DNS, connect, socket timeout, TLS, dropped connection | `0` | `false` |
| `BunnyError.Decode` | the response arrived but did not match the expected shape | `0` | `false` |
| `BunnyError.LocalFile` | the device could not read the file picked for upload | `0` | `true` |
| `BunnyError.InvalidState` | the call succeeded but the resource forbids the operation: publishing to an ended live stream, continuing an upload on the non-resumable path | `0` | varies — it says so itself |

Two properties answer the questions callers actually ask:

- `error.httpStatus` — the numeric status, or `0` when no HTTP response existed.
- `error.isTerminal` — whether retrying can ever succeed. Use it instead of hand-listing status
  codes; the polling loops in the SDK use the same flag.

```kotlin
when (error) {
    is BunnyError.Auth -> promptForNewAccessKey()
    is BunnyError.NotFound -> removeFromList()
    else -> if (error.isTerminal) giveUp(error.message) else scheduleRetry()
}
```

`error.message` is a human-readable description, safe to log. The wording for HTTP failures is
unchanged from 3.x, so existing log greps and debug UI keep working.

---

## 3. Uploads are a `Flow`, not a listener

`VideoUploader.uploadVideo` used to take an `UploadListener` and start immediately. It is replaced
by `startUpload`, which returns an id, and `observeUpload`, which streams that upload's events.

**Before**

```kotlin
videoUploader.uploadVideo(libraryId, uri, object : UploadListener {
    override fun onUploadStarted(uploadId: String, videoId: String) { this@X.uploadId = uploadId }
    override fun onProgressUpdated(percentage: Int, videoId: String, pauseState: PauseState) {
        showProgress(percentage, pauseState)
    }
    override fun onUploadDone(videoId: String) { showDone(videoId) }
    override fun onUploadError(error: UploadError, videoId: String?) { showError(error.toString()) }
    override fun onUploadCancelled(videoId: String) { dismiss() }
})
```

**After**

```kotlin
val uploadId = videoUploader.startUpload(libraryId, uri)
store.activeUpload = uploadId          // keep it: it is the only handle to this upload

viewModelScope.launch {
    videoUploader.observeUpload(uploadId)?.collect { event ->
        when (event) {
            is UploadEvent.Started   -> rememberVideoId(event.videoId)
            is UploadEvent.Progress  -> showProgress(event.percentage, event.pauseState)
            is UploadEvent.Completed -> showDone(event.videoId)
            is UploadEvent.Cancelled -> dismiss()
            is UploadEvent.Failed    -> showError(event.error.message)
        }
    }
}
```

The event sequence is ordered and finite: one `Started`, then zero or more `Progress`, then exactly
one terminal event (`Completed`, `Cancelled` or `Failed`), after which the flow completes. The
sequence is shorter when nothing ever got underway: a failure before the transfer could start — an
unreadable file, or the video record could not be created — emits `Failed` alone, and a cancel
that lands during that same window emits `Cancelled` alone.

Failures arrive as a `Failed` **value**, not a thrown exception, so a `collect` without a `catch`
cannot miss one.

### Starting and watching are now separate

`uploadVideo` is gone; there is `startUpload` and `observeUpload`. That is not ceremony — it is what
makes the rest work.

An upload runs inside the SDK, not in the coroutine that started it, so it keeps going when the
screen that began it is destroyed. `startUpload` hands back an **upload id**, and everything else —
observing, pausing, cancelling, continuing after a failure — is addressed by that id. Keep it
somewhere that outlives the screen and re-attach on the way back in:

```kotlin
// returning to the screen
store.activeUpload?.let { id ->
    viewModelScope.launch { videoUploader.observeUpload(id)?.collect(::render) }
}
```

`observeUpload` returns `null` when the id is unknown — it never existed, or it finished long enough
ago that the SDK has forgotten it (the last 32 uploads stay addressable). Several collectors may
watch the same upload at once, and abandoning one does not stop the transfer.

A collector joins at "now", not at the beginning: attaching to an upload already in flight starts
from the most recent event, so `Started` may never arrive. Every event carries the `videoId`, so
read it from whichever arrives first rather than only from `Started`.

Two ways of stopping, still not equivalent:

- **abandoning the collector** stops watching and leaves the transfer running;
- **`cancelUpload(uploadId)`** stops it, deletes the partial video server-side, and emits
  `Cancelled`.

Call `cancelUpload` when a user presses cancel.

If you relied on 3.x uploads surviving navigation, they still do. What changed is that the handle to
one is now explicit rather than a listener you happened to re-register.

### Continuing an interrupted upload

New in 4.0.0. On `UploadEvent.Failed` with a non-terminal error, `continueUpload` picks the transfer
up from the offset the server already has instead of re-sending the file:

```kotlin
// The upload was started on the resumable uploader:
val uploadId = tusVideoUploader.startUpload(libraryId, uri)

// …and later, on a transient failure, the same uploader continues it:
is UploadEvent.Failed -> event.videoId?.let { videoId ->
    if (!event.error.isTerminal) {
        observe(tusVideoUploader.continueUpload(libraryId, videoId, uri))
    }
}
```

Continue on the **same uploader that started the upload**. Only the resumable (TUS) one records an
offset, so continuing a transfer that went out through the plain uploader has nothing to resume
from: that call fails immediately with `BunnyError.InvalidState` rather than quietly re-sending the
whole file.

It needs the `videoId` and the same content URI, so persist both alongside the upload id.

---

## 4. The generated REST clients are gone from the public API

3.x handed you the OpenAPI generator's output directly: `videosApi` and `collectionsApi` on
`BunnyStreamApi`, returning types from `org.openapitools.client.models`. That made Bunny's API
spec your compile-time dependency — a renamed field on our side broke your build without anyone
touching your code.

4.0.0 replaces them with repositories that speak domain models:

```kotlin
// 3.x — generated client, blocking, throws on failure
val response = BunnyStreamApi.getInstance().videosApi.videoList(libraryId)
val videos = response.items.orEmpty()

// 4.0.0 — repository, suspend, BunnyResult
BunnyStreamApi.getInstance().videoRepository.listVideos(libraryId).fold(
    onOk = { page -> render(page.items) },
    onErr = { error -> showError(error.message) },
)
```

**No capability was dropped.** 22 of the 23 `videosApi` methods and all 5 `collectionsApi` methods
have a repository equivalent, and the domain models carry every field the generated ones did. The
23rd, `videoUploadVideo`, is the upload endpoint — `videoUploader` / `tusVideoUploader` have always
been the way to reach it, and they still are.

| 3.x | 4.0.0 |
|---|---|
| `StreamApi.videosApi` | `StreamApi.videoRepository` |
| `StreamApi.collectionsApi` | `StreamApi.collectionRepository` |
| `org.openapitools.client.models.VideoModel` | `net.bunny.api.video.domain.model.Video` |
| `VideoPlayDataModel` | `VideoPlayData` |
| `PaginationListOfVideoModel` | `VideoList` |
| `CollectionModel` | `VideoCollection` — prefixed so it does not clash with `kotlin.collections.Collection` |
| `PaginationListOfCollectionModel` | `VideoCollectionList` |
| `VideoHeatmapModel` | plain `Map<String, Int>` — the wrapper carried nothing else |
| `CaptionModel`, `ChapterModel`, `MomentModel`, `MetaTagModel` | `Caption`, `Chapter`, `Moment`, `MetaTag` |
| `TranscodingMessageModel` + `Severity` + `IssueCodes` | `TranscodingMessage` + `TranscodingSeverity` + `TranscodingIssue` |
| `EncoderOutputCodec` | `VideoCodec` |
| request DTOs (`VideoCreateVideoRequest`, …) | `CreateVideoRequest`, `UpdateVideoRequest`, `AddCaptionRequest`, `FetchVideoRequest`, `SmartGenerateRequest`, `TranscribeVideoRequest` |

Field renames worth knowing, all of them making the meaning explicit:

| generated | domain |
|---|---|
| `guid` | `id` |
| `length` | `lengthSeconds` |
| `storageSize` | `storageSizeBytes` |
| `averageWatchTime` / `totalWatchTime` | `averageWatchTimeSeconds` / `totalWatchTimeSeconds` |
| `srclang` | `languageCode` |
| `start` / `end` (chapter) | `startSeconds` / `endSeconds` |
| `timestamp` (moment) | `timestampSeconds` |
| `availableResolutions: String` | `availableResolutions: List<String>` — split for you |
| `outputCodecs: String` | `outputCodecs: List<String>` — split for you |
| `hasMP4Fallback` | `hasMp4Fallback` |
| `smartGenerateFeaturesStatus` | `smartGenerateFeatures` |
| `totalSize` (collection) | `totalSizeBytes` |
| `length` (storage object) | `lengthBytes` |

Two behavioural differences the compiler will not point at:

- **Enums are named.** The generator emitted `Severity._2` and `IssueCodes._4`, with the meaning
  only in a doc comment. These are now `TranscodingSeverity.WARNING` and
  `TranscodingIssue.INVALID_FRAMERATE`. An unknown value from a newer server maps to `UNDEFINED`
  instead of failing to parse.
- **`0` no longer masquerades as data.** The API reports `width = 0`, `height = 0`,
  `framerate = 0.0` for a video that has not finished transcoding. The domain model reports `null`,
  so a layout does not compute an aspect ratio of `NaN`.

### The player takes a domain video

`BunnyPlayer.playVideo` — the entry point for building a custom player — takes the domain type now:

```kotlin
// 3.x
fun playVideo(playerView: PlayerView, video: VideoModel, retentionData: Map<Int, Int>, playerSettings: PlayerSettings)

// 4.0.0
fun playVideo(playerView: PlayerView, video: Video, retentionData: Map<Int, Int>, playerSettings: PlayerSettings)
```

`BunnyStreamPlayer.playVideo(videoId)` on the view is unchanged and remains the normal path.

---

## 5. Types that moved or disappeared

| 3.x | 4.0.0 |
|---|---|
| `VideoUploader.uploadVideo(libraryId, uri, listener)` | `startUpload(libraryId, uri)` + `observeUpload(uploadId)` |
| `net.bunny.api.upload.service.UploadListener` | removed — collect `Flow<UploadEvent>` |
| `net.bunny.api.upload.model.UploadError` | removed — folded into `net.bunny.api.error.BunnyError` |
| `net.bunny.api.upload.service.PauseState` | moved to `net.bunny.api.upload.model.PauseState` |
| `net.bunny.api.upload.service.UploadRequest` (+ `BasicUploadRequest`, `TusUploadRequest`) | removed — internal detail |
| `net.bunny.api.upload.service.UploadService` and both implementations | now `internal` |
| `net.bunny.api.upload.DefaultVideoUploader` | now `internal` — reach it via `BunnyStreamApi.getInstance().videoUploader` |
| `net.bunny.api.upload.model.FileInfo`, `StreamContent` | now `internal` |
| `net.bunny.api.upload.model.HttpStatusCodes` | removed — the status codes it named are now read by the error mapper |

`UploadError` maps onto the new taxonomy like this:

| `UploadError` | `BunnyError` |
|---|---|
| `Unauthorized` | `Auth(401, …)` |
| `VideoNotFound` | `NotFound(…)` |
| `ServerError` | `Http(5xx, …)` |
| `ErrorCreating` | `Decode(…)` — the create call succeeded but returned no video id |
| `ErrorReadingFile` | `LocalFile(…)` |
| `UnknownError` | `Network(…)` — transient, cause preserved |

---

## 6. Fixes that come with the change

Behaviour that was wrong before and is worth knowing about, because it may look like a new bug:

- **A rejected plain upload now reports the failure.** The non-success branch used to build an error
  value and discard it, so a `401` on the upload request produced no event at all and the UI sat at
  its last percentage forever. It now emits `Failed`.
- **Upload progress is measured against the real file size.** It was divided by
  `InputStream.available()`, which only promises what can be read without blocking, so on large
  files the percentage ran ahead of the transfer. It now uses the size reported by the content
  resolver.
- **Resumable uploads resume.** The TUS fingerprint — the key an upload's offset is stored under —
  was a fresh random UUID per attempt, so nothing could ever match a stored offset and every retry
  restarted from zero. It is now derived from the library and video id, and `continueUpload` is the
  entry point that uses it.
- **Cancellation and failure are told apart.** A `CancellationException` used to be reported as a
  cancelled upload whoever caused it, so a collector going away looked identical to the user
  pressing Cancel. Now a caller's `cancelUpload` produces `Cancelled` — including when the chunk in
  flight dies as a result — while a genuinely cancelled coroutine propagates, as structured
  concurrency requires.
- **An upload always ends with a terminal event.** Releasing the SDK instance, or an unexpected
  failure outside the transfer itself, used to end the stream silently and leave anyone observing
  it waiting forever. Every path now emits `Completed`, `Cancelled` or `Failed` before it closes.
- **The picked file's stream is always closed**, including when the upload fails or is cancelled.

---

## 7. What an upload still does not survive

Uploads survive navigation. They do not survive the process: if the app is killed or swiped away,
the transfer stops, because the SDK's scope goes with it.

For the resumable path that is recoverable — persist the `videoId` and the content URI, and call
`continueUpload` on next launch to pick the transfer up from the server's offset. Take a persistable
URI permission when you pick the file, or the URI will not be readable in the next process.

For an upload that must keep running while the app is away, that is a foreground service or
`WorkManager` job on your side; the SDK does not start one for you. `startUpload` and
`observeUpload` work the same from inside a `Worker`.

[result]: bunny-stream-api/src/main/java/net/bunny/api/error/BunnyResult.kt
