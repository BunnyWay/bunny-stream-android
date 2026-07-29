# Upload videos

Upload files from the device to your library.

## Prerequisites

- `net.bunny:api` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)
- A content `Uri` of the file to upload (from the system picker, camera, etc.)

## Pick an uploader

Two uploaders, same interface:

- `tusVideoUploader` — chunked upload (TUS). Only this one can pause, resume, and continue an
  interrupted upload. Use it unless you have a reason not to.
- `videoUploader` — a plain single-request upload. Simpler, but none of the above.

## An upload is a thing, not a call

`startUpload` begins the transfer and hands back an **upload id**. Everything else — watching it,
pausing, cancelling, continuing after a failure — is addressed by that id.

The transfer runs inside the SDK, not in the coroutine that started it, so it keeps going when the
screen that began it is destroyed. That makes the id the one thing you must not lose: keep it
somewhere that outlives the screen.

```kotlin
val uploader = BunnyStreamApi.getInstance().tusVideoUploader

val uploadId = uploader.startUpload(libraryId, videoUri)
store.activeUpload = uploadId          // outlives this screen

lifecycleScope.launch {
    uploader.observeUpload(uploadId)?.collect { event ->
        when (event) {
            is UploadEvent.Started   -> store.videoId = event.videoId
            is UploadEvent.Progress  -> showProgress(event.percentage, event.pauseState)
            is UploadEvent.Completed -> showDone(event.videoId)
            is UploadEvent.Cancelled -> dismiss()
            is UploadEvent.Failed    -> showError(event.error.message)
        }
    }
}
```

The SDK creates the video object in your library for you, and deletes it again when you cancel.

Failures arrive as an `UploadEvent.Failed` value carrying a typed `BunnyError`, not as a thrown
exception — see [Handle errors](handle-errors.md).

## Coming back to a running upload

Re-attach with the id you kept. Collecting neither starts nor stops anything, several collectors
can watch the same upload, and abandoning one leaves the transfer running:

```kotlin
store.activeUpload?.let { id ->
    lifecycleScope.launch { uploader.observeUpload(id)?.collect(::render) }
}
```

`observeUpload` returns `null` when the id is unknown — it never existed, or it finished long
enough ago that the SDK has forgotten it (the last 32 uploads stay addressable).

A collector joins at "now", not at the beginning: attaching to an upload already in flight starts
from the most recent event, so `Started` may never arrive. Every event carries the `videoId`, so
read it from whichever arrives first.

## Pause, resume, cancel

```kotlin
uploader.pauseUpload(uploadId)
uploader.resumeUpload(uploadId)
uploader.cancelUpload(uploadId)   // also removes the created video from the library
```

Pause and resume work on the TUS uploader only; on the plain one they are no-ops, which is what
`UploadEvent.Progress.pauseState` tells your UI before it offers the control
(`PauseState.Unsupported`).

Cancelling is not the same as walking away from the collector: abandoning a collector stops
watching, `cancelUpload` stops the transfer and deletes the partial video.

## Continue an interrupted upload

On a transient failure, `continueUpload` picks the transfer up from the offset the server already
has instead of re-sending the file. It needs the `videoId` and the same `Uri`, so persist both
alongside the upload id:

```kotlin
is UploadEvent.Failed -> event.videoId?.let { videoId ->
    if (!event.error.isTerminal) {
        observe(uploader.continueUpload(libraryId, videoId, videoUri))
    }
}
```

Continue on the **same uploader that started the upload**. Only the TUS one records an offset; on
the plain uploader the attempt fails immediately with `BunnyError.InvalidState` rather than quietly
re-sending everything.

## After the upload

`UploadEvent.Completed` means the bytes arrived. The video then goes through processing and
transcoding before it is playable; track that through the video's `status`
(see `VideoModelStatus` — `FINISHED` means playable).

## Gotchas

- Uploads survive navigation, **not process death**. If the app is killed the transfer stops. On
  the TUS path you can recover: persist the `videoId` and the `Uri` (take a persistable URI
  permission when you pick the file) and call `continueUpload` on the next launch.
- To keep an upload running while the app is away, collect it from a foreground service or a
  `WorkManager` job — the SDK does not start one for you.
- Progress is reported in whole percent, and only when it actually changes.

Working example: the upload flow in the [demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
