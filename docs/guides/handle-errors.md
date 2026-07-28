# Handle errors

How SDK calls fail and what to do about it.

## Results, not exceptions

Management calls — live streams, settings, player settings — return a result you check rather than
an exception you catch. That result is `BunnyResult<T>`: either `Ok` with the value, or `Err` with
a typed error.

```kotlin
when (val result = repo.getLiveStream(libraryId, streamId)) {
    is BunnyResult.Ok -> render(result.value)
    is BunnyResult.Err -> if (result.isTerminal) giveUp(result.message) else retryLater()
}
```

`fold` is shorter when both sides are one-liners. Note that success comes **first**:

```kotlin
repo.getLiveStream(libraryId, streamId).fold(
    onOk = { stream -> render(stream) },
    onErr = { error -> log(error.message) },
)
```

Also available: `getOrNull()`, `errorOrNull()` and `map { }`.

## Terminal vs transient

This split is the one decision that matters in error handling here, and every error answers it
directly through `isTerminal`:

- **Terminal** — `401`, `403`, `404`, `410`, plus the device-side cases below. The request will
  keep failing until something changes on your side or in the dashboard: wrong or revoked API key,
  a deleted stream or video, an ended stream. Do not retry in a loop; surface the state to the user.
- **Transient** — `5xx` and transport errors (status `0`). Retry with a delay, or just keep polling
  if you were polling.

```kotlin
if (error.isTerminal) showPermanentFailure(error.message) else scheduleRetry()
```

## The error taxonomy

`BunnyError` has seven cases. Branch on the type when you want to react specifically; branch on
`isTerminal` when you only need to know whether retrying is worth it.

| Case | When | `httpStatus` | `isTerminal` |
|---|---|---|---|
| `Auth` | `401`/`403` — key missing, expired, or not allowed for this library | `401`/`403` | `true` |
| `NotFound` | `404` — wrong video, stream or library id | `404` | `true` |
| `Http` | any other non-success status: `5xx`, rate limiting, validation | as returned | `true` only for `410` |
| `Network` | no usable response: DNS, connect, socket timeout, TLS, dropped connection | `0` | `false` |
| `Decode` | the response arrived but did not match the expected shape | `0` | `false` |
| `LocalFile` | the device could not read the file picked for upload | `0` | `true` |
| `InvalidState` | the resource forbids the operation: an ended live stream, a stream key not issued yet | `0` | varies — it says so itself |

```kotlin
when (error) {
    is BunnyError.Auth -> promptForNewAccessKey()
    is BunnyError.NotFound -> removeFromList()
    else -> if (error.isTerminal) giveUp(error.message) else scheduleRetry()
}
```

`error.message` is a human-readable description, safe to log.

## Polling

`pollLiveStream` returns the same envelope as everything else, so a polling loop reads
`isTerminal` to decide whether to stop:

```kotlin
when (val result = repo.pollLiveStream(libraryId, streamId)) {
    is BunnyResult.Ok -> render(result.value)
    is BunnyResult.Err -> if (result.isTerminal) stopPolling() else Unit  // transient: keep going
}
```

Both players implement these rules internally; you only need them for your own management calls.

## Upload errors

Uploads report failures as an `UploadEvent.Failed` **value** on the event stream, not as a thrown
exception — so a `collect` without a `catch` cannot miss one. The event carries the same
`BunnyError` as everything else:

```kotlin
uploader.observeUpload(uploadId)?.collect { event ->
    if (event is UploadEvent.Failed) {
        if (!event.error.isTerminal && event.videoId != null) {
            // Resumable path: continue from the offset the server already has
            val retryId = tusVideoUploader.continueUpload(libraryId, event.videoId, uri)
            observe(retryId)
        } else {
            showError(event.error.message)
        }
    }
}
```

`LocalFile` means the picked file could not be read — a different file is the only fix.
`InvalidState` from `continueUpload` means that uploader cannot resume; only the TUS one records
an offset.

See [Upload videos](upload-videos.md) for the full flow.

## Player errors

The players show their own error states and recover from transient stream problems on their own.
To also log playback errors in your code, register a `PlayerStateListener` and read
`onPlayerError(message)`.

## What NOT to handle this way

The generated REST clients (`videosApi`, `collectionsApi`, `liveStreamsApi`) still **throw**
instead of returning a result: `ClientException` for 4xx, `ServerException` for 5xx. Wrap them in
`try/catch` when you call them directly.

Folding them into the same envelope needs domain models for videos and collections — a later part
of the 4.0.0 refactor that has not shipped yet. Where a repository exists
(`liveStreamRepository`, `settingsRepository`), prefer it: those are already converted.
