# Changelog

All notable changes to Bunny Stream Android are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project follows semantic versioning:

- `MAJOR` versions may include breaking API or behavior changes.
- `MINOR` versions add functionality in a backward-compatible way.
- `PATCH` versions include backward-compatible bug fixes and maintenance updates.

## [Unreleased] - 4.0.0

The live streaming release, bundled with an architecture refactor. Existing integrations: see
[MIGRATING.md](MIGRATING.md).

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
- Architecture refactor (in development, ships with this release; details in
  [MIGRATING.md](MIGRATING.md)):
  - management calls return a unified result envelope with HTTP status codes instead of
    `Either<String, T>`,
  - the SDK session is per-instance instead of a process-wide singleton,
  - generated REST types are hidden behind domain repositories,
  - the upload path reports progress through a Flow-based API instead of callbacks.
- Implementation types that leaked into the public API (player widgets, recording internals)
  are now `internal` and no longer appear in the API reference.

### Fixed

- Nothing yet.

### Deprecated

- Nothing yet.

### Removed

- Nothing yet.

### Security

- Nothing yet.

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
