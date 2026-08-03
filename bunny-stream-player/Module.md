# Module BunnyStreamPlayer

Playback module of the Bunny Stream Android SDK (`net.bunny:player`).

Two entry points:

- `BunnyStreamPlayer` - a `FrameLayout` for video-on-demand playback. Add it to a layout (or wrap
  it in `AndroidView` from Compose) and call `playVideo(videoId)`. Ships its own controls,
  fullscreen, Chromecast, Picture-in-Picture, captions, chapters and resume positions.
- `BunnyLiveStreamPlayer` - a composable for live streams. Give it a `libraryId` and `streamId`
  and it handles the rest: countdown before a scheduled stream, pre-stream trailer, offline
  screen, automatic connect when the stream goes live, DVR seeking and the switch to the
  recording after the stream ends. Player appearance is configured in the Bunny dashboard,
  not in code.

Playback needs an SDK instance from the `:api` module — either the default one registered by
`BunnyStreamApi.initialize(...)`, or one passed directly through `BunnyStreamPlayer.bunny` / the
`bunny` parameter of `BunnyLiveStreamPlayer` for apps that address more than one library. Leaving
`bunny` unset uses the default instance.

Integration guides with copy-paste examples live in the repository under
[docs/guides](https://github.com/BunnyWay/bunny-stream-android/tree/main/docs/guides).
