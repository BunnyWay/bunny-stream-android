# Upload videos

Upload files from the device to your library.

## Prerequisites

- `net.bunny:api` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)
- A content `Uri` of the file to upload (from the system picker, camera, etc.)

## Pick an uploader

Two uploaders, same interface:

- `tusVideoUploader` - chunked upload (TUS) with pause and resume while the upload is running.
  Use this one unless you have a reason not to.
- `videoUploader` - a plain single-request upload. Simpler, but pause and resume are not
  supported.

## Upload

```kotlin
BunnyStreamApi.getInstance().tusVideoUploader.uploadVideo(
    libraryId,
    videoUri,
    object : UploadListener {
        override fun onUploadStarted(uploadId: String, videoId: String) {
            // keep uploadId if you want to pause or cancel later
        }

        override fun onProgressUpdated(percentage: Int, videoId: String, pauseState: PauseState) {
            // 0..100
        }

        override fun onUploadDone(videoId: String) {
            // the video is uploaded; Bunny processes it before it becomes playable
        }

        override fun onUploadError(error: UploadError, videoId: String?) {
            // see UploadError for the cases (Unauthorized, VideoNotFound, ...)
        }

        override fun onUploadCancelled(videoId: String) {}
    },
)
```

The SDK creates the video object in your library for you and deletes it again when you cancel.

Callbacks arrive on a background thread. Touch your UI through your usual main-thread route
(`runOnUiThread`, a coroutine dispatcher, `post`, ...).

<!-- TODO before the 4.0.0 release: update this page to the final 4.0.0 upload API. -->

## Pause, resume, cancel

```kotlin
val uploader = BunnyStreamApi.getInstance().tusVideoUploader
uploader.pauseUpload(uploadId)
uploader.resumeUpload(uploadId)
uploader.cancelUpload(uploadId)   // also removes the created video from the library
```

`uploadId` comes from `onUploadStarted`. Pause and resume work on the TUS uploader only.

## After the upload

`onUploadDone` means the bytes arrived. The video then goes through processing and transcoding
before it is playable; track that through the video's `status`
(see `VideoModelStatus` - `FINISHED` means playable).

## Gotchas

- Uploads run while the app is in the foreground. There is no background transfer service, and
  an upload does not survive a process kill or app restart - the user starts it again.
- Progress is reported in whole percent.

Working example: the upload flow in the [demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
