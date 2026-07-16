# Demo app

A Compose app that exercises every feature of the Bunny Stream Android SDK. Use it as the
reference implementation next to the [integration guides](../docs/guides/README.md).

## Run it

1. Open the repository in Android Studio.
2. Put your Bunny credentials in `local.properties` (repository root, not committed):

```properties
bunny.demo.accessKey=your-library-api-key
bunny.demo.libraryId=12345
# optional, only for libraries with token authentication enabled:
bunny.demo.tokenAuthKey=your-token-auth-key
```

3. Run the `app` configuration on a device or emulator.

The values land in `BuildConfig` of debug and staging builds; release builds ship empty and the
credentials can also be changed at runtime on the demo's configuration screen.

## What is where

| Demo screen | Shows | Guide |
|---|---|---|
| Video player | VOD playback, resume positions, icons | [Play a video](../docs/guides/play-a-video.md) |
| Video Upload | TUS upload with pause/resume/cancel | [Upload videos](../docs/guides/upload-videos.md) |
| Camera upload | recording the camera to the library | [Go live from the camera](../docs/guides/go-live-from-the-camera.md) |
| Manage live streams | create/schedule/edit streams, thumbnails, trailer, watch | [Manage live streams](../docs/guides/manage-live-streams.md) |
| Live stream player (Watch) | the live player in every state | [Play a live stream](../docs/guides/play-a-live-stream.md) |
| Go live | broadcasting to a live stream, badges, dual publish | [Go live from the camera](../docs/guides/go-live-from-the-camera.md) |
| Direct video play | playback by raw video id | [Play a video](../docs/guides/play-a-video.md) |
| Resume positions | managing saved positions | [Play a video](../docs/guides/play-a-video.md) |

## Note on token signing

For token-protected libraries the demo signs playback tokens on the device so it can run without
a backend. Real apps should sign tokens server-side - see
[Secure playback](../docs/guides/secure-playback.md).
