# Camera recording and upload using BunnyStreamCameraUpload

Integrated camera solution that enables recording and direct upload of videos from the device camera.

## Installation

Declare dependency in your project's `build.gradle.kts`:
```
implementation("net.bunny:recording:1.0.0")
```

## Initialization

After installation, you'll need to configure the package with your Bunny credentials. Initialization is common for all modules.

```kotlin
// Initialize with your access key (optional) and library ID
BunnyStreamApi.initialize(context, accessKey, libraryId)
```

**`BunnyStreamCameraUpload` requires `CAMERA` and `RECORD_AUDIO` permissions:**

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

1. Add `BunnyRecordingView` to your layout

```xml
<net.bunnystream.recording.BunnyStreamCameraUpload
      android:id="@+id/recordingView"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      app:brvDefaultCamera="front"
      />
```

2. Set close listener:
```
recordingView.closeStreamClickListener = OnClickListener {
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

If you don't want to use default UI controls you can hide them using `hideDefaultControls()` and control the streaming by calling functions from `StreamCameraUploadView` interface that `BunnyRecordingView` implements.

Full usage example and permissions handling can be found in demo app.

Checkout [class level documentation](docs/index.md)

## License

Bunny Stream Android is licensed under the [MIT License](LICENSE). See the LICENSE file for more details.
