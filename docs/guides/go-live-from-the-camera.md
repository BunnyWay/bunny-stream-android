# Go live from the camera

Broadcast the device camera to a Bunny live stream.

## Prerequisites

- `net.bunny:recording` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)
- A live stream to broadcast to - create one in the dashboard or via
  [Manage live streams](manage-live-streams.md)
- `CAMERA` and `RECORD_AUDIO` granted at runtime (see below)

## 1. Add the view

```xml
<net.bunny.bunnystreamcameraupload.BunnyStreamCameraUpload
    android:id="@+id/cameraUpload"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:brvDefaultCamera="front" />
```

## 2. Request permissions, then start the preview

`startPreview()` does not request permissions and does not report an error when they are
missing - it logs a warning and does nothing. Request them first and call it again after the
grant:

```kotlin
private val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) binding.cameraUpload.startPreview()
    }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...
    if (permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }) {
        binding.cameraUpload.startPreview()
    } else {
        permissionLauncher.launch(permissions)
    }
}
```

## 3. Point the view at your live stream

```kotlin
binding.cameraUpload.liveStreamId = "stream-guid"   // set BEFORE going live
```

That is the whole switch: with `liveStreamId` set the view broadcasts to the stream instead of
recording a new video. The SDK resolves the RTMP ingest, starts the stream on the server once the
connection is up (viewers see it go live), reconnects on network drops with automatic failover
between the primary and backup ingest, and shows connection badges in the built-in controls.

Leave `liveStreamId` unset and the same view records the camera to a new video in your library.

## 4. Stop

```kotlin
binding.cameraUpload.stopRecording()
```

For a live stream this also ends it on the server. Stop through this call, not by killing the
screen; otherwise the stream stays live for viewers until the server times it out. Remember that
an ended stream cannot be restarted - create a new stream for the next broadcast.

## Listening to broadcast events

```kotlin
binding.cameraUpload.streamStateListener = object : RecordingStateListener {
    override fun onStreamInitializing() {}
    override fun onStreamConnected() { /* went live */ }
    override fun onStreamDisconnected() { /* connection lost, SDK reconnects */ }
    override fun onStreamStopped() { /* broadcast over */ }
    override fun onStreamAuthError() { /* wrong access key or stream key */ }
    override fun onStreamConnectionFailed(message: String) { /* gave up after retries */ }
    override fun onCameraChanged(deviceCamera: DeviceCamera) {}
    override fun onAudioMuted(muted: Boolean) {}

    override fun onIngestEndpointChanged(endpoint: IngestEndpoint, state: IngestEndpointState) {
        // drive your own Primary/Backup badges here
    }
}
binding.cameraUpload.streamDurationListener = object : RecordingDurationListener {
    override fun onDurationUpdated(durationMillis: Long, durationFormatted: String) {
        // durationFormatted is "hh:mm:ss"
    }
}
```

Setting `streamStateListener` also disables the SDK's built-in error dialogs, so you can show
your own UI.

## Dual publish

```kotlin
binding.cameraUpload.dualPublish = true   // set BEFORE startPreview()
```

Publishes to the primary and backup ingest at the same time, so a failover is instant. This
doubles the upload bandwidth, so it is off by default; warn the user before enabling it on
mobile data. Streams without a backup ingest fall back to normal single publishing.

## Custom controls

Hide the built-in UI and drive the view yourself:

```kotlin
binding.cameraUpload.hideDefaultControls = true
// then use startPreview / stopRecording / switchCamera / setAudioMuted / isRecording
```

## Gotchas

- The view locks the activity's orientation while it is active.
- Broadcasting from the emulator works (virtual camera), but test on a device for real encoder
  performance.
- The SDK does not ask for permissions; that flow stays in your app (step 2).
Working example: `GoLiveActivity` (live) and `RecordingActivity` (record to VOD) in the
[demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
