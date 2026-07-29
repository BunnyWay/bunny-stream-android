# Getting started

Set up the SDK and make your first call. Takes about five minutes.

## Requirements

- Android 8.0 (API 26) or newer
- `compileSdk` 35, JDK 17
- A Bunny Stream video library and its API key (Bunny dashboard: Stream > your library > API)

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

Everything in the SDK assumes this call happened. The most common integration mistake is skipping
it: the player then renders a black view and logs an error instead of crashing, and the camera
view fails at startup. See [Troubleshooting](troubleshooting.md).

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

<!-- TODO before the 4.0.0 release: update the init snippet once the session becomes
     per-instance (the singleton is the last part of the refactor still to land). -->

## Next steps

- [Play a video](play-a-video.md)
- [Play a live stream](play-a-live-stream.md)
- [Go live from the camera](go-live-from-the-camera.md)
- [Upload videos](upload-videos.md)

The demo app in [`app/`](https://github.com/BunnyWay/bunny-stream-android/blob/main/app/README.md) exercises every feature and is the quickest way to
see working code.
