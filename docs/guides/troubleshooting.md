# Troubleshooting

The failure modes integrators actually hit, with fixes.

## Player shows a black view, no crash

`BunnyStreamApi.initialize(...)` was not called before `playVideo`. The player logs an error
(`Unable to play video, initialize the player first...`) and renders nothing. Initialize in
`Application.onCreate`, see [Getting started](getting-started.md).

## The video plays black, but the controls and timeline work

The library has token authentication on and playback was started without a token. The API call
succeeds, so play data arrives and the player builds its UI, but the CDN serves no media — there
is nothing to report as an error, and nothing to see.

Pass `token` and `expires` to `playVideo` / `BunnyLiveStreamPlayer`, see
[Secure playback](secure-playback.md). Check the setting under Bunny dashboard > Stream > your
library > Security; a token signed with a different library's key fails the same silent way.

## `IllegalStateException: BunnyStreamApi has no default instance`

Something reached `getInstance()` before `initialize` ran. Usually the SDK is initialised from a
screen rather than `Application.onCreate`, or credentials arrive from the network and a screen
opens first. Either initialise earlier, guard with `isInitialized()`, or create an instance with
`BunnyStreamApi.create(...)` and hand it to the view through its `bunny` property.

## `IllegalArgumentException: accessKey must not be blank`

`initialize` is being called with placeholder credentials — commonly an empty key read from
preferences before the user has entered one, or a `libraryId` of `0`. The SDK rejects these instead
of accepting them and failing later with a `401`. Call `initialize` once you have real values.

## Images come back as HTTP 403

Your library has "Block direct URL file access" enabled and your own image loader does not send
the `Referer` header. Add `BunnyCdn.REFERER` to the request, see
[Secure playback](secure-playback.md). Playback and the player's own images are unaffected -
the SDK sends the header itself.

## Playback fails with 401 / 403

Token authentication is enabled for the library and the playback call carried no token, an
expired token, or one signed with the wrong key. Pass `token` and `expires` to
`playVideo` / `BunnyLiveStreamPlayer`, see [Secure playback](secure-playback.md).

## The PiP button does nothing

The hosting activity is missing `android:supportsPictureInPicture="true"`. See
[Picture-in-Picture and Chromecast](picture-in-picture-and-cast.md).

## There is no cast button

Either the `chromecast` control is disabled in the library's player settings, the device has no
Google Play services, or there is no cast target on the network.

## Camera preview never starts

`CAMERA` or `RECORD_AUDIO` is not granted. `startPreview()` does not request permissions and does
not throw - it logs a warning and returns. Request the permissions, then call `startPreview()`
again. See [Go live from the camera](go-live-from-the-camera.md).

## "Go live" fails immediately for a stream that worked before

The stream has ended (status ENDED or VOD_PROCESSING). Ended streams cannot be restarted; create
a new stream for every broadcast. See [Manage live streams](manage-live-streams.md).

## Live viewers still see the stream after the broadcaster quit

The broadcast was ended by killing the app or screen instead of calling `stopRecording()`. The
server keeps the stream live until it times out. Always stop through the view, see
[Go live from the camera](go-live-from-the-camera.md).

## Uploaded video does not play

Upload completion is not playability. The video needs to finish processing first; check its
status and wait for `FINISHED`. See [Upload videos](upload-videos.md).

## Playback misbehaves with two player views on screen

The playback engine is shared process-wide and drives one player view at a time. Detach the
first view (or leave its screen) before starting playback in another.

## The scheduled-stream countdown never appears

The stream has no `scheduledStartTime`, or `enableCountdown` is off for it. Set both when
creating the stream, see [Manage live streams](manage-live-streams.md).

## Still stuck?

Run the [demo app](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md) with your credentials - if the same flow works there,
compare its code with yours. Bug reports: use the repository's issue templates.
