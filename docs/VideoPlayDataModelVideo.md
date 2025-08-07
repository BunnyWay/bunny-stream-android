
# VideoPlayDataModelVideo

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**videoLibraryId** | **kotlin.Long** | The ID of the video library that the video belongs to. |  [optional]
**guid** | **kotlin.String** | The unique identifier of the video. |  [optional]
**title** | **kotlin.String** | The title of the video. |  [optional]
**dateUploaded** | **kotlin.String** | The date and time when the video was uploaded. |  [optional]
**views** | **kotlin.Long** | The number of views the video has received. |  [optional]
**isPublic** | **kotlin.Boolean** | Determines if the video is publicly accessible. |  [optional]
**length** | **kotlin.Int** | The duration of the video in seconds. |  [optional]
**status** | [**net.bunny.api.model.VideoModelStatus**](VideoModelStatus.md) |  |  [optional]
**framerate** | **kotlin.Double** | The framerate of the video. |  [optional]
**rotation** | **kotlin.Int** | The rotation (in degrees) of the video if applicable. |  [optional]
**width** | **kotlin.Int** | The width of the original video in pixels. |  [optional]
**height** | **kotlin.Int** | The height of the original video in pixels. |  [optional]
**availableResolutions** | **kotlin.String** | A comma-separated list of resolutions available for the video. |  [optional]
**outputCodecs** | **kotlin.String** | A comma-separated list of output codecs used for video encoding. |  [optional]
**thumbnailCount** | **kotlin.Int** | The number of thumbnails generated for the video. |  [optional]
**encodeProgress** | **kotlin.Int** | The current encoding progress of the video as a percentage. |  [optional]
**storageSize** | **kotlin.Long** | The total storage size of the video file in bytes. |  [optional]
**captions** | [**kotlin.collections.List&lt;CaptionModel&gt;**](CaptionModel.md) | A list of captions available for the video. |  [optional]
**hasMP4Fallback** | **kotlin.Boolean** | Indicates if MP4 fallback files are available for the video. |  [optional]
**collectionId** | **kotlin.String** | The identifier of the collection that the video belongs to. |  [optional]
**thumbnailFileName** | **kotlin.String** | The file name of the thumbnail stored on the server. |  [optional]
**averageWatchTime** | **kotlin.Long** | The average watch time of the video in seconds. |  [optional]
**totalWatchTime** | **kotlin.Long** | The total accumulated watch time of the video in seconds. |  [optional]
**category** | **kotlin.String** | The automatically detected category of the video. |  [optional]
**chapters** | [**kotlin.collections.List&lt;ChapterModel&gt;**](ChapterModel.md) | A list of chapters within the video. |  [optional]
**moments** | [**kotlin.collections.List&lt;MomentModel&gt;**](MomentModel.md) | A list of significant moments or events in the video. |  [optional]
**metaTags** | [**kotlin.collections.List&lt;MetaTagModel&gt;**](MetaTagModel.md) | A list of metadata tags associated with the video. |  [optional]
**transcodingMessages** | [**kotlin.collections.List&lt;TranscodingMessageModel&gt;**](TranscodingMessageModel.md) | Messages generated during transcoding that indicate warnings or errors. |  [optional]



