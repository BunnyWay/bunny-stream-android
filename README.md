# Bunny Stream Android SDK

The Bunny Stream Android SDK provides a comprehensive set of libraries for integrating video streaming and recording capabilities into your Android applications.

## 📦 Installation

Add the following dependencies to your `build.gradle.kts` (Module: app) file:

```kotlin
dependencies {
    // Core API library (required)
    implementation("net.bunny:bunny-stream-api:1.0.0")
    
    // Video player (optional)
    implementation("net.bunny:bunny-stream-player:1.0.0")
    
    // Camera recording and upload (optional)
    implementation("net.bunny:bunny-stream-camera-upload:1.0.0")
}
```

Make sure your project's `build.gradle.kts` (Project) includes Maven Central:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

## 📚 Libraries

### bunny-stream-api
Core API library providing:
- Video management API
- Upload functionality
- Stream configuration
- Authentication handling

### bunny-stream-player
Video player library featuring:
- HLS streaming support
- Adaptive bitrate streaming
- Custom player controls
- Subtitle support
- Video quality selection

### bunny-stream-camera-upload
Camera recording and upload library including:
- Camera recording functionality
- Real-time streaming
- File upload management
- Recording state management

## 🚀 Quick Start

### Basic API Usage

```kotlin
import net.bunny.stream.api.*

// Initialize the API client
val bunnyStreamApi = BunnyStreamApi(
    apiKey = "your-api-key",
    libraryId = "your-library-id"
)

// Upload a video
val uploadResult = bunnyStreamApi.uploadVideo(videoFile)
```

### Video Player

```kotlin
import net.bunny.stream.player.*

// Add player view to your layout
val playerView = BunnyPlayerView(context)

// Initialize player
val player = DefaultBunnyPlayer(context)
player.setVideoUrl("https://your-video-url.m3u8")
playerView.player = player
```

### Camera Recording

```kotlin
import net.bunny.stream.recording.*

// Initialize camera upload
val cameraUpload = StreamCameraUploadView(context)
cameraUpload.configure(
    streamKey = "your-stream-key",
    rtmpUrl = "your-rtmp-url"
)

// Start recording
cameraUpload.startRecording()
```

## 📖 Documentation

For detailed documentation and API reference, visit:
- [API Documentation](docs/index.md)
- [Player Documentation](docs/player/index.md)
- [Recording Documentation](docs/recording/index.md)

## 🔧 Requirements

- **Minimum SDK**: API 26 (Android 8.0)
- **Target SDK**: API 35
- **Kotlin**: 2.1.20+
- **Java**: 11+

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

For support and questions:
- Email: support@bunny.net
- Documentation: [Bunny.net Docs](https://docs.bunny.net)
- GitHub Issues: [Create an issue](https://github.com/BunnyWay/bunny-stream-android/issues)

## 🔄 Changelog

### Version 1.0.0
- Initial release
- Core API functionality
- Video player with HLS support
- Camera recording and upload
- Documentation and examples