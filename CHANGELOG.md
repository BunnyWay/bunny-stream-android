# Changelog

All notable changes to Bunny Stream Android are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project follows semantic versioning:

- `MAJOR` versions may include breaking API or behavior changes.
- `MINOR` versions add functionality in a backward-compatible way.
- `PATCH` versions include backward-compatible bug fixes and maintenance updates.

## [Unreleased] - 4.0.0

The live streaming release, bundled with an architecture refactor. Existing integrations: see
[MIGRATING.md](MIGRATING.md).

Three things change in the public API: how results and failures are reported (one envelope, one
typed error taxonomy, uploads as a `Flow`), what the API is made of (domain models instead of the
OpenAPI generator's output), and how a session is held (an instance you own instead of process-wide
state). Every item below is a breaking API change, and nothing that was possible in 3.x is gone —
see [MIGRATING.md](MIGRATING.md) for before/after examples.

### Added

- Live streaming across the SDK:
  - `BunnyLiveStreamPlayer` composable: countdown for scheduled streams, looping muted pre-stream
    trailer, automatic connect when the stream goes live, DVR seeking with jump-to-live,
    automatic recovery after playback hiccups, switch to the recording after the stream ends.
  - Live broadcasting from the camera: set `liveStreamId` on `BunnyStreamCameraUpload` to
    broadcast to an existing stream, with automatic server-side start/stop, reconnect with
    primary/backup failover, connection badges and an opt-in simultaneous dual publish.
  - Live stream management (`liveStreamRepository`): create, schedule, update, delete, start,
    stop, status polling, ingest status, thumbnails.
- DASH support in the playback engine (manifest type detected from the URL; Bunny currently
  serves HLS).
- Chromecast casts to the Bunny Stream receiver application - the receiver the web player uses -
  so fMP4 HLS and Widevine-protected videos play on the TV with the dashboard's player theming
  applied. Audio track, caption and speed selections bridge to the cast session, and the receiver
  application can be overridden through the `net.bunny.cast.RECEIVER_APPLICATION_ID` manifest
  meta-data.
- Live player appearance follows the library's player settings from the Bunny dashboard
  (accent color, font, language, controls, compact mode).
- `BunnyStreamPlayer.controlsEnabled` — set it to `false` for a bare video surface and drive
  playback from your own UI. Everything below the chrome keeps working (DRM, resume positions,
  captions, CDN telemetry), and playback errors are still surfaced on screen and through
  `onPlaybackError`. Your UI takes over what the control bar drew, including the live badge, the
  cast button and the fullscreen and Picture-in-Picture entry points.
- Player state callbacks on `BunnyStreamPlayer`, so an app drawing its own controls can follow what
  the engine is doing: `onPlayingChanged`, `onMutedChanged`, `onLoadingChanged`,
  `onPlaybackSpeedChanged`, `onChaptersUpdated`, `onMomentsUpdated`, `onRetentionGraphUpdated` and
  `onPlayerTypeChanged` (Chromecast handover). Previously the player view held the engine's only
  listener slot and forwarded almost nothing, so a custom UI went stale - a speed selector would
  show 1× over a video the engine had restored to 0.25×.
- `playbackSpeed` (read and write), `getPlaybackSpeeds()`, `isMuted()`, `mute()` and `unmute()` on
  `BunnyStreamPlayer`. Speed and mute had no public entry point at all; the demo app was reaching
  into private fields with reflection to change them.
- Hosted API reference (Dokka) published from CI, plus task-oriented integration guides under
  `docs/guides/`.

- `BunnyResult<T>` — the result envelope returned by every management call: `Ok(value)` or
  `Err(BunnyError)`, with `getOrNull()`, `errorOrNull()`, `map` and `fold(onOk, onErr)`.
- `BunnyError` — typed error taxonomy (`Auth`, `NotFound`, `Http`, `Network`, `Decode`,
  `LocalFile`, `InvalidState`). Every variant exposes `httpStatus` and `isTerminal`, so callers no
  longer parse message strings to decide whether to retry.
- `UploadEvent` — the upload stream's event type (`Started`, `Progress`, `Completed`, `Cancelled`,
  `Failed`).
- `VideoUploader.continueUpload(libraryId, videoId, uri)` — picks an interrupted upload up from the
  offset the server already has instead of re-sending the file. Resumable (TUS) uploader only; the
  plain one fails the attempt with `BunnyError.InvalidState` rather than quietly restarting.
