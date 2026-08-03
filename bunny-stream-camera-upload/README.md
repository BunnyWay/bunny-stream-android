# bunny-stream-camera-upload

Camera capture module of the Bunny Stream Android SDK (`net.bunny:recording`). One view,
`BunnyStreamCameraUpload`, that either records the device camera to a new video in your library
or broadcasts it live to an existing live stream.

## Installation

```kotlin
implementation("net.bunny:recording:latest.release")
```

Requires Android 8.0 (API 26). The module declares the `CAMERA` and `RECORD_AUDIO` permissions in
its manifest, but your app must request them at runtime - `startPreview()` does nothing without
them.

## Initialization

```kotlin
BunnyStreamApi.initialize(context, accessKey = "your-api-key", libraryId = 12345L)
```

The view reads the library when a recording starts, so it is safe to inflate it before this call
runs. To record into a specific library in an app that uses several, give the view its own
instance:

```kotlin
cameraUpload.bunny = BunnyStreamApi.create(context, BunnyStreamConfig(key, libraryId = 12345L))
```

## Record to your library

```xml
<net.bunny.bunnystreamcameraupload.BunnyStreamCameraUpload
    android:id="@+id/cameraUpload"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:brvDefaultCamera="front" />
```

```kotlin
// after CAMERA + RECORD_AUDIO are granted:
binding.cameraUpload.startPreview()
```

The built-in controls handle start, stop, mute and camera switching. Recording produces a new
video in your library.

## Broadcast to a live stream

Set the stream id before going live - everything else stays the same:

```kotlin
binding.cameraUpload.liveStreamId = "stream-guid"
```

The SDK starts the stream on the server once connected, ends it on stop, reconnects on network
drops with primary/backup failover, and shows connection badges. Full flow, listeners and the
dual-publish option: [Go live from the camera](../docs/guides/go-live-from-the-camera.md).

## Custom UI

Set `hideDefaultControls = true` (or the `brvHideDefaultControls` XML attribute) and drive the
view through the `StreamCameraUploadView` interface: `startPreview`, `stopRecording`,
`switchCamera`, `setAudioMuted`, `isRecording`.

## Guides

- [Go live from the camera](../docs/guides/go-live-from-the-camera.md)
- [Manage live streams](../docs/guides/manage-live-streams.md) (create the stream you broadcast to)
- [Troubleshooting](../docs/guides/troubleshooting.md)

## Reference

[API reference](https://bunnyway.github.io/bunny-stream-android/api/) (generated from the source)

## License

Bunny Stream Android is licensed under the [MIT License](../LICENSE).
