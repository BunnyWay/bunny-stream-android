//[BunnyStreamPlayer](../../../index.md)/[net.bunny.bunnystreamplayer.common](../index.md)/[BunnyPlayer](index.md)

# BunnyPlayer

interface [BunnyPlayer](index.md)

#### Inheritors

| |
|---|
| [DefaultBunnyPlayer](../../net.bunny.bunnystreamplayer/-default-bunny-player/index.md) |

## Properties

| Name | Summary |
|---|---|
| [autoPaused](auto-paused.md) | [androidJvm]<br>abstract var [autoPaused](auto-paused.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [currentPlayer](current-player.md) | [androidJvm]<br>abstract var [currentPlayer](current-player.md): [Player](https://developer.android.com/reference/kotlin/androidx/media3/common/Player.html)? |
| [playerSettings](player-settings.md) | [androidJvm]<br>abstract var [playerSettings](player-settings.md): PlayerSettings? |
| [playerStateListener](player-state-listener.md) | [androidJvm]<br>abstract var [playerStateListener](player-state-listener.md): [PlayerStateListener](../../net.bunny.bunnystreamplayer/-player-state-listener/index.md)? |
| [positionManager](position-manager.md) | [androidJvm]<br>abstract var [positionManager](position-manager.md): PlaybackPositionManager? |
| [seekThumbnail](seek-thumbnail.md) | [androidJvm]<br>abstract var [seekThumbnail](seek-thumbnail.md): [SeekThumbnail](../../net.bunny.bunnystreamplayer.model/-seek-thumbnail/index.md)? |

## Functions

| Name | Summary |
|---|---|
| [areSubtitlesEnabled](are-subtitles-enabled.md) | [androidJvm]<br>abstract fun [areSubtitlesEnabled](are-subtitles-enabled.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [cleanupExpiredPositions](cleanup-expired-positions.md) | [androidJvm]<br>abstract fun [cleanupExpiredPositions](cleanup-expired-positions.md)() |
| [clearAllSavedPositions](clear-all-saved-positions.md) | [androidJvm]<br>abstract fun [clearAllSavedPositions](clear-all-saved-positions.md)() |
| [clearProgress](clear-progress.md) | [androidJvm]<br>abstract fun [clearProgress](clear-progress.md)() |
| [clearSavedPosition](clear-saved-position.md) | [androidJvm]<br>abstract fun [clearSavedPosition](clear-saved-position.md)(videoId: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) |
| [disableResumePosition](disable-resume-position.md) | [androidJvm]<br>abstract fun [disableResumePosition](disable-resume-position.md)() |
| [enableResumePosition](enable-resume-position.md) | [androidJvm]<br>abstract fun [enableResumePosition](enable-resume-position.md)(config: ResumeConfig = ResumeConfig()) |
| [exportPositions](export-positions.md) | [androidJvm]<br>abstract fun [exportPositions](export-positions.md)(callback: ([String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)) |
| [getAllSavedPositions](get-all-saved-positions.md) | [androidJvm]<br>abstract fun [getAllSavedPositions](get-all-saved-positions.md)(callback: ([List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;PlaybackPosition&gt;) -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)) |
| [getAudioTrackOptions](get-audio-track-options.md) | [androidJvm]<br>abstract fun [getAudioTrackOptions](get-audio-track-options.md)(): [AudioTrackInfoOptions](../../net.bunny.bunnystreamplayer.model/-audio-track-info-options/index.md)? |
| [getCurrentPosition](get-current-position.md) | [androidJvm]<br>abstract fun [getCurrentPosition](get-current-position.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [getDuration](get-duration.md) | [androidJvm]<br>abstract fun [getDuration](get-duration.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [getPlaybackSpeeds](get-playback-speeds.md) | [androidJvm]<br>abstract fun [getPlaybackSpeeds](get-playback-speeds.md)(): [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)&gt; |
| [getSpeed](get-speed.md) | [androidJvm]<br>abstract fun [getSpeed](get-speed.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [getSubtitles](get-subtitles.md) | [androidJvm]<br>abstract fun [getSubtitles](get-subtitles.md)(): [Subtitles](../../net.bunny.bunnystreamplayer.model/-subtitles/index.md) |
| [getVideoQualityOptions](get-video-quality-options.md) | [androidJvm]<br>abstract fun [getVideoQualityOptions](get-video-quality-options.md)(): [VideoQualityOptions](../../net.bunny.bunnystreamplayer.model/-video-quality-options/index.md)? |
| [getVolume](get-volume.md) | [androidJvm]<br>@[FloatRange](https://developer.android.com/reference/kotlin/androidx/annotation/FloatRange.html)(from = 0.0, to = 1.0)<br>abstract fun [getVolume](get-volume.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [importPositions](import-positions.md) | [androidJvm]<br>abstract fun [importPositions](import-positions.md)(jsonData: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html), callback: ([Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)) |
| [isMuted](is-muted.md) | [androidJvm]<br>abstract fun [isMuted](is-muted.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isPlaying](is-playing.md) | [androidJvm]<br>abstract fun [isPlaying](is-playing.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [loadSavedSpeed](load-saved-speed.md) | [androidJvm]<br>abstract fun [loadSavedSpeed](load-saved-speed.md)() |
| [mute](mute.md) | [androidJvm]<br>abstract fun [mute](mute.md)() |
| [pause](pause.md) | [androidJvm]<br>abstract fun [pause](pause.md)(autoPaused: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false) |
| [play](play.md) | [androidJvm]<br>abstract fun [play](play.md)() |
| [playVideo](play-video.md) | [androidJvm]<br>abstract fun [playVideo](play-video.md)(playerView: [PlayerView](https://developer.android.com/reference/kotlin/androidx/media3/ui/PlayerView.html), video: VideoModel, retentionData: [Map](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/index.html)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;, playerSettings: PlayerSettings) |
| [release](release.md) | [androidJvm]<br>abstract fun [release](release.md)() |
| [replay](replay.md) | [androidJvm]<br>abstract fun [replay](replay.md)() |
| [saveCurrentProgress](save-current-progress.md) | [androidJvm]<br>abstract fun [saveCurrentProgress](save-current-progress.md)() |
| [seekTo](seek-to.md) | [androidJvm]<br>abstract fun [seekTo](seek-to.md)(positionMs: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |
| [selectAudioTrack](select-audio-track.md) | [androidJvm]<br>abstract fun [selectAudioTrack](select-audio-track.md)(audioTrackInfo: [AudioTrackInfo](../../net.bunny.bunnystreamplayer.model/-audio-track-info/index.md)) |
| [selectQuality](select-quality.md) | [androidJvm]<br>abstract fun [selectQuality](select-quality.md)(quality: [VideoQuality](../../net.bunny.bunnystreamplayer.model/-video-quality/index.md)) |
| [selectSubtitle](select-subtitle.md) | [androidJvm]<br>abstract fun [selectSubtitle](select-subtitle.md)(subtitleInfo: [SubtitleInfo](../../net.bunny.bunnystreamplayer.model/-subtitle-info/index.md)) |
| [setPlaybackSpeedConfig](set-playback-speed-config.md) | [androidJvm]<br>abstract fun [setPlaybackSpeedConfig](set-playback-speed-config.md)(config: [PlaybackSpeedConfig](../../net.bunny.bunnystreamplayer.config/-playback-speed-config/index.md)) |
| [setResumePosition](set-resume-position.md) | [androidJvm]<br>abstract fun [setResumePosition](set-resume-position.md)(position: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |
| [setResumePositionListener](set-resume-position-listener.md) | [androidJvm]<br>abstract fun [setResumePositionListener](set-resume-position-listener.md)(listener: ResumePositionListener) |
| [setSpeed](set-speed.md) | [androidJvm]<br>abstract fun [setSpeed](set-speed.md)(speed: [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) |
| [setSubtitlesEnabled](set-subtitles-enabled.md) | [androidJvm]<br>abstract fun [setSubtitlesEnabled](set-subtitles-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setVolume](set-volume.md) | [androidJvm]<br>abstract fun [setVolume](set-volume.md)(@[FloatRange](https://developer.android.com/reference/kotlin/androidx/annotation/FloatRange.html)(from = 0.0, to = 1.0)volume: [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) |
| [skipForward](skip-forward.md) | [androidJvm]<br>abstract fun [skipForward](skip-forward.md)() |
| [stop](stop.md) | [androidJvm]<br>abstract fun [stop](stop.md)() |
| [unmute](unmute.md) | [androidJvm]<br>abstract fun [unmute](unmute.md)() |