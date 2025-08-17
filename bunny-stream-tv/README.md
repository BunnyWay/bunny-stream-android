# Bunny Stream Android TV

The Bunny Stream Android TV SDK provides a seamless video streaming experience optimized for Android TV devices. This module extends the core Bunny Stream Android SDK with TV-specific features designed for the big screen.

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Implementation Guide](#implementation-guide)
- [TV Controls](#tv-controls)
- [Remote Control Support](#remote-control-support)
- [Resume Position](#resume-position)
- [Settings & Configuration](#settings--configuration)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)

## Overview

The Bunny Stream TV SDK extends the core Bunny Stream Android SDK with TV-optimized components:

- **TV-Optimized Player**: Large, focusable controls designed for D-pad navigation
- **Remote Control Support**: Full support for Android TV remote controls and media keys
- **Resume Position**: Seamless resume functionality across sessions
- **Focus Management**: Proper focus handling for TV navigation
- **Settings Dialog**: TV-optimized quality, speed, and subtitle selection
- **Error Handling**: TV-appropriate error states and recovery

## Requirements

### Minimum Requirements
- Android API Level 21 (Android 5.0)
- Android TV or device with Android TV features
- Bunny Stream Android SDK (core modules)

### Dependencies
```kotlin
// TV-specific dependencies (automatically included)
implementation("androidx.leanback:leanback:1.0.0")
implementation("androidx.leanback:leanback-preference:1.0.0")
implementation("androidx.tvprovider:tvprovider:1.0.0")
```

### Supported Devices
- Android TV devices
- Android TV boxes
- Smart TVs with Android TV OS
- Fire TV devices
- Emulators with Android TV system images

## Installation

### 1. Add TV Module to Your Project

Add the TV module to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("net.bunny:tv:latest.release")
    
    // Core dependencies (if not already included)
    implementation("net.bunny:api:latest.release")
    implementation("net.bunny:player:latest.release")
}
```

### 2. Update AndroidManifest.xml

Add TV features to your main app's manifest:

```xml
<!-- TV support features -->
<uses-feature
    android:name="android.software.leanback"
    android:required="false" />
<uses-feature
    android:name="android.hardware.touchscreen"
    android:required="false" />

<!-- Required permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Add TV launcher category to your main activity -->
<activity
    android:name=".MainActivity"
    android:banner="@drawable/tv_banner"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

### 3. Add TV Banner

Create a TV banner for the Android TV launcher:
- **Size**: 320x180 pixels
- **Location**: `app/src/main/res/drawable-xhdpi/tv_banner.png`

## Quick Start

### Basic TV Player Implementation

```kotlin
import net.bunny.tv.utils.isRunningOnTV
import net.bunny.tv.ui.BunnyTVPlayerActivity

class VideoActivity : AppCompatActivity() {
    
    fun playVideo(videoId: String, libraryId: Long, videoTitle: String) {
        if (isRunningOnTV()) {
            // Use TV-optimized player
            BunnyTVPlayerActivity.start(
                context = this,
                videoId = videoId,
                libraryId = libraryId,
                videoTitle = videoTitle
            )
        } else {
            // Use mobile player
            navigateToMobilePlayer(videoId, libraryId)
        }
    }
}
```

### TV Detection in MainActivity

```kotlin
import net.bunny.tv.utils.isRunningOnTV

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (isRunningOnTV()) {
            setupTVInterface()
        } else {
            setupMobileInterface()
        }
    }
    
    private fun setupTVInterface() {
        // TV-specific UI setup
        setContentView(R.layout.activity_main_tv)
        // Configure for D-pad navigation
    }
}
```

## Features

### 🎮 TV-Optimized Controls

- **Large Focusable Buttons**: Designed for D-pad navigation (80dp standard, 100dp for play/pause)
- **Visual Focus Indicators**: Clear focus states with scaling animations
- **Auto-Hide Behavior**: Controls hide after 5 seconds of inactivity
- **Custom Control Layout**: Optimized for TV screen sizes and viewing distances

### 📺 Remote Control Support

| Key | Action |
|-----|--------|
| **D-pad Center/Enter** | Show/Hide controls or activate focused button |
| **Media Play/Pause** | Toggle playback |
| **Media Fast Forward** | Seek forward 30 seconds |
| **Media Rewind** | Seek backward 30 seconds |
| **D-pad Left/Right** | Seek when controls hidden |
| **Back** | Hide controls or exit player |

### ⏯️ Resume Position

- **Automatic Position Saving**: Saves position every 10 seconds during playback
- **Resume Dialog**: TV-optimized dialog for resuming from saved position
- **Configurable Thresholds**: Customizable minimum watch time and resume thresholds
- **Lifecycle Integration**: Saves position on pause, stop, and app backgrounding

### ⚙️ Settings & Configuration

- **TV Settings Dialog**: Remote-friendly settings interface
- **Playback Speed Control**: 0.25x to 4x speed options
- **Video Quality Selection**: Automatic and manual quality selection
- **Subtitle Management**: Enable/disable and language selection
- **Focus Navigation**: Full D-pad navigation support

## Implementation Guide

### Setting Up TV Player Activity

The `BunnyTVPlayerActivity` is the main entry point for TV video playback:

```kotlin
// Launch TV player
BunnyTVPlayerActivity.start(
    context = context,
    videoId = "your-video-id",
    libraryId = 12345L,
    videoTitle = "Your Video Title" // Optional but recommended
)
```

### Custom TV Controls

You can customize the TV controls by extending `TVPlayerControlsView`:

```kotlin
class CustomTVControls(context: Context) : TVPlayerControlsView(context) {
    
    init {
        // Add custom buttons or modify existing ones
        addCustomButton()
    }
    
    private fun addCustomButton() {
        val customButton = ImageButton(context).apply {
            setImageResource(R.drawable.custom_icon)
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.tv_button_background)
            setOnClickListener {
                // Custom action
            }
        }
        // Add to controls layout
    }
}
```

### TV Key Event Handling

Handle custom key events by extending the activity:

```kotlin
class CustomTVPlayerActivity : BunnyTVPlayerActivity() {
    
    override fun handleTVKeyEvent(keyEvent: KeyEvent): Boolean {
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    showCustomMenu()
                    return true
                }
            }
            KeyEvent.KEYCODE_GUIDE -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    showProgramGuide()
                    return true
                }
            }
            else -> return super.handleTVKeyEvent(keyEvent)
        }
        return false
    }
}
```

## TV Controls

### Control Components

The TV player includes the following control components:

1. **Video Title Display** - Shows current video title
2. **Play/Pause Button** - Central play/pause control (100dp)
3. **Seek Buttons** - 30-second forward/backward seeking (80dp)
4. **Settings Button** - Opens TV settings dialog (80dp)
5. **Progress Bar** - Shows playback progress
6. **Time Display** - Current time / Total duration

### Focus Navigation

Controls are arranged for optimal D-pad navigation:

```
    [Settings]
[←30s] [Play/Pause] [30s→]
     [Progress Bar]
     [Time Display]
```

### Control Customization

```kotlin
// Configure control visibility and behavior
tvControls.apply {
    setVideoTitle("My Video")
    setOnSettingsClickListener {
        showCustomSettings()
    }
    // Show/hide controls programmatically
    show()
    hide()
}
```

## Remote Control Support

### Media Key Mapping

The TV player responds to standard Android TV remote keys:

```kotlin
// Media keys handled automatically
KEYCODE_MEDIA_PLAY_PAUSE    // Toggle playback
KEYCODE_MEDIA_PLAY          // Start playback
KEYCODE_MEDIA_PAUSE         // Pause playback
KEYCODE_MEDIA_FAST_FORWARD  // Seek forward 30s
KEYCODE_MEDIA_REWIND        // Seek backward 30s
```

### D-pad Navigation

```kotlin
// D-pad keys for navigation
KEYCODE_DPAD_CENTER         // Activate focused element
KEYCODE_DPAD_UP/DOWN        // Vertical navigation
KEYCODE_DPAD_LEFT/RIGHT     // Horizontal navigation / seeking
KEYCODE_BACK                // Back navigation / hide controls
```

### Custom Key Handling

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP -> {
            // Handle channel up
            nextVideo()
            return true
        }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
            // Handle channel down
            previousVideo()
            return true
        }
    }
    return super.onKeyDown(keyCode, event)
}
```

## Resume Position

### Configuration

Configure resume position behavior:

```kotlin
val resumeConfig = ResumeConfig(
    retentionDays = 7,              // Keep positions for 7 days
    minimumWatchTime = 30_000L,     // 30 seconds minimum watch time
    resumeThreshold = 0.05f,        // Don't resume if < 5% watched
    nearEndThreshold = 0.95f,       // Don't resume if > 95% watched
    enableAutoSave = true,          // Auto-save every 10 seconds
    saveInterval = 10_000L          // Save interval in milliseconds
)

bunnyPlayer.enableResumePosition(
    config = resumeConfig,
    onResumePositionCallback = { position, callback ->
        showResumeDialog(position, callback)
    }
)
```

### Resume Dialog

The TV player shows a resume dialog when a saved position is found:

```kotlin
private fun showResumeDialog(position: PlaybackPosition, callback: (Boolean) -> Unit) {
    AlertDialog.Builder(this)
        .setTitle("Resume Playback")
        .setMessage("Continue watching from ${formatTime(position.position)}?")
        .setPositiveButton("Resume") { _, _ -> callback(true) }
        .setNegativeButton("Start Over") { _, _ -> callback(false) }
        .show()
}
```

## Settings & Configuration

### TV Settings Dialog

The TV settings dialog provides remote-friendly access to:

- **Playback Speed**: 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x
- **Video Quality**: Auto, 240p, 360p, 480p, 720p, 1080p, 4K
- **Subtitles**: Available subtitle tracks and off option

### Opening Settings

```kotlin
// Show settings from TV controls
tvControls.setOnSettingsClickListener {
    val settingsDialog = TVSettingsDialog(this, bunnyPlayer)
    settingsDialog.show()
}
```

## Error Handling

### Error States

The TV player handles various error conditions:

- **Network Errors**: Connection timeouts, network unavailable
- **Video Errors**: Invalid video ID, video not found, DRM errors
- **Playback Errors**: Codec issues, format not supported

### Error Display

```kotlin
// Show error with retry option
private fun showError(title: String, message: String) {
    errorContainer.visibility = View.VISIBLE
    errorMessage.text = message
    retryButton.requestFocus() // Focus retry button for TV
}

// Handle retry
retryButton.setOnClickListener {
    hideError()
    loadVideo(videoId, libraryId) // Retry loading
}
```

## Testing

### TV Emulator Setup

1. **Create Android TV AVD**:
    - Open AVD Manager in Android Studio
    - Create new virtual device
    - Select "TV" category
    - Choose Android TV system image

2. **Install and Test**:
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Physical Device Testing

1. **Enable Developer Options** on Android TV
2. **Enable USB Debugging**
3. **Connect via ADB**:
   ```bash
   adb connect <TV_IP_ADDRESS>
   adb install app-debug.apk
   ```

### Testing Checklist

- [ ] App launches on TV
- [ ] Video loads and plays
- [ ] Remote control navigation works
- [ ] All buttons are focusable
- [ ] Controls auto-hide correctly
- [ ] Resume position works
- [ ] Settings dialog functions
- [ ] Error states display properly
- [ ] Performance is smooth

## Best Practices

### UI Design
- **Large Touch Targets**: Minimum 48dp for focusable elements
- **High Contrast**: Ensure good visibility from TV viewing distance
- **Clear Focus States**: Use prominent focus indicators
- **Readable Text**: Use large font sizes (16sp minimum)

### Navigation
- **Logical Focus Order**: Arrange controls in intuitive navigation flow
- **Focus Retention**: Maintain focus state during UI updates
- **Back Behavior**: Implement consistent back button behavior
- **Spatial Navigation**: Consider physical layout of controls

### Performance
- **Smooth Animations**: Keep animations under 300ms
- **Memory Management**: Release resources when not in use
- **Network Optimization**: Handle poor connectivity gracefully
- **Battery Optimization**: Avoid unnecessary background processing

## Troubleshooting

### Common Issues

#### Video Not Playing
```kotlin
// Check if BunnyStreamApi is initialized
if (!BunnyStreamApi.isInitialized()) {
    BunnyStreamApi.initialize(context, accessKey, libraryId)
}

// Verify network connectivity
if (!isNetworkAvailable()) {
    showNetworkError()
    return
}
```

#### Controls Not Responding
```kotlin
// Ensure controls are focusable
button.isFocusable = true
button.isFocusableInTouchMode = false // Important for TV

// Check focus handling
button.onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
    view.isSelected = hasFocus
}
```

## API Reference

### BunnyTVPlayerActivity

```kotlin
class BunnyTVPlayerActivity : AppCompatActivity() {
    
