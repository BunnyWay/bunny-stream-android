//[BunnyStreamCameraUpload](../../../index.md)/[net.bunny.bunnystreamcameraupload](../index.md)/[StreamCameraUploadView](index.md)

# StreamCameraUploadView

interface [StreamCameraUploadView](index.md)

#### Inheritors

| |
|---|
| [BunnyStreamCameraUpload](../-bunny-stream-camera-upload/index.md) |

## Properties

| Name | Summary |
|---|---|
| [closeStreamClickListener](close-stream-click-listener.md) | [androidJvm]<br>abstract var [closeStreamClickListener](close-stream-click-listener.md): [View.OnClickListener](https://developer.android.com/reference/kotlin/android/view/View.OnClickListener.html)?<br>Click listener to receive close clicked event so you can handle it, e.g. finish hosting activity or navigate to some other screen |
| [hideDefaultControls](hide-default-controls.md) | [androidJvm]<br>abstract var [hideDefaultControls](hide-default-controls.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>Hides default controls, if you plan to use your own |
| [streamDurationListener](stream-duration-listener.md) | [androidJvm]<br>abstract var [streamDurationListener](stream-duration-listener.md): [RecordingDurationListener](../-recording-duration-listener/index.md)?<br>Listener to receive stream duration |
| [streamStateListener](stream-state-listener.md) | [androidJvm]<br>abstract var [streamStateListener](stream-state-listener.md): [RecordingStateListener](../-recording-state-listener/index.md)?<br>Listener to receive events about stream status |

## Functions

| Name | Summary |
|---|---|
| [isRecording](is-recording.md) | [androidJvm]<br>abstract fun [isRecording](is-recording.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>Check if streaming is in progress |
| [setAudioMuted](set-audio-muted.md) | [androidJvm]<br>abstract fun [setAudioMuted](set-audio-muted.md)(muted: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html))<br>Mutes/un-mutes audio |
| [startPreview](start-preview.md) | [androidJvm]<br>abstract fun [startPreview](start-preview.md)()<br>Starts camera preview |
| [stopRecording](stop-recording.md) | [androidJvm]<br>abstract fun [stopRecording](stop-recording.md)()<br>Stops streaming |
| [switchCamera](switch-camera.md) | [androidJvm]<br>abstract fun [switchCamera](switch-camera.md)()<br>Switches stream camera |