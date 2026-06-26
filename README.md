# Bunny Stream Android

<p align="center">
  <img src="resources/bunnynet.svg" width="70%" alt="BunnyNet" />
</p>
<p align="center">
    <a href="./LICENSE" alt="License">
        <img src="https://img.shields.io/badge/Licence-MIT-green.svg" />
    </a>
</p>

## What is Bunny Stream?

Bunny Stream is an Android library designed to seamlessly integrate Bunny's powerful video streaming capabilities into your Android applications. The package provides a robust set of tools for video management, playback, uploading, and camera-based video uploads, all through an intuitive Kotlin API.

### Key Features

- **Complete API Integration**: Full support for Bunny REST Stream API
- **Efficient Video Upload**: TUS protocol implementation for reliable, resumable uploads
- **Advanced Video Player**: Custom-built player with full Bunny CDN integration
- **Camera Upload Support**: Built-in capabilities for recording and uploading videos directly from device camera

## Modules

The Bunny Stream is organized into several specialized modules, each focusing on specific functionality:

| Module                                                                 | Description                                                                                                                                                                                                                                                                                                                               |
|------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **[bunny-stream-api](bunny-stream-api/README.md)**                     | The core module that provides interface to Bunny's REST Stream API. It handles all API communication, request authentication, and response parsing, allowing you to easily manage your video content, retrieve analytics, and control CDN settings. Features include video management, collection organization, and thumbnail generation. |
| **[bunny-stream-camera-upload](bunny-stream-camera-upload/README.md)** | Integrated camera solution that enables recording and direct upload of videos from the device camera.                                                                                                                                                                                                                                     |
| **[bunny-stream-player](bunny-stream-player/README.md)**                            | A feature-rich video player specifically optimized for Bunny's CDN. It provides smooth playback with adaptive bitrate streaming, customizable controls, support for multiple video formats, and integration with Bunny's analytics. The player includes features like Google Cast support, DRM support and customizable UI elements.      |

## Minimum supported Android version

- Android 8.0 (API level 26)

## Installation

The Bunny Stream Android SDK is available on Maven Central. You can choose to use only the modules you need.

### Adding Dependencies

Declare the desired dependencies in your project's `build.gradle.kts`:

- **API only** (if you plan to use your own player implementation):
   ```kotlin
   implementation("net.bunny:api:latest.release")
   ```

- **Player** (includes API dependency):
   ```kotlin
   implementation("net.bunny:player:latest.release")
   ```

- **Camera recording and upload**:
   ```kotlin
   implementation("net.bunny:recording:latest.release")
   ```

- **All modules**:
   ```kotlin
   implementation("net.bunny:api:latest.release")
   implementation("net.bunny:player:latest.release")
   implementation("net.bunny:recording:latest.release")
   ```

### Repository Configuration

Since the artifacts are published to Maven Central, no additional repository configuration is needed. Maven Central is included by default in most Android projects.

If for some reason Maven Central is not configured in your project, add it to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}
```

## Initialization

After installation, you'll need to configure the package with your Bunny credentials. Initialization is common for all modules.

```kotlin
// Initialize with your access key (optional) and library ID
BunnyStreamApi.initialize(context, accessKey, libraryId)
```

## 1. Getting Started with video management using BunnyStreamApi

Initialize BunnyStreamApi:
```
BunnyStreamApi.initialize(context, accessKey, libraryId)
```

## Below are some BunnyStreamApi usage examples

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

Checkout full [API reference](api/README.md#full-api-reference) 

## 2. BunnyStreamPlayer - Video Playback

Before attempting video playback, make sure `BunnyStreamApi` is initialized  with your access key (optional) and library ID:
```
BunnyStreamApi.initialize(context, accessKey, libraryId)
```

#### Using `BunnyVideoPlayer` in Compose

```kotlin
@Composable
fun BunnyPlayerComposable(
    videoId: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            BunnyVideoPlayer(context)
        },
        update = {
            it.playVideo(videoId)
        },
        modifier = modifier.background(Color.Gray)
    )
}
```

Full usage example can be found in demo app.

#### Using `BunnyVideoPlayer` in XML Views

1. Add `BunnyVideoPlayer` into your layout:
```
<net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer
      android:id="@+id/videoPlayer"
      android:layout_width="match_parent"
      android:layout_height="match_parent"/>
```
2. Call `playVideo()`:
```
bunnyVideoPlayer.playVideo(videoId)
```

`bunnyVideoPlayer` comes from `findViewById()` or from View binding.

**Customizing Player:**

You can customize the BunnyVideoPlayer by passing custom icons. Other costumizations like primary color, font, handling control visibilty, captions, heatmap can be controlled from the Bunny dashboard.

1. Override icons you want to change from `PlayerIconSet` class:

```kotlin
@Parcelize
data class PlayerIconSet(
    @DrawableRes
    val playIcon: Int = R.drawable.ic_play_48dp,

    @DrawableRes
    val pauseIcon: Int = R.drawable.ic_pause_48dp,

    @DrawableRes
    val rewindIcon: Int = R.drawable.ic_replay_10s_48dp,

    @DrawableRes
    val forwardIcon: Int = R.drawable.ic_forward_10s_48dp,

    @DrawableRes
    val settingsIcon: Int = R.drawable.ic_settings_24dp,

    @DrawableRes
    val volumeOnIcon: Int = R.drawable.ic_volume_on_24dp,

    @DrawableRes
    val volumeOffIcon: Int = R.drawable.ic_volume_off_24dp,

    @DrawableRes
    val fullscreenOnIcon: Int = R.drawable.ic_fullscreen_24dp,

    @DrawableRes
    val fullscreenOffIcon: Int = R.drawable.ic_fullscreen_exit_24dp,
) : Parcelable
```
2. Set new icon set:
```
bunnyVideoPlayer.iconSet = newIconSet
```

## 3. Camera recording and upload using BunnyStreamCameraUpload

#### `BunnyStreamCameraUpload` requires `CAMERA` and `RECORD_AUDIO` permissions:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

1. Add `BunnyStreamCameraUpload` to your layout

```xml
<net.bunny.bunnystreamcameraupload.BunnyStreamCameraUpload
      android:id="@+id/recordingView"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      app:brvDefaultCamera="front"
      />
```

2. Set close listener:
```
bunnyStreamCameraUpload.closeStreamClickListener = OnClickListener {
    // Hanlde stream close event, e.g. finish currenty activity
    finish()
}
```

3. Check for mic and camera permissions and start preview:

```
private fun hasPermission(permissionId: String): Boolean {
    val permission = ContextCompat.checkSelfPermission(this, permissionId)
    return permission == PackageManager.PERMISSION_GRANTED
}

val camGranted = hasPermission(Manifest.permission.CAMERA)
val micGranted = hasPermission(Manifest.permission.RECORD_AUDIO)

if(camGranted && micGranted){
    recordingView.startPreview()
} else {
    val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    requestPermissions(
        permissions.toTypedArray(),
        PERMISSIONS_REQUEST_CODE
    )
}
```

If you don't want to use default UI controls you can hide them using `hideDefaultControls()` and control the streaming by calling functions from `RecordingView` interface that `BunnyRecordingView` implements.

Full usage example and permissions handling can be found in demo app.

## License

Bunny Stream Android is licensed under the [MIT License](LICENSE). See the LICENSE file for more details.