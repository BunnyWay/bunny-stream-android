//[BunnyStreamPlayer](../../../index.md)/[net.bunny.bunnystreamplayer.ui](../index.md)/[BunnyPlayer](index.md)

# BunnyPlayer

interface [BunnyPlayer](index.md)

#### Inheritors

| |
|---|
| [BunnyStreamPlayer](../-bunny-stream-player/index.md) |

## Properties

| Name | Summary |
|---|---|
| [iconSet](icon-set.md) | [androidJvm]<br>abstract var [iconSet](icon-set.md): [PlayerIconSet](../../net.bunny.bunnystreamplayer.model/-player-icon-set/index.md)<br>Apply custom icons to video player interface |

## Functions

| Name | Summary |
|---|---|
| [pause](pause.md) | [androidJvm]<br>abstract fun [pause](pause.md)()<br>Pauses video |
| [play](play.md) | [androidJvm]<br>abstract fun [play](play.md)()<br>Resumes playing video |
| [playVideo](play-video.md) | [androidJvm]<br>abstract fun [playVideo](play-video.md)(videoId: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html), libraryId: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)?)<br>Plays a video and fetches additional info, e.g. chapters, moments and subtitles |