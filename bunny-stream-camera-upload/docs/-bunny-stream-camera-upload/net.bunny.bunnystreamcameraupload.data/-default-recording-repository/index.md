//[BunnyStreamCameraUpload](../../../index.md)/[net.bunny.bunnystreamcameraupload.data](../index.md)/[DefaultRecordingRepository](index.md)

# DefaultRecordingRepository

[androidJvm]\
class [DefaultRecordingRepository](index.md)(coroutineDispatcher: CoroutineDispatcher) : [RecordingRepository](../../net.bunny.bunnystreamcameraupload.domain/-recording-repository/index.md)

## Constructors

| | |
|---|---|
| [DefaultRecordingRepository](-default-recording-repository.md) | [androidJvm]<br>constructor(coroutineDispatcher: CoroutineDispatcher) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [androidJvm]<br>object [Companion](-companion/index.md) |

## Functions

| Name | Summary |
|---|---|
| [prepareRecording](prepare-recording.md) | [androidJvm]<br>open suspend override fun [prepareRecording](prepare-recording.md)(libraryId: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): Either&lt;[String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)&gt; |