- `videoRepository` and `collectionRepository` on `BunnyStreamApi` — domain replacements for the
  generated `videosApi` and `collectionsApi`. Every method of both generated clients has an
  equivalent: 22 on `VideoRepository`, 5 on `CollectionRepository`, all `suspend` and returning
  `BunnyResult`.
- Domain models for the video surface: `Video`, `VideoList`, `VideoPlayData`, `VideoStatistics`,
  `VideoResolutionsInfo`, `VideoStorageSize`, `VideoCollection`, `VideoCollectionList`, plus
  `Caption`, `Chapter`, `Moment`, `MetaTag` and `TranscodingMessage`. They carry every field the
  generated DTOs did.
- Named enums where the generator emitted numbered ones: `TranscodingSeverity`, `TranscodingIssue`
  and `VideoCodec` replace `Severity._0..3`, `IssueCodes._0..11` and `EncoderOutputCodec._0..3`.
  Unknown values from a newer server map to `UNDEFINED` instead of failing to parse.
- **More than one library at a time.** `BunnyStreamApi.create(context, BunnyStreamConfig(...))`
  returns an instance that owns its credentials, HTTP client and uploads. Views take one through
  `bunny` (`BunnyStreamPlayer`, `BunnyStreamCameraUpload`, `BunnyLiveStreamPlayer`) and fall back to
  the default instance when it is not set. `StreamApi.release()` stops one instance's uploads
  without touching the others.
- `BunnyStreamConfig`, carrying `accessKey`, `libraryId` and `baseApi`. It rejects blank keys and
  non-positive library ids at construction, and its `toString` does not print the key.

### Changed
- **The session is an instance, not process-wide state.** `initialize`/`getInstance()` still work
  and now stand for a *default instance*. `BunnyStreamApi.libraryId` is removed — read
  `getInstance().libraryId`, or the `libraryId` of the instance you hold. The access key no longer
  goes into the generated client's static map; each instance authenticates through its own
  interceptor. See [MIGRATING.md](MIGRATING.md) section 1.
- `initialize` rejects a blank access key or a non-positive library id instead of accepting them and
  failing later with a `401`.
- `getInstance()` before `initialize` throws `IllegalStateException` naming what to call, rather
  than a bare `NullPointerException`.
- `StreamApi` gained `config` and `release()`, and `StreamCameraUploadView` gained `bunny`, so
  anything implementing those interfaces — a fake in a test, most likely — needs the new members.
- `BunnyLiveStreamPlayer` takes `bunny` before its `viewModel` parameter. Named arguments are
  unaffected; a call passing `viewModel` positionally is not.
- `release()` now frees what an instance holds rather than only dropping the reference, and an
  instance must not be used afterwards. Its repositories throw `IllegalStateException` instead of
  failing somewhere less obvious. Calling it twice is harmless.
- TUS resume state moved to a per-library store, so a resumable upload interrupted before the
  upgrade restarts instead of resuming. One-off; uploads started after the upgrade are unaffected.
- **Build requirements moved.** `compileSdk` 36 or higher, Kotlin 2.1 or newer, and core library
  desugaring enabled for `net.bunny:player`. `minSdk` stays at 26 and JDK stays at 17, so device
  reach is unchanged. See [MIGRATING.md](MIGRATING.md) section 0; all three are enforced by the
  build, not just documented.
- The toolchain moved with them: Gradle 9.5, Android Gradle plugin 9.3.1, Kotlin 2.2.20,
  openapi-generator 7.24.0, Dokka 2 in v2 mode. androidx, media3 1.10.1, ktor 3.5.0, RootEncoder
  2.7.2, gson and the TUS clients came up to current at the same time.

- `BunnyStreamApi.initialize` now requires a non-null `accessKey`. Passing null never worked
  (it crashed at runtime); the parameter type now says so.
- Implementation types that leaked into the public API (player widgets, recording internals)
  are now `internal` and no longer appear in the API reference.
- `SettingsRepository.fetchSettings` and `BunnyStreamApi.fetchPlayerSettings` return
  `BunnyResult<T>` instead of `Either<String, T>`. These were the only two Arrow-typed calls on
  the 3.x public API; everything added in 4.0.0 uses `BunnyResult` from the start.
