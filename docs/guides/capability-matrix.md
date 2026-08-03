# Capability matrix: VOD vs Live

What each playback mode supports on Android, plus availability notes.

| Capability | VOD | Live |
|---|---|---|
| Playback (HLS) | yes | yes |
| Entry point | `BunnyStreamPlayer` (View, XML or Compose via `AndroidView`) | `BunnyLiveStreamPlayer` (Compose only) |
| Seeking | full timeline with preview thumbnails | within the DVR window, when DVR is enabled for the stream |
| Pause | yes | yes with DVR; without DVR playback stays at the live edge |
| Playback speed | yes (dashboard-driven list, code override available) | pinned to 1x |
| Captions | yes | not currently delivered for live |
| Chapters and moments | yes | n/a |
| Quality selection | yes | automatic |
| Picture-in-Picture | yes | yes |
| Chromecast | yes | yes |
| Fullscreen | yes | yes |
| Resume positions | yes (opt-in) | n/a |
| Appearance | dashboard player settings + `PlayerIconSet` in code | dashboard player settings |
| Token authentication | yes | yes |
| Countdown before start | n/a | yes, for scheduled streams |
| Pre-stream trailer | n/a | yes, muted loop |
| Switch to recording after the stream ends | n/a | yes, when the stream records a VOD |
| Vertical (9:16) content | yes (`onVideoSizeChanged`) | yes (`onVideoSizeChanged`) |

## Broadcasting (camera)

| Capability | Record to VOD | Broadcast to live stream |
|---|---|---|
| Entry point | `BunnyStreamCameraUpload`, `liveStreamId` unset | same view, `liveStreamId` set |
| Server-side stream start/stop | n/a | automatic |
| Reconnect on network drop | reconnects | reconnects with primary/backup failover |
| Dual publish (primary + backup at once) | n/a | opt-in via `dualPublish` (doubles upload bandwidth) |
| Front/back camera, mute | yes | yes |
| Connection badges | n/a | built-in, plus `onIngestEndpointChanged` for custom UI |

## Availability notes

- **DASH**: the Android playback engine also plays DASH manifests, but Bunny's API currently
  serves live and VOD over HLS only, so all playback today is HLS. (iOS is HLS-only by platform.)
- **Dual publish** is Android-only; the iOS SDK uses single publishing with failover.
- **Live captions** are not currently available end to end.
- **Watch-time heatmap** applies to VOD playback; a running live stream has no retention data.
