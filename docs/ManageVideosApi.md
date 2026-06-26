# ManageVideosApi

All URIs are relative to *https://video.bunnycdn.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**videoAddCaption**](ManageVideosApi.md#videoAddCaption) | **POST** /library/{libraryId}/videos/{videoId}/captions/{srclang} | Add Caption
[**videoCreateVideo**](ManageVideosApi.md#videoCreateVideo) | **POST** /library/{libraryId}/videos | Create Video
[**videoDeleteCaption**](ManageVideosApi.md#videoDeleteCaption) | **DELETE** /library/{libraryId}/videos/{videoId}/captions/{srclang} | Delete Caption
[**videoDeleteResolutions**](ManageVideosApi.md#videoDeleteResolutions) | **POST** /library/{libraryId}/videos/{videoId}/resolutions/cleanup | Cleanup unconfigured resolutions
[**videoDeleteVideo**](ManageVideosApi.md#videoDeleteVideo) | **DELETE** /library/{libraryId}/videos/{videoId} | Delete Video
[**videoFetchNewVideo**](ManageVideosApi.md#videoFetchNewVideo) | **POST** /library/{libraryId}/videos/fetch | Fetch Video
[**videoFetchVideo**](ManageVideosApi.md#videoFetchVideo) | **POST** /library/{libraryId}/videos/{videoId}/fetch | Fetch Video
[**videoGetVideo**](ManageVideosApi.md#videoGetVideo) | **GET** /library/{libraryId}/videos/{videoId} | Get Video
[**videoGetVideoHeatmap**](ManageVideosApi.md#videoGetVideoHeatmap) | **GET** /library/{libraryId}/videos/{videoId}/heatmap | Get Video Heatmap
[**videoGetVideoHeatmapData**](ManageVideosApi.md#videoGetVideoHeatmapData) | **GET** /library/{libraryId}/videos/{videoId}/play/heatmap | Get Video heatmap data
[**videoGetVideoPlayData**](ManageVideosApi.md#videoGetVideoPlayData) | **GET** /library/{libraryId}/videos/{videoId}/play | Get Video play data
[**videoGetVideoResolutions**](ManageVideosApi.md#videoGetVideoResolutions) | **GET** /library/{libraryId}/videos/{videoId}/resolutions | Video resolutions info
[**videoGetVideoStatistics**](ManageVideosApi.md#videoGetVideoStatistics) | **GET** /library/{libraryId}/statistics | Get Video Statistics
[**videoGetVideoStorageSize**](ManageVideosApi.md#videoGetVideoStorageSize) | **GET** /library/{libraryId}/videos/{videoId}/storage | Get video storage size info
[**videoList**](ManageVideosApi.md#videoList) | **GET** /library/{libraryId}/videos | List Videos
[**videoReencodeUsingCodec**](ManageVideosApi.md#videoReencodeUsingCodec) | **PUT** /library/{libraryId}/videos/{videoId}/outputs/{outputCodecId} | Add output codec to video
[**videoReencodeVideo**](ManageVideosApi.md#videoReencodeVideo) | **POST** /library/{libraryId}/videos/{videoId}/reencode | Reencode Video
[**videoRepackage**](ManageVideosApi.md#videoRepackage) | **POST** /library/{libraryId}/videos/{videoId}/repackage | Repackage Video
[**videoSetThumbnail**](ManageVideosApi.md#videoSetThumbnail) | **POST** /library/{libraryId}/videos/{videoId}/thumbnail | Set Thumbnail
[**videoSmartGenerate**](ManageVideosApi.md#videoSmartGenerate) | **POST** /library/{libraryId}/videos/{videoId}/smart | Trigger Smart actions
[**videoTranscribeVideo**](ManageVideosApi.md#videoTranscribeVideo) | **POST** /library/{libraryId}/videos/{videoId}/transcribe | Transcribe video
[**videoUpdateVideo**](ManageVideosApi.md#videoUpdateVideo) | **POST** /library/{libraryId}/videos/{videoId} | Update Video
[**videoUploadVideo**](ManageVideosApi.md#videoUploadVideo) | **PUT** /library/{libraryId}/videos/{videoId} | Upload Video


<a id="videoAddCaption"></a>
# **videoAddCaption**
> StatusModel videoAddCaption(libraryId, videoId, srclang, videoAddCaptionRequest)

Add Caption

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val srclang : kotlin.String = srclang_example // kotlin.String | 
val videoAddCaptionRequest : VideoAddCaptionRequest =  // VideoAddCaptionRequest | 
try {
    val result : StatusModel = apiInstance.videoAddCaption(libraryId, videoId, srclang, videoAddCaptionRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoAddCaption")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoAddCaption")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **srclang** | **kotlin.String**|  |
 **videoAddCaptionRequest** | [**VideoAddCaptionRequest**](VideoAddCaptionRequest.md)|  |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoCreateVideo"></a>
# **videoCreateVideo**
> VideoModel videoCreateVideo(libraryId, videoCreateVideoRequest)

Create Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoCreateVideoRequest : VideoCreateVideoRequest =  // VideoCreateVideoRequest | 
try {
    val result : VideoModel = apiInstance.videoCreateVideo(libraryId, videoCreateVideoRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoCreateVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoCreateVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoCreateVideoRequest** | [**VideoCreateVideoRequest**](VideoCreateVideoRequest.md)|  |

### Return type

[**VideoModel**](VideoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoDeleteCaption"></a>
# **videoDeleteCaption**
> StatusModel videoDeleteCaption(libraryId, videoId, srclang)

Delete Caption

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val srclang : kotlin.String = srclang_example // kotlin.String | 
try {
    val result : StatusModel = apiInstance.videoDeleteCaption(libraryId, videoId, srclang)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoDeleteCaption")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoDeleteCaption")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **srclang** | **kotlin.String**|  |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoDeleteResolutions"></a>
# **videoDeleteResolutions**
> StatusModel videoDeleteResolutions(libraryId, videoId, resolutionsToDelete, deleteNonConfiguredResolutions, allResolutions, deleteOriginal, outputs, deleteMp4Files, dryRun)

Cleanup unconfigured resolutions

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val resolutionsToDelete : kotlin.String = resolutionsToDelete_example // kotlin.String | 
val deleteNonConfiguredResolutions : kotlin.Boolean = true // kotlin.Boolean | 
val allResolutions : kotlin.Boolean = true // kotlin.Boolean | 
val deleteOriginal : kotlin.Boolean = true // kotlin.Boolean | 
val outputs : kotlin.String = outputs_example // kotlin.String | Outputs to clean. Supported values: hls, mp4, all
val deleteMp4Files : kotlin.Boolean = true // kotlin.Boolean | 
val dryRun : kotlin.Boolean = true // kotlin.Boolean | If set to true, no actual file manipulation will happen, only informational data will be returned
try {
    val result : StatusModel = apiInstance.videoDeleteResolutions(libraryId, videoId, resolutionsToDelete, deleteNonConfiguredResolutions, allResolutions, deleteOriginal, outputs, deleteMp4Files, dryRun)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoDeleteResolutions")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoDeleteResolutions")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **resolutionsToDelete** | **kotlin.String**|  | [optional]
 **deleteNonConfiguredResolutions** | **kotlin.Boolean**|  | [optional] [default to false]
 **allResolutions** | **kotlin.Boolean**|  | [optional] [default to false]
 **deleteOriginal** | **kotlin.Boolean**|  | [optional] [default to false]
 **outputs** | **kotlin.String**| Outputs to clean. Supported values: hls, mp4, all | [optional]
 **deleteMp4Files** | **kotlin.Boolean**|  | [optional] [default to false]
 **dryRun** | **kotlin.Boolean**| If set to true, no actual file manipulation will happen, only informational data will be returned | [optional] [default to false]

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoDeleteVideo"></a>
# **videoDeleteVideo**
> StatusModel videoDeleteVideo(libraryId, videoId)

Delete Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
try {
    val result : StatusModel = apiInstance.videoDeleteVideo(libraryId, videoId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoDeleteVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoDeleteVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoFetchNewVideo"></a>
# **videoFetchNewVideo**
> StatusModel videoFetchNewVideo(libraryId, videoFetchNewVideoRequest, collectionId, thumbnailTime)

Fetch Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoFetchNewVideoRequest : VideoFetchNewVideoRequest =  // VideoFetchNewVideoRequest | 
val collectionId : kotlin.String = collectionId_example // kotlin.String | 
val thumbnailTime : kotlin.Int = 56 // kotlin.Int | (Optional) Video time in ms to extract the main video thumbnail.
try {
    val result : StatusModel = apiInstance.videoFetchNewVideo(libraryId, videoFetchNewVideoRequest, collectionId, thumbnailTime)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoFetchNewVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoFetchNewVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoFetchNewVideoRequest** | [**VideoFetchNewVideoRequest**](VideoFetchNewVideoRequest.md)|  |
 **collectionId** | **kotlin.String**|  | [optional]
 **thumbnailTime** | **kotlin.Int**| (Optional) Video time in ms to extract the main video thumbnail. | [optional]

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoFetchVideo"></a>
# **videoFetchVideo**
> StatusModel videoFetchVideo(libraryId, videoId, videoFetchNewVideoRequest, collectionId, enabledResolutions, lowPriority, thumbnailTime)

Fetch Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val videoFetchNewVideoRequest : VideoFetchNewVideoRequest =  // VideoFetchNewVideoRequest | 
val collectionId : kotlin.String = collectionId_example // kotlin.String | 
val enabledResolutions : kotlin.String = enabledResolutions_example // kotlin.String | 
val lowPriority : kotlin.Boolean = true // kotlin.Boolean | 
val thumbnailTime : kotlin.Int = 56 // kotlin.Int | (Optional) Video time in ms to extract the main video thumbnail.
try {
    val result : StatusModel = apiInstance.videoFetchVideo(libraryId, videoId, videoFetchNewVideoRequest, collectionId, enabledResolutions, lowPriority, thumbnailTime)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoFetchVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoFetchVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **videoFetchNewVideoRequest** | [**VideoFetchNewVideoRequest**](VideoFetchNewVideoRequest.md)|  |
 **collectionId** | **kotlin.String**|  | [optional]
 **enabledResolutions** | **kotlin.String**|  | [optional] [default to &quot;&quot;]
 **lowPriority** | **kotlin.Boolean**|  | [optional] [default to false]
 **thumbnailTime** | **kotlin.Int**| (Optional) Video time in ms to extract the main video thumbnail. | [optional]

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoGetVideo"></a>
# **videoGetVideo**
> VideoModel videoGetVideo(libraryId, videoId)

Get Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
try {
    val result : VideoModel = apiInstance.videoGetVideo(libraryId, videoId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |

### Return type

[**VideoModel**](VideoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoGetVideoHeatmap"></a>
# **videoGetVideoHeatmap**
> VideoHeatmapModel videoGetVideoHeatmap(libraryId, videoId)

Get Video Heatmap

Returns the attention heatmap for a specific video, showing relative viewer interest across the timeline. May be unavailable if the feature is disabled or there isn&#39;t enough viewing data.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | The ID of the video library.
val videoId : kotlin.String = videoId_example // kotlin.String | The GUID of the video.
try {
    val result : VideoHeatmapModel = apiInstance.videoGetVideoHeatmap(libraryId, videoId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideoHeatmap")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideoHeatmap")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**| The ID of the video library. |
 **videoId** | **kotlin.String**| The GUID of the video. |

### Return type

[**VideoHeatmapModel**](VideoHeatmapModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoGetVideoHeatmapData"></a>
# **videoGetVideoHeatmapData**
> VideoPlayDataModel videoGetVideoHeatmapData(libraryId, videoId, token, expires)

Get Video heatmap data

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val token : kotlin.String = token_example // kotlin.String | 
val expires : kotlin.Long = 789 // kotlin.Long | 
try {
    val result : VideoPlayDataModel = apiInstance.videoGetVideoHeatmapData(libraryId, videoId, token, expires)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideoHeatmapData")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideoHeatmapData")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **token** | **kotlin.String**|  | [optional] [default to &quot;&quot;]
 **expires** | **kotlin.Long**|  | [optional] [default to 0L]

### Return type

[**VideoPlayDataModel**](VideoPlayDataModel.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoGetVideoPlayData"></a>
# **videoGetVideoPlayData**
> VideoPlayDataModel videoGetVideoPlayData(libraryId, videoId, token, expires)

Get Video play data

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val token : kotlin.String = token_example // kotlin.String | 
val expires : kotlin.Long = 789 // kotlin.Long | 
try {
    val result : VideoPlayDataModel = apiInstance.videoGetVideoPlayData(libraryId, videoId, token, expires)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideoPlayData")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideoPlayData")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **token** | **kotlin.String**|  | [optional] [default to &quot;&quot;]
 **expires** | **kotlin.Long**|  | [optional] [default to 0L]

### Return type

[**VideoPlayDataModel**](VideoPlayDataModel.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoGetVideoResolutions"></a>
# **videoGetVideoResolutions**
> StatusModelOfVideoResolutionsInfoModel videoGetVideoResolutions(libraryId, videoId)

Video resolutions info

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
try {
    val result : StatusModelOfVideoResolutionsInfoModel = apiInstance.videoGetVideoResolutions(libraryId, videoId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideoResolutions")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideoResolutions")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |

### Return type

[**StatusModelOfVideoResolutionsInfoModel**](StatusModelOfVideoResolutionsInfoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoGetVideoStatistics"></a>
# **videoGetVideoStatistics**
> VideoStatisticsModel videoGetVideoStatistics(libraryId, dateFrom, dateTo, hourly, videoGuid)

Get Video Statistics

Returns time-series views and watch time, plus country-level aggregates, at the library level or for a specific video. Control the time window with dateFrom/dateTo and the granularity with hourly. Basic safeguards prevent spam and bot inflation by de-duplicating sessions and ignoring obviously invalid events.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val dateFrom : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Optional start of the time range (UTC). If omitted or invalid, the last 30 days are returned.
val dateTo : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Optional end of the time range (UTC). If omitted with a valid start, defaults to now; otherwise the last 30 days are returned.
val hourly : kotlin.Boolean = true // kotlin.Boolean | Optional. If true, returns hourly data; otherwise daily (UTC). Default is daily.
val videoGuid : kotlin.String = videoGuid_example // kotlin.String | Optional video GUID to filter results. When omitted, returns library-level aggregates.
try {
    val result : VideoStatisticsModel = apiInstance.videoGetVideoStatistics(libraryId, dateFrom, dateTo, hourly, videoGuid)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideoStatistics")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideoStatistics")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **dateFrom** | **kotlin.String**| Optional start of the time range (UTC). If omitted or invalid, the last 30 days are returned. | [optional]
 **dateTo** | **kotlin.String**| Optional end of the time range (UTC). If omitted with a valid start, defaults to now; otherwise the last 30 days are returned. | [optional]
 **hourly** | **kotlin.Boolean**| Optional. If true, returns hourly data; otherwise daily (UTC). Default is daily. | [optional] [default to false]
 **videoGuid** | **kotlin.String**| Optional video GUID to filter results. When omitted, returns library-level aggregates. | [optional]

### Return type

[**VideoStatisticsModel**](VideoStatisticsModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoGetVideoStorageSize"></a>
# **videoGetVideoStorageSize**
> StatusModelOfVideoStorageSizeModel videoGetVideoStorageSize(libraryId, videoId)

Get video storage size info

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
try {
    val result : StatusModelOfVideoStorageSizeModel = apiInstance.videoGetVideoStorageSize(libraryId, videoId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoGetVideoStorageSize")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoGetVideoStorageSize")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |

### Return type

[**StatusModelOfVideoStorageSizeModel**](StatusModelOfVideoStorageSizeModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoList"></a>
# **videoList**
> PaginationListOfVideoModel videoList(libraryId, page, itemsPerPage, search, collection, orderBy)

List Videos

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val page : kotlin.Int = 56 // kotlin.Int | 
val itemsPerPage : kotlin.Int = 56 // kotlin.Int | 
val search : kotlin.String = search_example // kotlin.String | 
val collection : kotlin.String = collection_example // kotlin.String | 
val orderBy : kotlin.String = orderBy_example // kotlin.String | 
try {
    val result : PaginationListOfVideoModel = apiInstance.videoList(libraryId, page, itemsPerPage, search, collection, orderBy)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoList")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoList")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **page** | **kotlin.Int**|  | [optional] [default to 1]
 **itemsPerPage** | **kotlin.Int**|  | [optional] [default to 100]
 **search** | **kotlin.String**|  | [optional] [default to &quot;&quot;]
 **collection** | **kotlin.String**|  | [optional] [default to &quot;&quot;]
 **orderBy** | **kotlin.String**|  | [optional] [default to &quot;date&quot;]

### Return type

[**PaginationListOfVideoModel**](PaginationListOfVideoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoReencodeUsingCodec"></a>
# **videoReencodeUsingCodec**
> VideoModel videoReencodeUsingCodec(libraryId, videoId, outputCodecId)

Add output codec to video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val outputCodecId : EncoderOutputCodec =  // EncoderOutputCodec | 
try {
    val result : VideoModel = apiInstance.videoReencodeUsingCodec(libraryId, videoId, outputCodecId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoReencodeUsingCodec")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoReencodeUsingCodec")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **outputCodecId** | [**EncoderOutputCodec**](.md)|  | [enum: 0, 1, 2, 3]

### Return type

[**VideoModel**](VideoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoReencodeVideo"></a>
# **videoReencodeVideo**
> VideoModel videoReencodeVideo(libraryId, videoId)

Reencode Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
try {
    val result : VideoModel = apiInstance.videoReencodeVideo(libraryId, videoId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoReencodeVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoReencodeVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |

### Return type

[**VideoModel**](VideoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoRepackage"></a>
# **videoRepackage**
> VideoModel videoRepackage(libraryId, videoId, keepOriginalFiles)

Repackage Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val keepOriginalFiles : kotlin.Boolean = true // kotlin.Boolean | Marks whether previous file versions should be kept in storage, allows for faster repackage later on. Default is true.
try {
    val result : VideoModel = apiInstance.videoRepackage(libraryId, videoId, keepOriginalFiles)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoRepackage")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoRepackage")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **keepOriginalFiles** | **kotlin.Boolean**| Marks whether previous file versions should be kept in storage, allows for faster repackage later on. Default is true. | [optional] [default to true]

### Return type

[**VideoModel**](VideoModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="videoSetThumbnail"></a>
# **videoSetThumbnail**
> StatusModel videoSetThumbnail(libraryId, videoId, thumbnailUrl, body)

Set Thumbnail

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val thumbnailUrl : kotlin.String = thumbnailUrl_example // kotlin.String | 
val body : java.io.File = BINARY_DATA_HERE // java.io.File | Optional thumbnail file to upload
try {
    val result : StatusModel = apiInstance.videoSetThumbnail(libraryId, videoId, thumbnailUrl, body)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoSetThumbnail")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoSetThumbnail")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **thumbnailUrl** | **kotlin.String**|  | [optional]
 **body** | **java.io.File**| Optional thumbnail file to upload | [optional]

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/octet-stream
 - **Accept**: application/json

<a id="videoSmartGenerate"></a>
# **videoSmartGenerate**
> StatusModel videoSmartGenerate(libraryId, videoId, videoSmartGenerateRequest)

Trigger Smart actions

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val videoSmartGenerateRequest : VideoSmartGenerateRequest =  // VideoSmartGenerateRequest | 
try {
    val result : StatusModel = apiInstance.videoSmartGenerate(libraryId, videoId, videoSmartGenerateRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoSmartGenerate")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoSmartGenerate")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **videoSmartGenerateRequest** | [**VideoSmartGenerateRequest**](VideoSmartGenerateRequest.md)|  |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoTranscribeVideo"></a>
# **videoTranscribeVideo**
> StatusModel videoTranscribeVideo(libraryId, videoId, force, videoTranscribeVideoRequest)

Transcribe video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val force : kotlin.Boolean = true // kotlin.Boolean | 
val videoTranscribeVideoRequest : VideoTranscribeVideoRequest =  // VideoTranscribeVideoRequest | Used to override video library transcription settings, null by default
try {
    val result : StatusModel = apiInstance.videoTranscribeVideo(libraryId, videoId, force, videoTranscribeVideoRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoTranscribeVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoTranscribeVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **force** | **kotlin.Boolean**|  | [optional] [default to false]
 **videoTranscribeVideoRequest** | [**VideoTranscribeVideoRequest**](VideoTranscribeVideoRequest.md)| Used to override video library transcription settings, null by default | [optional]

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoUpdateVideo"></a>
# **videoUpdateVideo**
> StatusModel videoUpdateVideo(libraryId, videoId, videoUpdateVideoRequest)

Update Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val videoUpdateVideoRequest : VideoUpdateVideoRequest =  // VideoUpdateVideoRequest | 
try {
    val result : StatusModel = apiInstance.videoUpdateVideo(libraryId, videoId, videoUpdateVideoRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoUpdateVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoUpdateVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **videoUpdateVideoRequest** | [**VideoUpdateVideoRequest**](VideoUpdateVideoRequest.md)|  |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="videoUploadVideo"></a>
# **videoUploadVideo**
> StatusModel videoUploadVideo(libraryId, videoId, body, jitEnabled, enabledResolutions, enabledOutputCodecs, transcribeEnabled, transcribeLanguages, sourceLanguage, generateTitle, generateDescription, generateChapters, generateMoments)

Upload Video

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageVideosApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val videoId : kotlin.String = videoId_example // kotlin.String | 
val body : java.io.File = BINARY_DATA_HERE // java.io.File | Video file to upload
val jitEnabled : kotlin.Boolean = true // kotlin.Boolean | Marks whether JIT encoding should be enabled for this video (works only when Premium Encoding is enabled), overrides library settings
val enabledResolutions : kotlin.String = enabledResolutions_example // kotlin.String | Comma separated list of resolutions enabled for encoding, available options: 240p, 360p, 480p, 720p, 1080p, 1440p, 2160p
val enabledOutputCodecs : kotlin.String = enabledOutputCodecs_example // kotlin.String | List of codecs that will be used to encode the file (overrides library settings). Available values: x264, vp9
val transcribeEnabled : kotlin.Boolean = true // kotlin.Boolean | Setting this to true will enable transcription on this video. Enabling this will incur transcription charges
val transcribeLanguages : kotlin.String = transcribeLanguages_example // kotlin.String | Comma separated list of languages that will be used as target languages, use ISO 639-1 language codes.
val sourceLanguage : kotlin.String = sourceLanguage_example // kotlin.String | Language spoken in the video, use ISO 639-1 language codes.
val generateTitle : kotlin.Boolean = true // kotlin.Boolean | Whether video title should be generated from transcription.
val generateDescription : kotlin.Boolean = true // kotlin.Boolean | Whether video description should be generated from transcription.
val generateChapters : kotlin.Boolean = true // kotlin.Boolean | Whether video chapters should be generated from transcription.
val generateMoments : kotlin.Boolean = true // kotlin.Boolean | Whether video moments should be generated from transcription.
try {
    val result : StatusModel = apiInstance.videoUploadVideo(libraryId, videoId, body, jitEnabled, enabledResolutions, enabledOutputCodecs, transcribeEnabled, transcribeLanguages, sourceLanguage, generateTitle, generateDescription, generateChapters, generateMoments)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageVideosApi#videoUploadVideo")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageVideosApi#videoUploadVideo")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **libraryId** | **kotlin.Long**|  |
 **videoId** | **kotlin.String**|  |
 **body** | **java.io.File**| Video file to upload |
 **jitEnabled** | **kotlin.Boolean**| Marks whether JIT encoding should be enabled for this video (works only when Premium Encoding is enabled), overrides library settings | [optional]
 **enabledResolutions** | **kotlin.String**| Comma separated list of resolutions enabled for encoding, available options: 240p, 360p, 480p, 720p, 1080p, 1440p, 2160p | [optional] [default to &quot;&quot;]
 **enabledOutputCodecs** | **kotlin.String**| List of codecs that will be used to encode the file (overrides library settings). Available values: x264, vp9 | [optional] [default to &quot;&quot;]
 **transcribeEnabled** | **kotlin.Boolean**| Setting this to true will enable transcription on this video. Enabling this will incur transcription charges | [optional]
 **transcribeLanguages** | **kotlin.String**| Comma separated list of languages that will be used as target languages, use ISO 639-1 language codes. | [optional]
 **sourceLanguage** | **kotlin.String**| Language spoken in the video, use ISO 639-1 language codes. | [optional]
 **generateTitle** | **kotlin.Boolean**| Whether video title should be generated from transcription. | [optional]
 **generateDescription** | **kotlin.Boolean**| Whether video description should be generated from transcription. | [optional]
 **generateChapters** | **kotlin.Boolean**| Whether video chapters should be generated from transcription. | [optional]
 **generateMoments** | **kotlin.Boolean**| Whether video moments should be generated from transcription. | [optional]

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/octet-stream
 - **Accept**: application/json

