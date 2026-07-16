# Play a live stream

Show a live stream to viewers with one composable.

## Prerequisites

- `net.bunny:player` dependency
- `BunnyStreamApi.initialize(...)` called, see [Getting started](getting-started.md)
- Jetpack Compose (the live player is Compose-only)
- The stream's GUID, from the dashboard or from
  [Manage live streams](manage-live-streams.md)

## Basic playback

```kotlin
BunnyLiveStreamPlayer(
    libraryId = 12345L,
    streamId = "stream-guid",
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
)
```

`libraryId` must be the same library the SDK was initialized with.

## What the player does for you

The composable renders the correct screen for the stream's state and moves between states on its
own; you never refresh or reconnect manually:

- Scheduled stream: a countdown to the start time, over the stream thumbnail.
- Pre-stream trailer configured: the trailer plays in a muted loop instead of the thumbnail.
- Encoder connected, stream started: live playback with the standard controls and a LIVE badge.
- Stream with DVR: viewers can pause and seek back within the DVR window, and jump back to the
  live edge with one tap. Without DVR the timeline is hidden.
- Playback hiccup mid-stream: the player recovers on its own.
- Stream ends: if the stream records a VOD, the player switches to the recording, with a normal
  seek bar.
- Stream offline or over: an offline screen with the stream thumbnail.

The player checks the stream status every few seconds and connects automatically the moment the
stream goes live.

## Reading stream state from your UI

To show your own metadata next to the player (title, viewer count, status), read the view model's
state flows:

```kotlin
val viewModel: BunnyLiveStreamPlayerViewModel = viewModel()

BunnyLiveStreamPlayer(
    libraryId = libraryId,
    streamId = streamId,
    viewModel = viewModel,
)

val stream by viewModel.liveStream.collectAsStateWithLifecycle()
Text(stream?.title.orEmpty())
```

## Vertical (9:16) streams

The player reports the real video size, so your container can adapt:

```kotlin
var aspect by remember { mutableStateOf(16f / 9f) }

BunnyLiveStreamPlayer(
    libraryId = libraryId,
    streamId = streamId,
    onVideoSizeChanged = { w, h -> if (w > 0 && h > 0) aspect = w.toFloat() / h },
    modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
)
```

## Appearance

The live player is configured from the Bunny dashboard (Stream > your library > Player): accent
color, font, UI language, which controls are visible, compact mode. There is no code-side
configuration object; change the dashboard and the player follows.

## Token-protected libraries

Pass a playback token, same as for videos - see [Secure playback](secure-playback.md):

```kotlin
BunnyLiveStreamPlayer(libraryId, streamId, token = token, expires = expires)
```

## Gotchas

- An ended stream cannot go live again. The player will show the recording (when the stream
  recorded one) or the offline screen. Start a new stream for a new broadcast.
- The countdown appears only for streams with a scheduled start time.
- Casting and Picture-in-Picture follow the same host-app requirements as the video player, see
  [Picture-in-Picture and Chromecast](picture-in-picture-and-cast.md).

Working example: `LiveStreamPlayerScreen` in the [demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md).
