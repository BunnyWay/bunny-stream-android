# BunnyStreamApi

The core package that provides interface to Bunny's REST Stream API. It handles all API communication, request authentication, and response parsing, allowing you to easily manage your video content, retrieve analytics, and control CDN settings. Features include video management, collection organization, and thumbnail generation.

## Minimum supported Android version

- Android 8.0 (API level 26)

## Installation

Declare desired dependencies in your project's `build.gradle.kts`:
```
implementation("net.bunny:api:1.0.0")
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

#### Uploading Using session uploader

```kotlin
BunnyStreamApi.getInstance().videoUploader.uploadVideo(libraryId, videoUri, object : UploadListener {
    override fun onUploadError(error: UploadError, videoId: String?) {
        Log.d(TAG, "onVideoUploadError: $error")
    }

    override fun onUploadDone(videoId: String) {
        Log.d(TAG, "onVideoUploadDone")
    }

    override fun onUploadStarted(uploadId: String, videoId: String) {
        Log.d(TAG, "onVideoUploadStarted: uploadId=$uploadId")
    }

    override fun onProgressUpdated(percentage: Int, videoId: String) {
        Log.d(TAG, "onUploadProgress: percentage=$percentage")
    }

    override fun onUploadCancelled(videoId: String) {
        Log.d(TAG, "onUploadProgress: onVideoUploadCancelled")
    }
})
```

#### Using TUS resumable uploader

If TUS upload gets interrupted, calling `uploadVideo` with same parameters will resume upload.

```kotlin
BunnyStreamApi.getInstance().tusVideoUploader.uploadVideo(libraryId, videoUri, object : UploadListener {
    override fun onUploadError(error: UploadError, videoId: String?) {
        Log.d(TAG, "onVideoUploadError: $error")
    }

    override fun onUploadDone(videoId: String) {
        Log.d(TAG, "onVideoUploadDone")
    }

    override fun onUploadStarted(uploadId: String, videoId: String) {
        Log.d(TAG, "onVideoUploadStarted: uploadId=$uploadId")
    }

    override fun onProgressUpdated(percentage: Int, videoId: String) {
        Log.d(TAG, "onUploadProgress: percentage=$percentage")
    }

    override fun onUploadCancelled(videoId: String) {
        Log.d(TAG, "onUploadProgress: onVideoUploadCancelled")
    }
})
```

#### Cancel video upload
```
BunnyStreamApi.getInstance().videoUploader.cancelUpload(uploadId)
```
or

```
BunnyStreamApi.getInstance().tusVideoUploader.cancelUpload(uploadId)
```

`uploadId` comes from `onUploadStarted(uploadId: String, videoId: String)` callback function.

Full example can be found in demo app.

### Full API reference

- [Collections API](../docs/ManageCollectionsApi.md)
- [Videos API](../docs/ManageVideosApi.md)

Bunny Stream Android is licensed under the [MIT License](LICENSE). See the LICENSE file for more details.