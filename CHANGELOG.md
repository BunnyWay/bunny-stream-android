# Changelog

All notable changes to Bunny Stream Android are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project follows semantic versioning:

- `MAJOR` versions may include breaking API or behavior changes.
- `MINOR` versions add functionality in a backward-compatible way.
- `PATCH` versions include backward-compatible bug fixes and maintenance updates.

## [Unreleased] - 4.0.0

The live streaming release, bundled with an architecture refactor. Existing integrations: see
[MIGRATING.md](MIGRATING.md).

Unifies asynchronous results and error reporting across the SDK. Every item below is a breaking
API change; see [MIGRATING.md](MIGRATING.md) for before/after examples.

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
- Live player appearance follows the library's player settings from the Bunny dashboard
  (accent color, font, language, controls, compact mode).
- Hosted API reference (Dokka) published from CI, plus task-oriented integration guides under
  `docs/guides/`.

### Changed

- `BunnyStreamApi.initialize` now requires a non-null `accessKey`. Passing null never worked
  (it crashed at runtime); the parameter type now says so.
- Implementation types that leaked into the public API (player widgets, recording internals)
  are now `internal` and no longer appear in the API reference.
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
- `BunnyError.InvalidState` — the call succeeded but the resource's state forbids the operation.
  The one variant whose terminality is explicit, because it genuinely varies: an ended live stream
  never becomes publishable, while a missing stream key usually appears moments later.

### Changed

- `LiveStreamRepository`, `SettingsRepository` and `BunnyStreamApi.fetchPlayerSettings` return
  `BunnyResult<T>` instead of `Either<String, T>`.
- `VideoUploader.uploadVideo` is replaced by `startUpload(libraryId, uri): String` and
  `observeUpload(uploadId): Flow<UploadEvent>?`. An upload is now an addressable thing rather than a
  call: it runs inside the SDK, keeps going when the screen that started it is destroyed, and is
  observed, paused, cancelled and continued by its upload id. Several collectors may watch one at
  once, and abandoning a collector no longer stops the transfer. As in 3.x, uploads survive
  navigation but not the process.
- `PauseState` moved from `net.bunny.api.upload.service` to `net.bunny.api.upload.model`.
- `RecordingRepository` in the `:recording` module returns `BunnyResult<T>` instead of
  `Either<String, T>`, completing the migration. Its two client-side preconditions — publishing to
  an ended stream, and a stream whose key has not been issued — now arrive as
  `BunnyError.InvalidState` with the correct terminality rather than as indistinguishable strings.
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

### Removed

- `UploadListener`, `UploadError`, `UploadRequest` (with `BasicUploadRequest` and `TusUploadRequest`)
  and `HttpStatusCodes`. `UploadError` is folded into `BunnyError`; see the mapping table in
  [MIGRATING.md](MIGRATING.md).

### Known gaps

- Uploads do not survive process death. Persist the `videoId` and the content URI (with a
  persistable URI permission) and call `continueUpload` on the next launch, or run the upload from
  a foreground service or `WorkManager` job.

<!-- TODO before the 4.0.0 release: check the "Changed" bullets against the final API and
     move the entries to a dated 4.0.0 section. -->

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
