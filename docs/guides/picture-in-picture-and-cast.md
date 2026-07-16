# Picture-in-Picture and Chromecast

Both features are built into the player. Each has one host-app requirement.

## Picture-in-Picture

The PiP button appears in the player controls when the library's player settings include the
`pip` control and the device supports PiP. Tapping it shrinks the video into the system PiP
window; playback continues.

**Required:** declare PiP support on the activity hosting the player. Without this the button is
a silent no-op - nothing happens and nothing is logged prominently.

```xml
<activity
    android:name=".PlayerActivity"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" />
```

The `configChanges` line prevents the activity from being recreated when the window changes size,
which would otherwise interrupt playback.

Nothing else to wire: the SDK handles entering PiP, keeps playing inside the window, and pauses
when the user dismisses it.

## Chromecast

The cast button appears when:

- the library's player settings include the `chromecast` control (Bunny dashboard), and
- Google Play services are available on the device, and
- there is a cast device on the network.

The SDK initializes the Cast framework by itself and hands playback over to the receiver with
title and artwork; position carries over in both directions. Works for videos and live streams.
On devices without Play services the player simply runs without a cast button.

No code is required in the host app.

## Gotchas

- PiP: the `supportsPictureInPicture` flag is the whole trick. If the button does nothing, this
  flag is missing - see [Troubleshooting](troubleshooting.md).
- Cast: casting DRM-protected videos depends on receiver-side support; test with your content
  before relying on it.
- Both controls can be turned off per library in the dashboard, which removes the buttons
  entirely.
