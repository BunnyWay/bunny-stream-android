# Getting started

Set up the SDK and make your first call. Takes about five minutes.

## Requirements

- Android 8.0 (API 26) or newer on the device
- `compileSdk` 36 or higher, JDK 17
- Kotlin 2.1 or newer (the SDK itself is built with 2.2.20)
- Core library desugaring enabled, if you use `net.bunny:player`
- A Bunny Stream video library and its API key (Bunny dashboard: Stream > your library > API)

The last three are enforced by the build rather than documented politely. A `compileSdk` below 36
fails with `checkAarMetadata`; an older Kotlin cannot read the SDK's metadata and reports the
binary version as incompatible - 2.1 works because Kotlin reads metadata one version ahead, 2.0
and below do not; and without desugaring the player's media3 dependency is rejected.
Desugaring looks like this:

```kotlin
// build.gradle.kts
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
```

## 1. Add the dependencies

The SDK ships on Maven Central as three artifacts. Pick what you need:

```kotlin
// build.gradle.kts
dependencies {
    implementation("net.bunny:api:latest.release")        // video and live stream management, uploads
    implementation("net.bunny:player:latest.release")     // video and live playback (pulls in :api)
    implementation("net.bunny:recording:latest.release")  // camera recording and go-live (pulls in :api)
}
```

Replace `latest.release` with a concrete version for reproducible builds.

## 2. Declare the INTERNET permission

The api and player artifacts do not declare it for you (the recording artifact does):

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

Camera capture needs two more permissions, covered in
[Go live from the camera](go-live-from-the-camera.md).

## 3. Initialize

Call `initialize` once, before anything else from the SDK. `Application.onCreate` is the usual
place:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BunnyStreamApi.initialize(
            context = this,
            accessKey = BuildConfig.BUNNY_API_KEY, // your library API key
            libraryId = 12345L,                    // your library id
        )
    }
}
```

Keep the API key out of source control. Read it from `local.properties`, an environment variable
or your secrets tooling, and remember that a key baked into a shipped APK can be extracted; for
production consider a backend that talks to Bunny on the app's behalf where possible.

This registers a *default instance* that the rest of the SDK reaches through `getInstance()`, and
that the player and camera views fall back to when you do not give them one. Blank credentials are
rejected here rather than failing later with a 401, so call it once you actually have a key.

The most common integration mistake is skipping the call: the player renders a black view and logs
an error instead of crashing, and the camera view refuses to start. See
[Troubleshooting](troubleshooting.md).

### More than one library

`initialize` is a convenience for the common case. Every instance owns its credentials, its HTTP
client and its uploads, so you can hold as many as you have libraries:

```kotlin
val marketing = BunnyStreamApi.create(
    context = this,
    config = BunnyStreamConfig(accessKey = marketingKey, libraryId = 12345L),
)
val training = BunnyStreamApi.create(
    context = this,
    config = BunnyStreamConfig(accessKey = trainingKey, libraryId = 67890L),
)
```

Nothing is registered globally, so hold on to the handles. Point a view at one with its `bunny`
property (`BunnyStreamPlayer`, `BunnyStreamCameraUpload`) or the `bunny` parameter
(`BunnyLiveStreamPlayer`); leave it unset and the view uses the default instance. Call
`release()` on an instance when you are done with it — that stops its in-flight uploads and leaves
every other instance running.

## 4. Make a call

```kotlin
// List the videos in your library. Repository calls are suspend - call them from a coroutine.
val result = BunnyStreamApi.getInstance().videoRepository.listVideos(libraryId = 12345L)

result.fold(
    onOk = { page -> render(page.items) },
    onErr = { error -> showError(error.message) },
)
```

Every management call answers with a `BunnyResult` rather than throwing — see
[Handle errors](handle-errors.md).

## Next steps

- [Play a video](play-a-video.md)
- [Play a live stream](play-a-live-stream.md)
- [Go live from the camera](go-live-from-the-camera.md)
- [Upload videos](upload-videos.md)

The demo app in [`app/`](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md) exercises every feature and is the quickest way to
see working code.
