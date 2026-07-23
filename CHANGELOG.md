# Changelog

All notable changes to Bunny Stream Android are documented in this file.

This project follows semantic versioning where possible:

- `MAJOR` versions may include breaking API or behavior changes.
- `MINOR` versions add functionality in a backward-compatible way.
- `PATCH` versions include backward-compatible bug fixes and maintenance updates.

## [Unreleased]

Unifies asynchronous results and error reporting across the SDK. Every item below is a breaking
API change; see [MIGRATING.md](MIGRATING.md) for before/after examples.

### Added

- `BunnyResult<T>` — the result envelope returned by every management call: `Ok(value)` or
  `Err(BunnyError)`, with `getOrNull()`, `errorOrNull()`, `map` and `fold(onOk, onErr)`.
- `BunnyError` — typed error taxonomy (`Auth`, `NotFound`, `Http`, `Network`, `Decode`,
  `LocalFile`). Every variant exposes `httpStatus` and `isTerminal`, so callers no longer parse
  message strings to decide whether to retry.
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
- `CancellationException` is rethrown instead of being reported as an upload failure, restoring
  structured concurrency.
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

## Release Notes Guidance

When preparing a release, move relevant items from `[Unreleased]` into a new version section:

```markdown
## [3.4.0] - 2026-06-05

### Added

- Add ...

### Fixed

- Fix ...
```

For SDK users, call out:

- public API changes,
- migration steps,
- dependency upgrades that may affect apps,
- playback, upload, recording, or Android TV behavior changes,
- security fixes.
