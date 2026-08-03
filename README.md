<!-- TODO before the repo goes public: this README documents the 4.0.0 feature set (live
     streaming). Publish it together with the 4.0.0 release so the described features match
     what is on Maven Central. -->

# Bunny Stream Android

<p align="center">
  <img src="resources/bunnynet.svg" width="70%" alt="BunnyNet" />
</p>
<p align="center">
    <a href="./LICENSE" alt="License">
        <img src="https://img.shields.io/badge/License-MIT-green.svg" />
    </a>
    <a href="https://central.sonatype.com/search?q=g:net.bunny" alt="Maven Central">
        <img src="https://img.shields.io/maven-central/v/net.bunny/api" />
    </a>
    <a href="https://bunnyway.github.io/bunny-stream-android/api/" alt="API reference">
        <img src="https://img.shields.io/badge/API%20reference-Dokka-blue" />
    </a>
</p>

## What is Bunny Stream?

Bunny Stream is the Android SDK for [Bunny's](https://bunny.net) video platform. It covers the
whole content flow: manage the videos and live streams in your library, upload from the device,
play videos, play live streams, and broadcast live from the camera.

### Key features

- **Video playback**: a ready player with controls, captions, chapters, seek previews,
  Chromecast, Picture-in-Picture and resume positions
- **Live streaming**: a live player that handles countdowns, pre-stream trailers, DVR and the
  switch to the recording by itself, plus camera broadcasting with automatic reconnect and
  primary/backup failover
- **Uploads**: chunked TUS uploads with mid-upload pause and resume
- **Full REST API access**: videos, collections and live streams of your library

## Documentation

- [Integration guides](docs/guides/README.md) - task-oriented, copy-paste examples
  (published at [bunnyway.github.io/bunny-stream-android](https://bunnyway.github.io/bunny-stream-android/))
- [API reference](https://bunnyway.github.io/bunny-stream-android/api/) - generated from the source
- [Demo app](app/README.md) - every feature, runnable
- [Changelog](CHANGELOG.md)

## Requirements

- Android 8.0 (API level 26) or newer on the device
- `compileSdk` 36 or higher, JDK 17
- Kotlin 2.1 or newer
- Core library desugaring enabled, if you use `net.bunny:player` - see
  [Getting started](docs/guides/getting-started.md#requirements)
- A [Bunny Stream](https://bunny.net/stream/) video library

## Installation

The SDK ships on Maven Central as three artifacts. Use what you need:

```kotlin
dependencies {
    implementation("net.bunny:api:latest.release")        // management + uploads
    implementation("net.bunny:player:latest.release")     // playback (pulls in :api)
    implementation("net.bunny:recording:latest.release")  // camera + go-live (pulls in :api)
}
```

Replace `latest.release` with a concrete version for reproducible builds. Maven Central is
configured by default in Android projects; no extra repository setup is needed.

| Module | Artifact | What it does |
|---|---|---|
| [bunny-stream-api](bunny-stream-api/README.md) | `net.bunny:api` | REST API access (videos, collections, live streams), uploads, playback settings |
| [bunny-stream-player](bunny-stream-player/README.md) | `net.bunny:player` | Video player and live stream player |
| [bunny-stream-camera-upload](bunny-stream-camera-upload/README.md) | `net.bunny:recording` | Camera recording to the library and live broadcasting |

## Quickstart

Declare the INTERNET permission (the api and player artifacts do not declare it; the
recording artifact does):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Initialize once, in `Application.onCreate`:

```kotlin
BunnyStreamApi.initialize(context, accessKey = "your-api-key", libraryId = 12345L)
```

That registers a default instance the whole SDK reaches through `getInstance()`. If your app talks
to several libraries, create an instance per library instead and hand it to the views:

```kotlin
val marketing = BunnyStreamApi.create(context, BunnyStreamConfig(marketingKey, 12345L))
playerView.bunny = marketing
```

Play a video:

```kotlin
// XML: add net.bunny.bunnystreamplayer.ui.BunnyStreamPlayer to a layout, then
binding.videoPlayer.playVideo(videoId = "your-video-guid")
```

Play a live stream (Compose):

```kotlin
BunnyLiveStreamPlayer(libraryId = 12345L, streamId = "stream-guid")
```

Broadcast from the camera to a live stream:

```kotlin
// XML: add net.bunny.bunnystreamcameraupload.BunnyStreamCameraUpload to a layout, then
binding.cameraUpload.liveStreamId = "stream-guid"
binding.cameraUpload.startPreview()   // CAMERA + RECORD_AUDIO must be granted
```

Each of these has a guide with the full flow, prerequisites and gotchas:
[Getting started](docs/guides/getting-started.md),
[Play a video](docs/guides/play-a-video.md),
[Play a live stream](docs/guides/play-a-live-stream.md),
[Go live from the camera](docs/guides/go-live-from-the-camera.md),
[Upload videos](docs/guides/upload-videos.md),
[Manage live streams](docs/guides/manage-live-streams.md).

## Chromecast

The player casts to the Bunny Stream receiver application - the same receiver the web player
uses - which plays every Bunny asset, including fMP4 HLS and Widevine-protected videos, and
mirrors the player appearance configured in the Bunny dashboard on the TV. The cast button
appears automatically when the library's player controls include `chromecast` and a cast device
is available; the video title and thumbnail show on the TV, and audio track, caption and
playback speed selections apply to the cast session.

No setup is needed. To point the SDK at a different receiver application (for example a staging
one), override it in your app's manifest:

```xml
<meta-data
    android:name="net.bunny.cast.RECEIVER_APPLICATION_ID"
    android:value="YOUR_APP_ID" />
```

## Player appearance

Colors, visible controls, captions styling and the player language are configured per library in
the Bunny dashboard (Stream > your library > Player). Both players apply those settings
automatically; the video player also accepts custom control icons in code
([Play a video](docs/guides/play-a-video.md#appearance)).

## Security notes

For token-protected libraries pass `token`/`expires` to the players and sign tokens on your
server, not in the app. When loading Bunny-hosted images with your own image loader under
hotlink protection, send the `Referer` header. Details:
[Secure playback](docs/guides/secure-playback.md).

## License

Bunny Stream Android is licensed under the [MIT License](LICENSE).
