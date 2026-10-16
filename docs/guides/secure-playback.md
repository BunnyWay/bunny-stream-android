# Secure playback

Playing content from libraries with token authentication or hotlink protection enabled.

## Token authentication

When "Token authentication" is on for your library (Bunny dashboard > Stream > your library >
Security), every playback needs a signed token and an expiry timestamp. Both players accept them:

```kotlin
// VOD
player.playVideo(videoId, token = token, expires = expires)

// Live
BunnyLiveStreamPlayer(libraryId, streamId, token = token, expires = expires)
```

`expires` is a unix timestamp (seconds). The token is a SHA-256 based signature over your
library's token authentication key, the video id and the expiry.

**Forgetting the token looks like nothing failing.** The management API does not need it, so the
call for play data succeeds and the player comes up with a working timeline and controls — the CDN
just serves no media, and the picture stays black. There is no error to catch. The same happens
with a token signed using a different library's key. If playback is black on a library that works
in the Bunny dashboard, check this first.

**Sign tokens on your server.** The signature requires the library's token authentication key;
shipping that key inside the app makes the protection pointless, since anyone can extract it and
mint their own tokens. The usual setup: your backend exposes an endpoint that returns
`{token, expires}` for a video id, your app calls it before starting playback.

The demo app signs tokens on the device purely so it can run standalone; treat that as a demo
convenience, not a pattern to copy.

## Hotlink protection and the Referer header

When "Block direct URL file access" is on, Bunny's CDN only serves requests carrying the expected
`Referer` header. The SDK sends it for everything it loads itself: playback, seek thumbnails,
posters inside the players.

You only need to act when you load Bunny-hosted images with your own image loader (thumbnails in
your video list, for example). Send the SDK's Referer constant:

```kotlin
// Coil
val request = ImageRequest.Builder(context)
    .data(thumbnailUrl)
    .httpHeaders(NetworkHeaders.Builder().set("Referer", BunnyCdn.REFERER).build())
    .build()

// Glide
val glideUrl = GlideUrl(thumbnailUrl) { mapOf("Referer" to BunnyCdn.REFERER) }
```

Without the header those requests come back as `403`.

## Enterprise DRM (Widevine)

The player follows the library's Enterprise DRM settings on its own; there is nothing to configure
in the app. What viewers see depends on the Widevine security level of their device. Emulators and
many cheaper phones only have L3 (software), most current phones have L1 (hardware).

- **SD only for L3**: the license server only gives L3 devices the keys for the SD renditions.
  On an L3 device the player stays at or below the library's maximum software resolution (480p
  unless the library says otherwise), and the quality menu only lists those renditions. L1 devices
  get the full ladder.
- **Minimum security level**: a device below the library's minimum gets no license at all. The
  player does not start playback there and shows "This video can't be played on this device". The
  same text reaches `onPlaybackError`, and the exact reason (device level and required level) goes
  to logcat.

When the SDK can't read the device's level, it applies neither rule and leaves the decision to the
license server. While casting, the receiver plays and licenses the video itself. The phone's level
counts again when playback comes back to the phone: the cap applies there, and a video the phone
can't license stops with the same message.

## Keep the API key server-side where you can

The `accessKey` passed to `initialize` is your library's API key. It can manage content, not just
read it. Apps that only play content do not need it at all: initialise with
`BunnyStreamApi.initialize(context, libraryId)` and the SDK plays through the public play
endpoints. If the library has token authentication on, have a backend sign the playback tokens and
hand the app only what it needs (video ids, tokens), so neither key ships in the APK.
