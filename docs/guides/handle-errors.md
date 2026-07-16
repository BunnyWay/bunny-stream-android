# Handle errors

How SDK calls fail and what to do about it.

## Today: results, not exceptions

Management calls (live streams, settings) return a result you check rather than an exception you
catch. Repository methods return an `Either<String, T>`: a message on failure, the value on
success:

```kotlin
repo.getLiveStream(libraryId, streamId).fold(
    { message -> log(message) },
    { stream -> render(stream) },
)
```

The one call that also carries the HTTP status code today is `pollLiveStream` (below). 4.0.0
extends that status-code envelope to every call.

<!-- TODO before the 4.0.0 release: replace the Either section above with the final unified
     result type and update the snippets. -->

## The 4.0.0 result envelope (planned)

> 4.0.0 replaces `Either<String, T>` with one result type on every management call. Names may
> still change before release.

A failure will carry three things:

- an HTTP status code (`0` when the request never reached the server: DNS, socket, timeout)
- a message for logging
- whether the failure is terminal

## Terminal vs transient

This split is the one decision that matters in error handling here:

- **Terminal** - `401`, `403`, `404`, `410`. The request will keep failing until something
  changes on your side or in the dashboard: wrong or revoked API key, a deleted stream or video,
  an ended stream. Do not retry in a loop; surface the state to the user.
- **Transient** - `5xx` and transport errors (status `0`). Retry with a delay, or just keep
  polling if you were polling.

The live poll result models this directly today:

```kotlin
when (val result = repo.pollLiveStream(libraryId, streamId)) {
    is LiveStreamPollResult.Success -> render(result.stream)
    is LiveStreamPollResult.Failure ->
        if (result.isTerminal()) stopPolling() else Unit  // transient: keep going
}
```

Both players implement these rules internally; you only need them for your own management calls.

## Upload errors

Uploads report failures through `UploadListener.onUploadError` with a typed `UploadError`:
`Unauthorized` (check the API key), `VideoNotFound`, `ServerError`, `ErrorCreating`,
`ErrorReadingFile` (check the Uri you passed), `UnknownError(message)`.

## Player errors

The players show their own error states and recover from transient stream problems on their own.
To also log playback errors in your code, register a
`PlayerStateListener` and read `onPlayerError(message)`.

## What NOT to handle

Generated REST client calls (`videosApi`, `collectionsApi`) currently throw exceptions
(`ClientException` for 4xx, `ServerException` for 5xx) instead of returning a result. Wrap them
in `try/catch` when you call them directly. The 4.0.0 refactor folds these into the same result
envelope as everything else.
