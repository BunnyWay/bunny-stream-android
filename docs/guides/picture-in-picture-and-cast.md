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

The SDK initializes the Cast framework by itself and casts to the **Bunny Stream receiver
application** - the same receiver the web player uses - which plays every Bunny asset, including
fMP4 HLS and Widevine-protected videos, and applies the dashboard's player theming (key color,
caption color, font and size) on the TV. Title and artwork show on the TV, position carries over
in both directions, and audio track, caption and playback speed selections made in the player
apply to the cast session. The quality menu is hidden while casting - the receiver decides ABR.
On devices without Play services the player simply runs without a cast button.

No code is required in the host app. To point the SDK at a different receiver application (for
example a staging one), override it in your app's manifest:

```xml
<meta-data
    android:name="net.bunny.cast.RECEIVER_APPLICATION_ID"
    android:value="YOUR_APP_ID" />
```

## Gotchas

- PiP: the `supportsPictureInPicture` flag is the whole trick. If the button does nothing, this
  flag is missing - see [Troubleshooting](troubleshooting.md).
- Cast: casting DRM-protected videos depends on receiver-side support; test with your content
  before relying on it.
- Both controls can be turned off per library in the dashboard, which removes the buttons
  entirely.
