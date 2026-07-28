# BunnyStreamApi

The core package that provides interface to Bunny's REST Stream API. It handles all API communication, request authentication, and response parsing, allowing you to easily manage your video content, retrieve analytics, and control CDN settings. Features include video management, collection organization, and thumbnail generation.

## Minimum supported Android version

- Android 8.0 (API level 26)

## Installation

Declare desired dependencies in your project's `build.gradle.kts`:
```
implementation("net.bunny:api:latest.release")
```

## Initialization

After installation, you'll need to configure the package with your Bunny credentials.

```kotlin
// Initialize with your access key (optional) and library ID
BunnyStreamApi.initialize(context, accessKey, libraryId)
```

## 1. Getting Started with video management using BunnyStreamApi

BunnyStreamApi.initialize(context, accessKey, libraryId)

### List videos from library

 ```
 try {
    val response: PaginationListOfVideoModel = BunnyStreamApi.videosApi.videoList(
        libraryId = libraryId
    )
    println("response=$response")
} catch (e: Exception) {
    // handle exception
}
 ```

### Create a video

 ```
 val createVideoRequest = VideoCreateVideoRequest(
    title = title,
    collectionId = collectionId,
    thumbnailTime = thumbnailTime
)
try {
    val result: VideoModel = BunnyStreamApi.videosApi.videoCreateVideo(
        libraryId = libraryId,
        videoCreateVideoRequest = createVideoRequest
    )
    println("result=$result")
} catch (e: Exception) {
    // handle exception
}
 ```

### Upload video

An upload is addressed by an **upload id**: `startUpload` begins the transfer and returns it,
`observeUpload` streams that upload's events. The transfer runs inside the SDK, so it keeps going
when the screen that started it goes away — keep the id somewhere that outlives the screen and
re-attach with `observeUpload` on the way back in.

```kotlin
val uploader = BunnyStreamApi.getInstance().videoUploader   // or tusVideoUploader
val uploadId = uploader.startUpload(libraryId, videoUri)
store.activeUpload = uploadId

lifecycleScope.launch {
    uploader.observeUpload(uploadId)?.collect { event ->
        when (event) {
            is UploadEvent.Started   -> Log.d(TAG, "started, videoId=${event.videoId}")
            is UploadEvent.Progress  -> showProgress(event.percentage, event.pauseState)
            is UploadEvent.Completed -> showDone(event.videoId)
            is UploadEvent.Cancelled -> dismiss()
            is UploadEvent.Failed    -> showError(event.error.message)
        }
    }
}
```

Failures arrive as an `UploadEvent.Failed` value carrying a typed `BunnyError`, not as a thrown
exception. `pauseUpload`, `resumeUpload` and `cancelUpload` all take the upload id; pausing works
only on the resumable uploader, which is what `UploadEvent.Progress.pauseState` tells the UI.

#### Resumable (TUS) uploads

`tusVideoUploader` sends the file in chunks, which is what makes pausing and resuming possible. Use
it for large files and unreliable networks.

An interrupted upload is continued with `continueUpload`, on the same uploader that started it —
it needs the `videoId` from the event stream plus the same content URI, so persist both:

```kotlin
is UploadEvent.Failed -> if (!event.error.isTerminal && event.videoId != null) {
    val retryId = tusVideoUploader.continueUpload(libraryId, event.videoId, videoUri)
    observe(retryId)
}
```

Uploads survive navigation, not process death. To keep one running while the app is away, collect
it from a foreground service or `WorkManager` job.
### Full API reference

- [Collections API](../docs/ManageCollectionsApi.md)
- [Videos API](../docs/ManageVideosApi.md)

Bunny Stream Android is licensed under the [MIT License](LICENSE). See the LICENSE file for more details.