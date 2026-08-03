# Module BunnyStreamCameraUpload

Camera capture module of the Bunny Stream Android SDK (`net.bunny:recording`).

One view, two jobs. `BunnyStreamCameraUpload` is a `FrameLayout` that streams the device camera
over RTMP:

- With no extra setup it records straight to a new video in your library.
- Set `liveStreamId` before starting and the same view broadcasts to an existing live stream
  instead, including automatic server-side start and stop, primary and backup ingest badges and
  reconnect handling.

The host app must request the `CAMERA` and `RECORD_AUDIO` runtime permissions before calling
`startPreview()` - without them the call logs a warning and does nothing.

Requires `BunnyStreamApi.initialize(...)` from the `:api` module. The view also takes an SDK
instance of its own through `bunny`, for apps that record into more than one library; leaving it
unset uses the instance `initialize` registered. The library is read when a recording starts, so
the view can be inflated before the SDK is ready.

Integration guides with copy-paste examples live in the repository under
[docs/guides](https://github.com/BunnyWay/bunny-stream-android/tree/main/docs/guides).
