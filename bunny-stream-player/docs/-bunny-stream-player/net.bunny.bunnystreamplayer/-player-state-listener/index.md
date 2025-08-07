//[BunnyStreamPlayer](../../../index.md)/[net.bunny.bunnystreamplayer](../index.md)/[PlayerStateListener](index.md)

# PlayerStateListener

[androidJvm]\
interface [PlayerStateListener](index.md)

## Functions

| Name | Summary |
|---|---|
| [onChaptersUpdated](on-chapters-updated.md) | [androidJvm]<br>abstract fun [onChaptersUpdated](on-chapters-updated.md)(chapters: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Chapter](../../net.bunny.bunnystreamplayer.model/-chapter/index.md)&gt;) |
| [onLoadingChanged](on-loading-changed.md) | [androidJvm]<br>abstract fun [onLoadingChanged](on-loading-changed.md)(isLoading: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [onMomentsUpdated](on-moments-updated.md) | [androidJvm]<br>abstract fun [onMomentsUpdated](on-moments-updated.md)(moments: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Moment](../../net.bunny.bunnystreamplayer.model/-moment/index.md)&gt;) |
| [onMutedChanged](on-muted-changed.md) | [androidJvm]<br>abstract fun [onMutedChanged](on-muted-changed.md)(isMuted: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [onPlaybackSpeedChanged](on-playback-speed-changed.md) | [androidJvm]<br>abstract fun [onPlaybackSpeedChanged](on-playback-speed-changed.md)(speed: [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) |
| [onPlayerError](on-player-error.md) | [androidJvm]<br>abstract fun [onPlayerError](on-player-error.md)(message: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) |
| [onPlayerTypeChanged](on-player-type-changed.md) | [androidJvm]<br>abstract fun [onPlayerTypeChanged](on-player-type-changed.md)(player: [Player](https://developer.android.com/reference/kotlin/androidx/media3/common/Player.html), playerType: [PlayerType](../-player-type/index.md)) |
| [onPlayingChanged](on-playing-changed.md) | [androidJvm]<br>abstract fun [onPlayingChanged](on-playing-changed.md)(isPlaying: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [onRetentionGraphUpdated](on-retention-graph-updated.md) | [androidJvm]<br>abstract fun [onRetentionGraphUpdated](on-retention-graph-updated.md)(points: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[RetentionGraphEntry](../../net.bunny.bunnystreamplayer.model/-retention-graph-entry/index.md)&gt;) |