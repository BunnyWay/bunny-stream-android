# ManageLiveStreamsApi

All URIs are relative to *https://video.bunnycdn.com*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**liveStreamCreate**](ManageLiveStreamsApi.md#liveStreamCreate) | **POST** /library/{libraryId}/live | Create new live stream |
| [**liveStreamDelete**](ManageLiveStreamsApi.md#liveStreamDelete) | **DELETE** /library/{libraryId}/live/{streamId} | Delete live stream |
| [**liveStreamDeleteThumbnail**](ManageLiveStreamsApi.md#liveStreamDeleteThumbnail) | **DELETE** /library/{libraryId}/live/{streamId}/thumbnail | Delete Thumbnail |
| [**liveStreamGetBitrateHistory**](ManageLiveStreamsApi.md#liveStreamGetBitrateHistory) | **GET** /library/{libraryId}/live/{streamId}/bitrate-history |  |
| [**liveStreamGetByStreamId**](ManageLiveStreamsApi.md#liveStreamGetByStreamId) | **GET** /library/{libraryId}/live/{streamId} | Get live stream by ID |
| [**liveStreamGetLatestBitrate**](ManageLiveStreamsApi.md#liveStreamGetLatestBitrate) | **GET** /library/{libraryId}/live/{streamId}/current-bitrate |  |
| [**liveStreamGetStreamPlayData**](ManageLiveStreamsApi.md#liveStreamGetStreamPlayData) | **GET** /library/{libraryId}/live/{streamId}/play | Get live stream play data |
| [**liveStreamGetStreamStatus**](ManageLiveStreamsApi.md#liveStreamGetStreamStatus) | **GET** /library/{libraryId}/live/{streamId}/status | Get live stream status |
| [**liveStreamGetThumbnails**](ManageLiveStreamsApi.md#liveStreamGetThumbnails) | **GET** /library/{libraryId}/live/{streamId}/thumbnails | Get live stream thumbnails |
| [**liveStreamList**](ManageLiveStreamsApi.md#liveStreamList) | **GET** /library/{libraryId}/live | List streams |
| [**liveStreamRegenerateStreamKey**](ManageLiveStreamsApi.md#liveStreamRegenerateStreamKey) | **PUT** /library/{libraryId}/live/{streamId}/regenerate-key | Regenerate stream key |
| [**liveStreamSetThumbnail**](ManageLiveStreamsApi.md#liveStreamSetThumbnail) | **POST** /library/{libraryId}/live/{streamId}/thumbnail | Set Thumbnail |
| [**liveStreamStartStream**](ManageLiveStreamsApi.md#liveStreamStartStream) | **PUT** /library/{libraryId}/live/{streamId}/start | Start live stream |
| [**liveStreamStopStream**](ManageLiveStreamsApi.md#liveStreamStopStream) | **PUT** /library/{libraryId}/live/{streamId}/stop | Stop live stream |
| [**liveStreamUpdate**](ManageLiveStreamsApi.md#liveStreamUpdate) | **PUT** /library/{libraryId}/live/{streamId} | Update live stream |


<a id="liveStreamCreate"></a>
# **liveStreamCreate**
> LiveStreamModel liveStreamCreate(libraryId, createLiveStreamModel)

Create new live stream

Creates a new live stream object with the specified parameters and returns it

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val createLiveStreamModel : CreateLiveStreamModel =  // CreateLiveStreamModel | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamCreate(libraryId, createLiveStreamModel)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamCreate")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamCreate")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **createLiveStreamModel** | [**CreateLiveStreamModel**](CreateLiveStreamModel.md)|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="liveStreamDelete"></a>
# **liveStreamDelete**
> LiveStreamModel liveStreamDelete(libraryId, streamId)

Delete live stream

Deletes the live stream object and all resources associated with it. This operation cannot be undone.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamDelete(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamDelete")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamDelete")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamDeleteThumbnail"></a>
# **liveStreamDeleteThumbnail**
> liveStreamDeleteThumbnail(libraryId, streamId, restoreLibraryDefault)

Delete Thumbnail

Removes the custom thumbnail from the live stream. When restoreLibraryDefault is true and the library has a default live thumbnail set, the stream will use that instead of having no thumbnail.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
val restoreLibraryDefault : kotlin.Boolean = true // kotlin.Boolean | When true and the library has a default live thumbnail, the stream will be set to use that thumbnail after removal. When false or no library default exists, the stream will have no thumbnail.
try {
    apiInstance.liveStreamDeleteThumbnail(libraryId, streamId, restoreLibraryDefault)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamDeleteThumbnail")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamDeleteThumbnail")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **streamId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **restoreLibraryDefault** | **kotlin.Boolean**| When true and the library has a default live thumbnail, the stream will be set to use that thumbnail after removal. When false or no library default exists, the stream will have no thumbnail. | [optional] [default to false] |

### Return type

null (empty response body)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamGetBitrateHistory"></a>
# **liveStreamGetBitrateHistory**
> java.io.File liveStreamGetBitrateHistory(libraryId, streamId, startTime, endTime)



### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
val startTime : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | 
val endTime : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | 
try {
    val result : java.io.File = apiInstance.liveStreamGetBitrateHistory(libraryId, streamId, startTime, endTime)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamGetBitrateHistory")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamGetBitrateHistory")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **streamId** | **kotlin.String**|  | |
| **startTime** | **kotlin.String**|  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **endTime** | **kotlin.String**|  | [optional] |

### Return type

[**java.io.File**](java.io.File.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/octet-stream

<a id="liveStreamGetByStreamId"></a>
# **liveStreamGetByStreamId**
> LiveStreamModel liveStreamGetByStreamId(libraryId, streamId)

Get live stream by ID

Gets details of a live stream contained in library by its ID.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamGetByStreamId(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamGetByStreamId")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamGetByStreamId")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamGetLatestBitrate"></a>
# **liveStreamGetLatestBitrate**
> java.io.File liveStreamGetLatestBitrate(libraryId, streamId)



### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : java.io.File = apiInstance.liveStreamGetLatestBitrate(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamGetLatestBitrate")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamGetLatestBitrate")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**java.io.File**](java.io.File.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/octet-stream

<a id="liveStreamGetStreamPlayData"></a>
# **liveStreamGetStreamPlayData**
> LiveStreamPlayDataModel liveStreamGetStreamPlayData(libraryId, streamId, token, expires)

Get live stream play data

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
val token : kotlin.String = token_example // kotlin.String | 
val expires : kotlin.Long = 789 // kotlin.Long | 
try {
    val result : LiveStreamPlayDataModel = apiInstance.liveStreamGetStreamPlayData(libraryId, streamId, token, expires)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamGetStreamPlayData")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamGetStreamPlayData")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **streamId** | **kotlin.String**|  | |
| **token** | **kotlin.String**|  | [optional] [default to &quot;&quot;] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **expires** | **kotlin.Long**|  | [optional] [default to 0L] |

### Return type

[**LiveStreamPlayDataModel**](LiveStreamPlayDataModel.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamGetStreamStatus"></a>
# **liveStreamGetStreamStatus**
> LiveStreamStatusModel liveStreamGetStreamStatus(libraryId, streamId)

Get live stream status

Returns the current status of the specified live stream.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : LiveStreamStatusModel = apiInstance.liveStreamGetStreamStatus(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamGetStreamStatus")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamGetStreamStatus")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**LiveStreamStatusModel**](LiveStreamStatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamGetThumbnails"></a>
# **liveStreamGetThumbnails**
> kotlin.collections.List&lt;ThumbnailListResponseModel&gt; liveStreamGetThumbnails(libraryId, streamId, limit, from, to)

Get live stream thumbnails

Returns recent generated thumbnail object paths for the specified live stream. You can limit the number of results and optionally constrain by a time range using &#39;from&#39; and &#39;to&#39; in UTC.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
val limit : kotlin.Int = 56 // kotlin.Int | The maximum number of thumbnails to return. Default: 5
val from : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Filter thumbnails created at or after this UTC timestamp
val to : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Filter thumbnails created at or before this UTC timestamp
try {
    val result : kotlin.collections.List<ThumbnailListResponseModel> = apiInstance.liveStreamGetThumbnails(libraryId, streamId, limit, from, to)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamGetThumbnails")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamGetThumbnails")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **streamId** | **kotlin.String**|  | |
| **limit** | **kotlin.Int**| The maximum number of thumbnails to return. Default: 5 | [optional] [default to 5] |
| **from** | **kotlin.String**| Filter thumbnails created at or after this UTC timestamp | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **to** | **kotlin.String**| Filter thumbnails created at or before this UTC timestamp | [optional] |

### Return type

[**kotlin.collections.List&lt;ThumbnailListResponseModel&gt;**](ThumbnailListResponseModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamList"></a>
# **liveStreamList**
> PaginationListOfLiveStreamModel liveStreamList(libraryId, page, itemsPerPage, search, orderBy, collectionId)

List streams

Returns a list of streams associated with this library and collection, if passed

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val page : kotlin.Int = 56 // kotlin.Int | 
val itemsPerPage : kotlin.Int = 56 // kotlin.Int | 
val search : kotlin.String = search_example // kotlin.String | 
val orderBy : kotlin.String = orderBy_example // kotlin.String | The field to order by. Possible values: date, title, status, started, ended, planned
val collectionId : kotlin.String = collectionId_example // kotlin.String | 
try {
    val result : PaginationListOfLiveStreamModel = apiInstance.liveStreamList(libraryId, page, itemsPerPage, search, orderBy, collectionId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamList")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamList")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **page** | **kotlin.Int**|  | [optional] [default to 1] |
| **itemsPerPage** | **kotlin.Int**|  | [optional] [default to 100] |
| **search** | **kotlin.String**|  | [optional] [default to &quot;&quot;] |
| **orderBy** | **kotlin.String**| The field to order by. Possible values: date, title, status, started, ended, planned | [optional] [default to &quot;date&quot;] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **collectionId** | **kotlin.String**|  | [optional] |

### Return type

[**PaginationListOfLiveStreamModel**](PaginationListOfLiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamRegenerateStreamKey"></a>
# **liveStreamRegenerateStreamKey**
> LiveStreamModel liveStreamRegenerateStreamKey(libraryId, streamId)

Regenerate stream key

Regenerates the stream key for the live stream. This operation is not allowed for streams that ended.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamRegenerateStreamKey(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamRegenerateStreamKey")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamRegenerateStreamKey")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamSetThumbnail"></a>
# **liveStreamSetThumbnail**
> StatusModel liveStreamSetThumbnail(libraryId, streamId, thumbnailUrl, body)

Set Thumbnail

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
val thumbnailUrl : kotlin.String = thumbnailUrl_example // kotlin.String | 
val body : java.io.File = BINARY_DATA_HERE // java.io.File | Optional thumbnail file to upload
try {
    val result : StatusModel = apiInstance.liveStreamSetThumbnail(libraryId, streamId, thumbnailUrl, body)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamSetThumbnail")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamSetThumbnail")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **streamId** | **kotlin.String**|  | |
| **thumbnailUrl** | **kotlin.String**|  | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **body** | **java.io.File**| Optional thumbnail file to upload | [optional] |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/octet-stream
 - **Accept**: application/json

<a id="liveStreamStartStream"></a>
# **liveStreamStartStream**
> LiveStreamModel liveStreamStartStream(libraryId, streamId)

Start live stream

Marks the stream as started, allowing viewers to view the stream.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamStartStream(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamStartStream")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamStartStream")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamStopStream"></a>
# **liveStreamStopStream**
> LiveStreamModel liveStreamStopStream(libraryId, streamId)

Stop live stream

Stops the stream, if publishing is still ongoing, it will be stopped by the ingest server. If &#39;RecordVod&#39; was set to true, the stream will be converted to a regular video as a VOD.This operation cannot be undone.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamStopStream(libraryId, streamId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamStopStream")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamStopStream")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **streamId** | **kotlin.String**|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStreamUpdate"></a>
# **liveStreamUpdate**
> LiveStreamModel liveStreamUpdate(libraryId, streamId, updateLiveStreamModel)

Update live stream

Updates the live stream object with the specified parameters and returns it

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageLiveStreamsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val streamId : kotlin.String = streamId_example // kotlin.String | 
val updateLiveStreamModel : UpdateLiveStreamModel =  // UpdateLiveStreamModel | 
try {
    val result : LiveStreamModel = apiInstance.liveStreamUpdate(libraryId, streamId, updateLiveStreamModel)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageLiveStreamsApi#liveStreamUpdate")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageLiveStreamsApi#liveStreamUpdate")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **streamId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **updateLiveStreamModel** | [**UpdateLiveStreamModel**](UpdateLiveStreamModel.md)|  | |

### Return type

[**LiveStreamModel**](LiveStreamModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

