//[BunnyStreamPlayer](../../../index.md)/[net.bunny.bunnystreamplayer](../index.md)/[DefaultBunnyPlayer](index.md)

# DefaultBunnyPlayer

[androidJvm]\
class [DefaultBunnyPlayer](index.md) : [BunnyPlayer](../../net.bunny.bunnystreamplayer.common/-bunny-player/index.md)

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [androidJvm]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [autoPaused](auto-paused.md) | [androidJvm]<br>open override var [autoPaused](auto-paused.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [currentPlayer](current-player.md) | [androidJvm]<br>open override var [currentPlayer](current-player.md): [Player](https://developer.android.com/reference/kotlin/androidx/media3/common/Player.html)? |
| [playerSettings](player-settings.md) | [androidJvm]<br>open override var [playerSettings](player-settings.md): PlayerSettings? |
| [playerStateListener](player-state-listener.md) | [androidJvm]<br>open override var [playerStateListener](player-state-listener.md): [PlayerStateListener](../-player-state-listener/index.md)? |
| [positionManager](position-manager.md) | [androidJvm]<br>open override var [positionManager](position-manager.md): PlaybackPositionManager? |
| [seekThumbnail](seek-thumbnail.md) | [androidJvm]<br>open override var [seekThumbnail](seek-thumbnail.md): [SeekThumbnail](../../net.bunny.bunnystreamplayer.model/-seek-thumbnail/index.md)? |

## Functions

| Name | Summary |
|---|---|
| [areSubtitlesEnabled](are-subtitles-enabled.md) | [androidJvm]<br>open override fun [areSubtitlesEnabled](are-subtitles-enabled.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [cleanupExpiredPositions](cleanup-expired-positions.md) | [androidJvm]<br>open override fun [cleanupExpiredPositions](cleanup-expired-positions.md)() |
| [clearAllSavedPositions](clear-all-saved-positions.md) | [androidJvm]<br>open override fun [clearAllSavedPositions](clear-all-saved-positions.md)() |
| [clearProgress](clear-progress.md) | [androidJvm]<br>open override fun [clearProgress](clear-progress.md)() |
| [clearSavedPosition](clear-saved-position.md) | [androidJvm]<br>open override fun [clearSavedPosition](clear-saved-position.md)(videoId: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) |
| [disableResumePosition](disable-resume-position.md) | [androidJvm]<br>open override fun [disableResumePosition](disable-resume-position.md)() |
| [enableResumePosition](enable-resume-position.md) | [androidJvm]<br>open override fun [enableResumePosition](enable-resume-position.md)(config: ResumeConfig) |
| [exportPositions](export-positions.md) | [androidJvm]<br>open override fun [exportPositions](export-positions.md)(callback: ([String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)) |
| [getAllSavedPositions](get-all-saved-positions.md) | [androidJvm]<br>open override fun [getAllSavedPositions](get-all-saved-positions.md)(callback: ([List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;PlaybackPosition&gt;) -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)) |
| [getAudioTrackOptions](get-audio-track-options.md) | [androidJvm]<br>open override fun [getAudioTrackOptions](get-audio-track-options.md)(): [AudioTrackInfoOptions](../../net.bunny.bunnystreamplayer.model/-audio-track-info-options/index.md)? |
| [getCurrentPosition](get-current-position.md) | [androidJvm]<br>open override fun [getCurrentPosition](get-current-position.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [getDuration](get-duration.md) | [androidJvm]<br>open override fun [getDuration](get-duration.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [getPlaybackSpeeds](get-playback-speeds.md) | [androidJvm]<br>open override fun [getPlaybackSpeeds](get-playback-speeds.md)(): [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)&gt; |
| [getSpeed](get-speed.md) | [androidJvm]<br>open override fun [getSpeed](get-speed.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [getSubtitles](get-subtitles.md) | [androidJvm]<br>open override fun [getSubtitles](get-subtitles.md)(): [Subtitles](../../net.bunny.bunnystreamplayer.model/-subtitles/index.md) |
| [getVideoQualityOptions](get-video-quality-options.md) | [androidJvm]<br>open override fun [getVideoQualityOptions](get-video-quality-options.md)(): [VideoQualityOptions](../../net.bunny.bunnystreamplayer.model/-video-quality-options/index.md)? |
| [getVolume](get-volume.md) | [androidJvm]<br>open override fun [getVolume](get-volume.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [importPositions](import-positions.md) | [androidJvm]<br>open override fun [importPositions](import-positions.md)(jsonData: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html), callback: ([Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)) |
| [isMuted](is-muted.md) | [androidJvm]<br>open override fun [isMuted](is-muted.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isPlaying](is-playing.md) | [androidJvm]<br>open override fun [isPlaying](is-playing.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [loadSavedSpeed](load-saved-speed.md) | [androidJvm]<br>open override fun [loadSavedSpeed](load-saved-speed.md)() |
| [mute](mute.md) | [androidJvm]<br>open override fun [mute](mute.md)() |
| [pause](pause.md) | [androidJvm]<br>open override fun [pause](pause.md)(autoPaused: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [play](play.md) | [androidJvm]<br>open override fun [play](play.md)() |
| [playVideo](play-video.md) | [androidJvm]<br>open override fun [playVideo](play-video.md)(playerView: [PlayerView](https://developer.android.com/reference/kotlin/androidx/media3/ui/PlayerView.html), video: VideoModel, retentionData: [Map](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/index.html)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;, playerSettings: PlayerSettings) |
| [release](release.md) | [androidJvm]<br>open override fun [release](release.md)() |
| [replay](replay.md) | [androidJvm]<br>open override fun [replay](replay.md)() |
| [saveCurrentProgress](save-current-progress.md) | [androidJvm]<br>open override fun [saveCurrentProgress](save-current-progress.md)() |
| [seekTo](seek-to.md) | [androidJvm]<br>open override fun [seekTo](seek-to.md)(positionMs: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |
| [selectAudioTrack](select-audio-track.md) | [androidJvm]<br>open override fun [selectAudioTrack](select-audio-track.md)(audioTrackInfo: [AudioTrackInfo](../../net.bunny.bunnystreamplayer.model/-audio-track-info/index.md)) |
| [selectQuality](select-quality.md) | [androidJvm]<br>open override fun [selectQuality](select-quality.md)(quality: [VideoQuality](../../net.bunny.bunnystreamplayer.model/-video-quality/index.md)) |
| [selectSubtitle](select-subtitle.md) | [androidJvm]<br>open override fun [selectSubtitle](select-subtitle.md)(subtitleInfo: [SubtitleInfo](../../net.bunny.bunnystreamplayer.model/-subtitle-info/index.md)) |
| [setPlaybackSpeedConfig](set-playback-speed-config.md) | [androidJvm]<br>open override fun [setPlaybackSpeedConfig](set-playback-speed-config.md)(config: [PlaybackSpeedConfig](../../net.bunny.bunnystreamplayer.config/-playback-speed-config/index.md)) |
| [setResumePosition](set-resume-position.md) | [androidJvm]<br>open override fun [setResumePosition](set-resume-position.md)(position: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |
| [setResumePositionListener](set-resume-position-listener.md) | [androidJvm]<br>open override fun [setResumePositionListener](set-resume-position-listener.md)(listener: ResumePositionListener) |
| [setSpeed](set-speed.md) | [androidJvm]<br>open override fun [setSpeed](set-speed.md)(speed: [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) |
| [setSubtitlesEnabled](set-subtitles-enabled.md) | [androidJvm]<br>open override fun [setSubtitlesEnabled](set-subtitles-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setVolume](set-volume.md) | [androidJvm]<br>open override fun [setVolume](set-volume.md)(volume: [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) |
| [skipForward](skip-forward.md) | [androidJvm]<br>open override fun [skipForward](skip-forward.md)() |
| [stop](stop.md) | [androidJvm]<br>open override fun [stop](stop.md)() |
| [unmute](unmute.md) | [androidJvm]<br>open override fun [unmute](unmute.md)() |