- `VideoUploader.uploadVideo` is replaced by `startUpload(libraryId, uri): String` and
  `observeUpload(uploadId): Flow<UploadEvent>?`. An upload is now an addressable thing rather than a
  call: it runs inside the SDK, keeps going when the screen that started it is destroyed, and is
  observed, paused, cancelled and continued by its upload id. Several collectors may watch one at
  once, and abandoning a collector no longer stops the transfer. As in 3.x, uploads survive
  navigation but not the process.
- `PauseState` moved from `net.bunny.api.upload.service` to `net.bunny.api.upload.model`.
- `StreamApi` no longer exposes `videosApi` or `collectionsApi`. The generated OpenAPI client is
  an implementation detail; use the repositories. See
  [MIGRATING.md](MIGRATING.md) section 4 for the type-by-type mapping.
- `BunnyPlayer.playVideo` takes the domain `Video` instead of the generated `VideoModel`.
  `BunnyStreamPlayer.playVideo(videoId)` on the view is unchanged.
- Video dimensions and framerate read as `null` before transcoding has measured them, where the
  API reports `0` — a layout no longer computes an aspect ratio of `NaN` from a placeholder.
- Comma-separated API strings arrive as lists: `availableResolutions`, `outputCodecs` and a
  collection's `previewVideoIds`.
- `RecordingRepository` in the `:recording` module is now `internal`. It was public by accident —
  nothing in the documented API took one or handed one out. Recording goes through
  `StreamCameraUploadView`; `videoRepository.createVideo` covers the video record it was creating.
- Arrow is gone from the SDK entirely: no source imports it and the dependency is no longer
  declared in any module, so it cannot reach an integrator's classpath by any route.
- `UploadService`, its two implementations, `DefaultVideoUploader`, `FileInfo` and `StreamContent`
  are now `internal`.

### Fixed

- A rejected plain upload reports the failure. The non-success branch built an error value and
  discarded it, so an upload rejected with `401` emitted nothing and the UI stalled at its last
  percentage.
- Upload progress is measured against the file size reported by the content resolver rather than
  `InputStream.available()`, which under-reports on large files and made the percentage run ahead
  of the transfer.
- Resumable uploads resume. The TUS fingerprint keying an upload's stored offset was a fresh random
  UUID per attempt, so no offset could ever be matched and every retry restarted from zero. It is
  now derived from the library and video id, and `continueUpload` is the entry point that uses it.
- An upload always ends with a terminal event. Releasing the SDK instance, or a failure outside the
  transfer itself, used to close the stream silently and leave every observer waiting forever.
- A cancel that lands while a chunk is in flight reports `Cancelled` rather than the failure that
  chunk hit on the way down — the user pressed Cancel and should not get an error.
- `CancellationException` from a genuinely cancelled coroutine propagates instead of being reported
  as an upload outcome, restoring structured concurrency.
- Deleting a cancelled upload's video no longer runs on the scope being torn down, so cancelling and
  then releasing the SDK does not leave the partial video behind.
- The stream for the picked file is closed on every path, including failure and cancellation.
- Upload metadata is read before the stream is opened, so a file with unreadable metadata no longer
  leaks a file handle.
- A rejected mutation is reported as a failure. Bunny answers `HTTP 200` with `success = false` for
  validation errors and wrong-state requests; the video and collection repositories now surface
  that as a `BunnyError` instead of `Ok`.
- `setThumbnail` works. The generated client sends `Content-Type: application/octet-stream` with no
  body for the URL variant and threw before reaching the network, so every call failed.
- Creating a video that comes back without an id is a failure rather than a `Video` with an empty
  id. An empty id reached the camera's RTMP ingest URL, which published to nothing and lost the
  recording without reporting anything.
- The camera view records to the right library when it is inflated from XML. It read the library id
  when the view was constructed — for a view in a layout, before `initialize` had run — and kept
  `-1` for its whole life, so every recording went nowhere without an error.
- Composing `BunnyLiveStreamPlayer` before the SDK is initialised no longer crashes. Its view model
  reached for the SDK in its constructor, which threw from inside composition where the app could
  not catch it; the player shows an error panel instead.
