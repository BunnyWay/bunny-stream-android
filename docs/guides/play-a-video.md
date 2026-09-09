# Play a video

Embed the player and play a video from your library.

## Prerequisites

- `net.bunny:player` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)

## Basic playback

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
@Composable
fun VideoPlayer(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> BunnyStreamPlayer(context) },
        update = { player -> player.playVideo(videoId) },
        modifier = modifier,
    )
}
```

That is the whole integration. The player fetches the video, picks the right rendition, and shows
its controls: play/pause, seek bar with preview thumbnails, chapters and moments, captions,
quality and speed menus, fullscreen, Chromecast and Picture-in-Picture.

For videos in a token-protected library pass the token too - see
[Secure playback](secure-playback.md):

```kotlin
player.playVideo(videoId, token = token, expires = expires)
```

## Appearance

Colors, captions styling and most other appearance options are configured per library in the Bunny
dashboard (Stream > your library > Player), not in code. The player applies them automatically.
(The dashboard's list of visible controls is applied to live playback; VOD shows the full control
bar.)

Code-side there is the icon set:

```kotlin
player.iconSet = PlayerIconSet(
    playIcon = R.drawable.my_play,
    pauseIcon = R.drawable.my_pause,
)
```

To replace the controls entirely rather than restyle them, see
[Your own controls instead of the built-in ones](#your-own-controls-instead-of-the-built-in-ones).

## Resume positions

Let viewers continue where they left off. Positions are stored on the device:

```kotlin
player.enableResumePosition(ResumeConfig()) { position, resume ->
    // Called when a saved position exists. Ask the user, then decide:
    resume(true)   // continue from position.position
    // resume(false) starts from the beginning
}
```

The callback decides whether playback jumps; without it positions are still saved, but playback
starts from the beginning. `ResumeConfig` controls retention (default 7 days), minimum watch
time and the auto-save interval. `clearSavedPosition(videoId)` and `clearAllSavedPositions()`
cover cleanup.

## Playback speed

```kotlin
player.setPlaybackSpeedConfig(
    PlaybackSpeedConfig(
        defaultSpeed = 1.0f,
        rememberLastSpeed = true,
    )
)
```

Without this call the player offers the speeds configured in the dashboard.

## Progress from your own UI

```kotlin
player.setProgressListener(object : BunnyPlayer.ProgressListener {
    override fun onProgressChanged(position: Long, duration: Long, progress: Float) {
        // position and duration in milliseconds, progress 0..1
    }
})
```

## Your own controls instead of the built-in ones

Turn the built-in control bar off to get a bare video surface and drive playback yourself. Set it
before starting playback:

```kotlin
val player = BunnyStreamPlayer(context)
player.controlsEnabled = false
player.playVideo(videoId)

// drive it from your own UI
myPlayButton.setOnClickListener { player.play() }
myPauseButton.setOnClickListener { player.pause() }
mySeekBar.setOnSeekBarChangeListener(/* ... */)   // player.seekTo(positionMs)
```

Nothing is drawn over the video and taps on it do nothing, so your own overlay is free to handle
them. Combine it with the progress listener above to render your own timeline.

Everything below the chrome keeps working: DRM, resume positions, captions, watermark, playback
speed and CDN telemetry.

Drive your UI from the player's callbacks, not from your own taps - playback changes for reasons
your buttons never see (the video ends, the engine restores the remembered speed on a new video,
Chromecast takes over):

```kotlin
player.onPlayingChanged = { playing -> playButton.isSelected = playing }
player.onLoadingChanged = { loading -> spinner.isVisible = loading }
player.onMutedChanged = { muted -> muteButton.isSelected = muted }
player.onPlaybackSpeedChanged = { speed -> speedLabel.text = "${speed}x" }
player.onPlayerTypeChanged = { type -> /* DEFAULT_PLAYER or CAST_PLAYER */ }
player.onPlaybackError = { message -> showYourOwnError(message) }
```

`onChaptersUpdated`, `onMomentsUpdated` and `onRetentionGraphUpdated` deliver the same data the
built-in seek bar uses, for a custom timeline. `onVideoSizeChanged` gives the real aspect ratio.

Commands and current state: `play()`, `pause()`, `seekTo()`, `mute()`, `unmute()`, `isPlaying()`,
`isMuted()`, `playbackSpeed` (read and write) and `getPlaybackSpeeds()`.

What you take over: everything the control bar drew. That includes the live badge, the cast button
and the entry points to fullscreen and Picture-in-Picture, so a custom UI has to provide its own.

## Gotchas

- One playback engine is shared per process: use one player view at a time and detach it before
  starting playback in another.
- Playback stops when the view is detached from the window.
- Fullscreen opens a separate screen provided by the SDK; nothing to configure.
- Picture-in-Picture needs a flag on your activity, see
  [Picture-in-Picture and Chromecast](picture-in-picture-and-cast.md).
- In Compose, do not put the player inside a `verticalScroll` container. The scroll gesture
  detector swallows taps aimed at the `AndroidView`, so tapping the video never brings the controls
  up - playback looks fine, the controls just never appear. Keep the player outside the scrolling
  area and scroll only the content below it:

  ```kotlin
  Column(Modifier.fillMaxSize()) {
      BunnyPlayerComposable(...)                              // outside the scroll
      Column(Modifier.verticalScroll(rememberScrollState())) {
          // the rest of your screen
      }
  }
  ```

Working example: `PlayerScreen` in the [demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
