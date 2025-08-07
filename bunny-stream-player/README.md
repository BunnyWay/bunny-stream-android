# BunnyStreamPlayer - Video Playback

BunnyStreamPlayer is an Android library that provides an easy-to-use video player implementation built on top of media3 APIs (previously ExoPlayer).

## Overview

This library simplifies video playback in Android applications by providing:

- A flexible player component for seamless integration
- Support for multiple video formats including HLS
- Customizable UI components
- Cast support for Chromecast devices
- Built-in fullscreen handling

## Installation

Declare dependency in your project's `build.gradle.kts`:
```
implementation("net.bunny:player:1.0.0")
```

## Initialization

After installation, you'll need to configure the package with your Bunny credentials. Initialization is common for all modules.

```kotlin
// Initialize with your access key (optional) and library ID
BunnyStreamApi.initialize(context, accessKey, libraryId)
```

### Using `BunnyStreamPlayer` in Compose

```kotlin
@Composable
fun BunnyStreamPlayerComposable(
    videoId: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            BunnyStreamPlayer(context)
        },
        update = {
            it.playVideo(videoId)
        },
        modifier = modifier.background(Color.Gray)
    )
}
```

Full usage example can be found in demo app.

### Using `BunnyStreamPlayer` in XML Views

1. Add `BunnyStreamPlayer` into your layout:
```
<net.bunnystream.player.ui.BunnyStreamPlayer
      android:id="@+id/videoPlayer"
      android:layout_width="match_parent"
      android:layout_height="match_parent"/>
```
2. Call `playVideo()`:
```
bunnyStreamPlayer.playVideo(videoId)
```

`bunnyStreamPlayer` comes from `findViewById()` or from View binding.

**Customizing Player:**

You can customize the BunnyStreamPlayer by passing custom icons. Other costumizations like primary color, font, handling control visibilty, captions, heatmap can be controlled from the Bunny dashboard.

1. Override icons you want to change from `PlayerIconSet` class:

```kotlin
@Parcelize
data class PlayerIconSet(
    @DrawableRes
    val playIcon: Int = R.drawable.ic_play_48dp,

    @DrawableRes
    val pauseIcon: Int = R.drawable.ic_pause_48dp,

    @DrawableRes
    val rewindIcon: Int = R.drawable.ic_replay_10s_48dp,

    @DrawableRes
    val forwardIcon: Int = R.drawable.ic_forward_10s_48dp,

    @DrawableRes
    val settingsIcon: Int = R.drawable.ic_settings_24dp,

    @DrawableRes
    val volumeOnIcon: Int = R.drawable.ic_volume_on_24dp,

    @DrawableRes
    val volumeOffIcon: Int = R.drawable.ic_volume_off_24dp,

    @DrawableRes
    val fullscreenOnIcon: Int = R.drawable.ic_fullscreen_24dp,

    @DrawableRes
    val fullscreenOffIcon: Int = R.drawable.ic_fullscreen_exit_24dp,
) : Parcelable
```
2. Set new icon set:
```
bunnyStreamPlayer.iconSet = newIconSet
```

Full player usage example and permissions handling can be found in demo app.

For more information, see [class level documentation](docs/index.md).

## License

Bunny Stream Android is licensed under the [MIT License](LICENSE). See the LICENSE file for more details.
