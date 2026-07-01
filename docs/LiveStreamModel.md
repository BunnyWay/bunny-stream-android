
# LiveStreamModel

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**videoLibraryId** | **kotlin.Long** | The ID of the video library that the live stream belongs to |  [optional]
**guid** | **kotlin.String** | The unique ID of the live stream |  [optional]
**title** | **kotlin.String** | The title of the live stream |  [optional]
**description** | **kotlin.String** | The description of the live stream |  [optional]
**category** | **kotlin.String** | The category of the live stream |  [optional]
**collectionId** | **kotlin.String** | The ID of the collection where the live stream belongs |  [optional]
**&#x60;public&#x60;** | **kotlin.Boolean** | Determines if the live stream is publicly accessible |  [optional]
**status** | [**net.bunny.api.model.LiveStreamStatus**](LiveStreamModelStatus.md) |  |  [optional]
**dateCreated** | **kotlin.String** | The date when the live stream was created |  [optional]
**scheduledStartTime** | **kotlin.String** | The scheduled start time of the live stream (optional) |  [optional]
**scheduledEndTime** | **kotlin.String** | The scheduled end time of the live stream (optional) |  [optional]
**startedAt** | **kotlin.String** | The date and time when the live stream publishing started |  [optional]
**endedAt** | **kotlin.String** | The date and time when the live stream publishing ended |  [optional]
**durationSeconds** | **kotlin.Int** | The duration of the live stream in seconds |  [optional]
**streamKey** | **kotlin.String** | The stream key required for publishing the live stream |  [optional]
**playbackUrlHls** | **kotlin.String** | The HLS playback URL of the live stream |  [optional]
**dvrEnabled** | **kotlin.Boolean** | Determines if DVR is enabled for the live stream |  [optional]
**dvrWindowSeconds** | **kotlin.Int** | The DVR window size in seconds, required when DvrEnabled is true |  [optional]
**recordVod** | **kotlin.Boolean** | Determines if a VOD recording should be created for the live stream |  [optional]
**availableResolutions** | **kotlin.String** | The available output resolutions of the live stream |  [optional]
**width** | **kotlin.Int** | The input video width in pixels |  [optional]
**height** | **kotlin.Int** | The input video height in pixels |  [optional]
**framerate** | **kotlin.Double** | The framerate of the input stream |  [optional]
**ingestRegion** | **kotlin.String** | The primary ingest region of the live stream |  [optional]
**peakConcurrentViewers** | **kotlin.Int** | The peak number of concurrent viewers |  [optional]
**totalViewerSeconds** | **kotlin.Long** | The total viewer watch time in seconds |  [optional]
**thumbnailFileName** | **kotlin.String** | The file name of the thumbnail inside of the storage |  [optional]
**thumbnailUpdatedAt** | **kotlin.String** | The date and time when the thumbnail was last updated |  [optional]
**enableCountdown** | **kotlin.Boolean** | Determines if countdown should be shown in player before the stream start, if it was scheduled |  [optional]
**rtmpOutputs** | [**kotlin.collections.List&lt;RtmpOutput&gt;**](RtmpOutput.md) | A list of up to 4 RTMP outputs that the incoming stream will be forwarded to |  [optional]
**preStreamTrailerVideoId** | **kotlin.String** | Video ID of the trailer that will be played before the live stream starts |  [optional]
**ingestEndpoints** | [**IngestEndpoints**](IngestEndpoints.md) |  |  [optional]