- A blocked video says so instead of surfacing a raw player error. Any `HTTP 403` — geo-blocking,
  hotlink protection or a rejected token, which the CDN does not tell apart — now shows the
  localized "Video is not available", and so does a DNS-level geo block, where the CDN host
  resolves to a loopback sinkhole and the connection is refused before any status code exists. Both
  are terminal: the live player stops its poll loop rather than retrying a stream it will never be
  allowed to play.
- A device that lost its connection is told so. A playback failure media3 files under one of its
  connectivity codes, with no HTTP status and no sinkhole behind it, now shows the localized
  "No internet connection" instead of a raw `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` — the engine
  code still goes to logcat. The outage says nothing about the video, so it stays transient: the
  live player keeps retrying and `Retry` keeps working.
- The live player's recording recovers from a playback error. Once a stream had ended, its `VodPlay`
  state was marked terminated and every recovery was dropped, so a network drop — or the roughly
  30-second window in which the recording is still being finalised and answers `404` — left the
  viewer on a frozen frame with a dead Play button. Network failures now retry unbounded and HTTP
  failures on the ended recording up to 12 attempts, and `play()` re-prepares an errored engine
  instead of leaving it idle.
- Two libraries no longer share TUS resume state. The store was one file per process keyed by a
  fingerprint of the uploaded file, so the same file uploaded from two libraries could resume into
  the wrong one.
- The library API key no longer reaches logcat. The Ktor client — used for player settings and
  plain uploads — logged full request headers, including `AccessKey` in clear text, on release
  builds as well. Both credential headers are redacted now, matching what the OkHttp path already
  did.
- The HTTP client behind player settings and plain uploads is closed when an instance is released.
  It owns a thread pool and a connection pool of its own and was never closed, so every
  `initialize` leaked one.

### Removed

- `UploadListener`, `UploadError`, `UploadRequest` (with `BasicUploadRequest` and `TusUploadRequest`)
  and `HttpStatusCodes`. `UploadError` is folded into `BunnyError`; see the mapping table in
  [MIGRATING.md](MIGRATING.md).
- `BunnyStreamApi.libraryId` and `BunnyStreamApi.baseApi`. Both were process-wide; read
  `getInstance().libraryId` and `getInstance().config.baseApi` — or the same properties of the
  instance you hold.

### Known gaps

- Uploads do not survive process death. Persist the `videoId` and the content URI (with a
  persistable URI permission) and call `continueUpload` on the next launch, or run the upload from
  a foreground service or `WorkManager` job.

<!-- TODO before the 4.0.0 release: check the "Changed" bullets against the final API and
     move the entries to a dated 4.0.0 section. -->

## [3.3.1] - 2026-08-21

### Fixed

- Camera upload records again. The VOD ingest URL was built without the separator between the
  application and the stream name, so RootEncoder published to application `ingest?` with a stream
  name that had lost its required leading `?`. The ingest server rejected that, which created the
  video entry but ingested nothing and left every camera recording empty. The URL is now built as
  `<rtmpEndpoint>/??vid=<guid>&accessKey=<key>&lib=<libraryId>`, and a regression test runs it
  through RootEncoder's own parser.

## [3.3.0] - 2026-06-01

### Added

- Public `seekTo(position)` on the player.
- Auto-contrast option for the player's position/duration readout.

## [3.2.0] - 2026-02-10

### Added

- Secure playback: `playVideo` accepts `token` / `expires` for token-protected libraries.

### Changed

- RootEncoder upgraded to 2.6.6 (16 KB page size support).

### Removed

- Progress endpoint integration (replaced by local playback state APIs).

## [3.1.0] - 2025-12-22

### Added

- Video progress tracking and playback state APIs.

## [3.0.0] - 2025-09-18

### Added

- Android TV support.

### Fixed

- Player crash fix.

## [2.0.1] - 2025-08-13

### Added

- `Referer` headers on GetPlayData requests.
- Demo APK distribution to Firebase.

## [2.0.0] - 2025-08-08

First Maven Central release under the `net.bunny` group (`net.bunny:api`, `net.bunny:player`,
`net.bunny:recording`). Earlier tags (0.9.x, 1.0.1) covered project setup and publishing
plumbing.

## Release Notes Guidance

When preparing a release, move relevant items from `[Unreleased]` into a new version section:

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added

- Add ...
```

For SDK users, call out:

- public API changes,
- migration steps,
- dependency upgrades that may affect apps,
- playback, upload, recording, or Android TV behavior changes,
- security fixes.
