
# VideoModel

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**videoLibraryId** | **kotlin.Long** | The ID of the video library that the video belongs to |  [optional]
**guid** | **kotlin.String** | The unique ID of the video |  [optional]
**title** | **kotlin.String** | The title of the video |  [optional]
**description** | **kotlin.String** | The description of the video |  [optional]
**dateUploaded** | **kotlin.String** | The date when the video was uploaded |  [optional]
**views** | **kotlin.Long** | The number of views the video received |  [optional]
**isPublic** | **kotlin.Boolean** | Determines if the video is publically accessible |  [optional]
**length** | **kotlin.Int** | The duration of the video in seconds |  [optional]
**status** | [**net.bunny.api.model.VideoModelStatus**](VideoModelStatus.md) |  |  [optional]
**framerate** | **kotlin.Double** | The framerate of the video |  [optional]
**rotation** | **kotlin.Int** | The rotation of the video |  [optional]
**width** | **kotlin.Int** | The width of the original video file |  [optional]
**height** | **kotlin.Int** | The height of the original video file |  [optional]
**availableResolutions** | **kotlin.String** | The available resolutions of the video |  [optional]
**outputCodecs** | **kotlin.String** | Encoded output codecs of the video |  [optional]
**thumbnailCount** | **kotlin.Int** | The number of thumbnails generated for this video |  [optional]
**encodeProgress** | **kotlin.Int** | The current encode progress of the video |  [optional]
**storageSize** | **kotlin.Long** | The amount of storage used by this video |  [optional]
**captions** | [**kotlin.collections.List&lt;CaptionModel&gt;**](CaptionModel.md) | The list of captions available for the video |  [optional]
**hasMP4Fallback** | **kotlin.Boolean** | Determines if the video has MP4 fallback files generated |  [optional]
**collectionId** | **kotlin.String** | The ID of the collection where the video belongs |  [optional]
**thumbnailFileName** | **kotlin.String** | The file name of the thumbnail inside of the storage |  [optional]
**thumbnailBlurhash** | **kotlin.String** | Thumbnail blurhash to show while the actual thumbnail is being loaded |  [optional]
**averageWatchTime** | **kotlin.Long** | The average watch time of the video in seconds |  [optional]
**totalWatchTime** | **kotlin.Long** | The total video watch time in seconds |  [optional]
**category** | **kotlin.String** | The automatically detected category of the video |  [optional]
**chapters** | [**kotlin.collections.List&lt;ChapterModel&gt;**](ChapterModel.md) | The list of chapters available for the video |  [optional]
**moments** | [**kotlin.collections.List&lt;MomentModel&gt;**](MomentModel.md) | The list of moments available for the video |  [optional]
**metaTags** | [**kotlin.collections.List&lt;MetaTagModel&gt;**](MetaTagModel.md) | The list of meta tags that have been added to the video |  [optional]
**transcodingMessages** | [**kotlin.collections.List&lt;TranscodingMessageModel&gt;**](TranscodingMessageModel.md) | The list of transcoding messages that describe potential issues while the video was transcoding |  [optional]
**jitEncodingEnabled** | **kotlin.Boolean** |  |  [optional]
**smartGenerateStatus** | [**net.bunny.api.model.SmartGenerateStatus**](VideoModelSmartGenerateStatus.md) |  |  [optional]
**smartGenerateFeaturesStatus** | [**VideoModelSmartGenerateFeaturesStatus**](VideoModelSmartGenerateFeaturesStatus.md) |  |  [optional]
**hasOriginal** | **kotlin.Boolean** | Determines if video has the original file available in storage |  [optional]
**originalHash** | **kotlin.String** | Original uploaded file SHA256 hash |  [optional]
**hasHighQualityPreview** | **kotlin.Boolean** |  |  [optional]



