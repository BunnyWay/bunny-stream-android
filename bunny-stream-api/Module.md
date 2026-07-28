# Module BunnyStreamApi

Core module of the Bunny Stream Android SDK (`net.bunny:api`). Start here: call
`BunnyStreamApi.initialize(context, accessKey, libraryId)` once, then reach the rest of the SDK
through `BunnyStreamApi.getInstance()`.

What you get:

- Video and collection management for your Bunny Stream library
- Video upload, including chunked TUS uploads with mid-upload pause and resume
- Live stream management (`liveStreamRepository`): create, schedule, start, stop, thumbnails,
  status polling
- Playback settings and resume-position storage used by the player module

The `:player` and `:recording` modules depend on this one and expect `initialize` to have been
called before they are used.

Integration guides with copy-paste examples live in the repository under
[docs/guides](https://github.com/BunnyWay/bunny-stream-android/tree/main/docs/guides).
