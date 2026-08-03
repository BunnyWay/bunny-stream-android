# bunny-stream-player

Playback module of the Bunny Stream Android SDK (`net.bunny:player`), built on media3/ExoPlayer.
Two entry points: `BunnyStreamPlayer` for videos and `BunnyLiveStreamPlayer` for live streams.

## Installation

```kotlin
implementation("net.bunny:player:latest.release")
```

Requires Android 8.0 (API 26) and the `INTERNET` permission in your manifest.

## Initialization

```kotlin
BunnyStreamApi.initialize(context, accessKey = "your-api-key", libraryId = 12345L)
```

Without this call the player renders a black view and logs an error.

To play from a specific library in an app that uses several, give the view its own instance:

```kotlin
videoPlayer.bunny = BunnyStreamApi.create(context, BunnyStreamConfig(key, libraryId = 12345L))

// Compose, for live streams
BunnyLiveStreamPlayer(libraryId, streamId, bunny = marketing)
```

Leave `bunny` unset and the view uses the instance `initialize` registered.

## Play a video

XML:

```xml
<net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer
    android:id="@+id/videoPlayer"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
binding.videoPlayer.playVideo(videoId = "your-video-guid")
```

Compose:

```kotlin
AndroidView(
    factory = { context -> BunnyStreamPlayer(context) },
    update = { it.playVideo(videoId) },
)
```

The player brings its own controls: play/pause, seek bar with preview thumbnails, chapters,
moments, captions, quality and speed menus, fullscreen, Chromecast, Picture-in-Picture. Resume
positions and speed behaviour are opt-in - see [Play a video](../docs/guides/play-a-video.md).

## Play a live stream

Compose-only:

```kotlin
BunnyLiveStreamPlayer(libraryId = 12345L, streamId = "stream-guid")
```

The live player follows the stream on its own: countdown for scheduled streams, a looping muted
pre-stream trailer, automatic connect when the stream goes live, DVR seeking, recovery after
hiccups, and the switch to the recording once the stream ends. Details:
[Play a live stream](../docs/guides/play-a-live-stream.md).

## Appearance

Colors, visible controls, captions styling and the player language come from the library's
player settings in the Bunny dashboard. In code you can replace the control icons:

```kotlin
binding.videoPlayer.iconSet = PlayerIconSet(
    playIcon = R.drawable.my_play,
    pauseIcon = R.drawable.my_pause,
)
```

## Picture-in-Picture

The player shows a PiP button whenever the player settings include the `pip` control and the
device supports it. **Entering PiP requires opt-in from the host Activity** - without it the
button is a silent no-op. Add to the Activity that hosts the player (and keep `configChanges` so
the window resize doesn't recreate it):

```xml
<activity
    android:name=".YourPlayerActivity"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" />
```

The SDK handles the rest: playback keeps running inside the PiP window, the window uses the
video's real aspect ratio, and dismissing the window pauses playback.

## Guides

- [Play a video](../docs/guides/play-a-video.md)
- [Play a live stream](../docs/guides/play-a-live-stream.md)
- [Secure playback](../docs/guides/secure-playback.md) (tokens, hotlink protection)
- [Picture-in-Picture and Chromecast](../docs/guides/picture-in-picture-and-cast.md)
- [Troubleshooting](../docs/guides/troubleshooting.md)

## Reference

[API reference](https://bunnyway.github.io/bunny-stream-android/api/) (generated from the source)

## License

Bunny Stream Android is licensed under the [MIT License](../LICENSE).