    companion object {
        fun start(
            context: Context, 
            videoId: String, 
            libraryId: Long, 
            videoTitle: String? = null
        )
    }
    
    // Override these methods for customization
    protected open fun handleTVKeyEvent(keyEvent: KeyEvent): Boolean
    protected open fun showResumeDialog(position: PlaybackPosition, callback: (Boolean) -> Unit)
    protected open fun showSettingsDialog()
    protected open fun showError(title: String, message: String)
}
```

### TVPlayerControlsView

```kotlin
class TVPlayerControlsView : ConstraintLayout {
    
    fun setBunnyPlayer(player: BunnyPlayer)
    fun setVideoTitle(title: String)
    fun setOnSettingsClickListener(listener: () -> Unit)
    fun show()
    fun hide()
    fun showLoading()
    fun hideLoading()
    fun updateProgress()
    fun isControlsVisible(): Boolean
}
```

### Device Detection

```kotlin
// Extension functions available
fun Context.isRunningOnTV(): Boolean
fun Context.getDeviceType(): DeviceType

// Usage
if (context.isRunningOnTV()) {
    // TV-specific logic
}
```

## Support

For additional support and documentation:

- **Core SDK Documentation**: See main [README.md](../README.md)
- **Android TV Guidelines**: [Android TV Design Guidelines](https://developer.android.com/design/tv)
- **Leanback Library**: [Android Leanback Documentation](https://developer.android.com/training/tv/start)

## License

This TV SDK follows the same license as the core Bunny Stream Android SDK. See [LICENSE](../LICENSE) for details.