# Migrating to 4.0.0

4.0.0 unifies how the SDK reports asynchronous results and failures. Before it, the public API
answered in three different shapes — Arrow's `Either<String, T>` from repositories, callbacks from
uploads, and raw generated types elsewhere — and an error was a bare `String`, so telling a `401`
apart from a lost connection meant matching on message text.

After this release there is one result envelope, one error taxonomy, and uploads are a `Flow`.

Every change below is source-breaking. None of it is behavioural guesswork on your side: the
compiler points at each call site, and the fixes are mechanical.

---

## 1. Management calls return `BunnyResult<T>` instead of `Either<String, T>`

Everything on `LiveStreamRepository`, `SettingsRepository` and `BunnyStreamApi.fetchPlayerSettings`
now returns [`BunnyResult<T>`][result] — `Ok(value)` or `Err(BunnyError)`.

**Before**

```kotlin
repository.getLiveStream(libraryId, streamId).fold(
    ifLeft = { message -> showError(message) },
    ifRight = { stream -> render(stream) },
)
```

**After**

```kotlin
repository.getLiveStream(libraryId, streamId).fold(
    onOk = { stream -> render(stream) },
    onErr = { error -> showError(error.message) },
)
```

Note the argument names changed (`ifLeft`/`ifRight` → `onErr`/`onOk`) **and the order flipped** —
success comes first. If you used positional arguments, re-check each call; the types are often
compatible enough that a swapped pair still compiles.

`when` works too, and is usually clearer when you branch on the error:

```kotlin
when (val result = repository.listLiveStreams(libraryId)) {
    is BunnyResult.Ok -> render(result.value)
    is BunnyResult.Err -> if (result.isTerminal) giveUp(result.message) else retryLater()
}
```

Helpers: `getOrNull()`, `errorOrNull()`, `map { }`, `fold(onOk, onErr)`.

### Arrow is no longer on your compile classpath

Arrow was never meant to be part of the contract. It is now an internal implementation detail
(declared `implementation`, not `api`), so it no longer leaks transitively. If your code imported
`arrow.core.Either` only to read an SDK result, drop the dependency. If you use Arrow elsewhere,
nothing changes — just convert at the boundary:

```kotlin
fun <T> BunnyResult<T>.toEither(): Either<String, T> =
    fold(onOk = { it.right() }, onErr = { it.message.left() })
```

---

## 2. Errors are typed: the `BunnyError` taxonomy

An error is no longer a `String`. Six cases cover everything the SDK can fail with:

| Variant | When | `httpStatus` | `isTerminal` |
|---|---|---|---|
| `BunnyError.Auth` | `401`, `403` — key missing, expired, or not allowed for this library | `401`/`403` | `true` |
| `BunnyError.NotFound` | `404` — wrong video, stream or library id | `404` | `true` |
| `BunnyError.Http` | any other non-success status: `5xx`, rate limiting, validation | as returned | `true` only for `410` |
| `BunnyError.Network` | no usable response at all: DNS, connect, socket timeout, TLS, dropped connection | `0` | `false` |
| `BunnyError.Decode` | the response arrived but did not match the expected shape | `0` | `false` |
| `BunnyError.LocalFile` | the device could not read the file picked for upload | `0` | `true` |

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

`VideoUploader.uploadVideo` used to take an `UploadListener` and start immediately. It now returns
a cold `Flow<UploadEvent>` that runs the upload when collected.

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
single exception is a failure before the transfer could start — an unreadable file, or the video
record could not be created — which emits `Failed` alone.

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
is UploadEvent.Failed -> if (!event.error.isTerminal && event.videoId != null) {
    val retryId = tusVideoUploader.continueUpload(libraryId, event.videoId, sameUri)
    observe(retryId)
}
```

It needs the `videoId` and the same content URI, so persist both alongside the upload id. Only the
resumable (TUS) uploader can do this; on the plain uploader the returned upload fails immediately
with `BunnyError.InvalidState` rather than quietly re-sending everything.

---

## 4. Types that moved or disappeared

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
| `net.bunny.api.upload.model.HttpStatusCodes` | removed — unused |

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

## 5. Fixes that come with the change

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
- **Cancellation is no longer reported as a failure.** `CancellationException` was caught and turned
  into an error event; it is now rethrown, so structured concurrency behaves as expected.
- **The picked file's stream is always closed**, including when the upload fails or is cancelled.

---

## 6. What an upload still does not survive

Uploads survive navigation. They do not survive the process: if the app is killed or swiped away,
the transfer stops, because the SDK's scope goes with it.

For the resumable path that is recoverable — persist the `videoId` and the content URI, and call
`continueUpload` on next launch to pick the transfer up from the server's offset. Take a persistable
URI permission when you pick the file, or the URI will not be readable in the next process.

For an upload that must keep running while the app is away, that is a foreground service or
`WorkManager` job on your side; the SDK does not start one for you. `startUpload` and
`observeUpload` work the same from inside a `Worker`.

[result]: bunny-stream-api/src/main/java/net/bunny/api/error/BunnyResult.kt
