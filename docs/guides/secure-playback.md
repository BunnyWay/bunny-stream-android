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

## Keep the API key server-side where you can

The `accessKey` passed to `initialize` is your library's API key. It can manage content, not just
read it. For apps that only play content, consider a thin backend that performs management calls
and hands the app only what it needs (video ids, playback tokens).
