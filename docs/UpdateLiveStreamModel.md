
# UpdateLiveStreamModel

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **title** | **kotlin.String** | The title of the live stream |  [optional] |
| **description** | **kotlin.String** | The description of the live stream |  [optional] |
| **collectionId** | **kotlin.String** | The ID of the collection where the live stream belongs |  [optional] |
| **dvrEnabled** | **kotlin.Boolean** | Determines if DVR is enabled for the live stream |  [optional] |
| **dvrWindowSeconds** | **kotlin.Int** | The DVR window size in seconds, required when DvrEnabled is true, max is 12 hours |  [optional] |
| **recordVod** | **kotlin.Boolean** | Determines if a VOD recording should be created for the live stream |  [optional] |
| **scheduledStartTime** | **kotlin.String** | The scheduled start time of the live stream (optional, UTC) |  [optional] |
| **scheduledEndTime** | **kotlin.String** | The scheduled end time of the live stream (optional, UTC) |  [optional] |
| **&#x60;public&#x60;** | **kotlin.Boolean** | Determines if the live stream is publicly accessible, defaults to true |  [optional] |
| **enableCountdown** | **kotlin.Boolean** | Determines if countdown should be shown in player before the stream start, if it was scheduled (optional) |  [optional] |
| **preStreamTrailerVideoId** | **kotlin.String** | Video ID of the trailer that will be played before the live stream starts |  [optional] |
| **rtmpOutputs** | [**kotlin.collections.List&lt;RtmpOutput&gt;**](RtmpOutput.md) | A list of up to 4 RTMP outputs that the incoming stream will be forwarded to |  [optional] |



