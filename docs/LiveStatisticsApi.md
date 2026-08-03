# LiveStatisticsApi

All URIs are relative to *https://video.bunnycdn.com*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**liveStatisticsGetEncodingStatistics**](LiveStatisticsApi.md#liveStatisticsGetEncodingStatistics) | **GET** /library/{libraryId}/live/statistics/encoding | Get Live Encoding Statistics |
| [**liveStatisticsGetEncodingStatistics2**](LiveStatisticsApi.md#liveStatisticsGetEncodingStatistics2) | **GET** /videolibrary/{libraryId}/live/statistics/encoding | Get Live Encoding Statistics |


<a id="liveStatisticsGetEncodingStatistics"></a>
# **liveStatisticsGetEncodingStatistics**
> LiveEncodingStatisticsModel liveStatisticsGetEncodingStatistics(libraryId, dateFrom, dateTo, hourly, liveStreamGuids, videoCodecs, resolutions)

Get Live Encoding Statistics

Returns live encoding seconds totals, a time-series chart (hourly or daily UTC), and breakdowns by live stream, codec, and resolution. Use hourly&#x3D;true for hour buckets when the range falls within the last 30 days; older ranges automatically use daily buckets.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = LiveStatisticsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val dateFrom : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Optional start of the time range (UTC). If omitted, the last 14 days are returned.
val dateTo : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Optional end of the time range (UTC). If omitted, the current date is used.
val hourly : kotlin.Boolean = true // kotlin.Boolean | Optional. If true, returns hourly data when the range is within the last 30 days; otherwise daily (UTC). Default is daily.
val liveStreamGuids : kotlin.collections.List<kotlin.String> =  // kotlin.collections.List<kotlin.String> | Optional live stream IDs to include. Repeat the query parameter for multiple values (max 50).
val videoCodecs : kotlin.collections.List<kotlin.String> =  // kotlin.collections.List<kotlin.String> | Optional video codecs to include: H264, VP9, HEVC, AV1. Repeat for multiple values.
val resolutions : kotlin.collections.List<kotlin.Int> =  // kotlin.collections.List<kotlin.Int> | Optional output heights to include: 240, 360, 480, 720, 1080, 1440, 2160. Repeat for multiple values.
try {
    val result : LiveEncodingStatisticsModel = apiInstance.liveStatisticsGetEncodingStatistics(libraryId, dateFrom, dateTo, hourly, liveStreamGuids, videoCodecs, resolutions)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LiveStatisticsApi#liveStatisticsGetEncodingStatistics")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LiveStatisticsApi#liveStatisticsGetEncodingStatistics")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **dateFrom** | **kotlin.String**| Optional start of the time range (UTC). If omitted, the last 14 days are returned. | [optional] |
| **dateTo** | **kotlin.String**| Optional end of the time range (UTC). If omitted, the current date is used. | [optional] |
| **hourly** | **kotlin.Boolean**| Optional. If true, returns hourly data when the range is within the last 30 days; otherwise daily (UTC). Default is daily. | [optional] [default to false] |
| **liveStreamGuids** | [**kotlin.collections.List&lt;kotlin.String&gt;**](kotlin.String.md)| Optional live stream IDs to include. Repeat the query parameter for multiple values (max 50). | [optional] |
| **videoCodecs** | [**kotlin.collections.List&lt;kotlin.String&gt;**](kotlin.String.md)| Optional video codecs to include: H264, VP9, HEVC, AV1. Repeat for multiple values. | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **resolutions** | [**kotlin.collections.List&lt;kotlin.Int&gt;**](kotlin.Int.md)| Optional output heights to include: 240, 360, 480, 720, 1080, 1440, 2160. Repeat for multiple values. | [optional] |

### Return type

[**LiveEncodingStatisticsModel**](LiveEncodingStatisticsModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="liveStatisticsGetEncodingStatistics2"></a>
# **liveStatisticsGetEncodingStatistics2**
> LiveEncodingStatisticsModel liveStatisticsGetEncodingStatistics2(libraryId, dateFrom, dateTo, hourly, liveStreamGuids, videoCodecs, resolutions)

Get Live Encoding Statistics

Returns live encoding seconds totals, a time-series chart (hourly or daily UTC), and breakdowns by live stream, codec, and resolution. Use hourly&#x3D;true for hour buckets when the range falls within the last 30 days; older ranges automatically use daily buckets.

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = LiveStatisticsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val dateFrom : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Optional start of the time range (UTC). If omitted, the last 14 days are returned.
val dateTo : kotlin.String = 2013-10-20T19:20:30+01:00 // kotlin.String | Optional end of the time range (UTC). If omitted, the current date is used.
val hourly : kotlin.Boolean = true // kotlin.Boolean | Optional. If true, returns hourly data when the range is within the last 30 days; otherwise daily (UTC). Default is daily.
val liveStreamGuids : kotlin.collections.List<kotlin.String> =  // kotlin.collections.List<kotlin.String> | Optional live stream IDs to include. Repeat the query parameter for multiple values (max 50).
val videoCodecs : kotlin.collections.List<kotlin.String> =  // kotlin.collections.List<kotlin.String> | Optional video codecs to include: H264, VP9, HEVC, AV1. Repeat for multiple values.
val resolutions : kotlin.collections.List<kotlin.Int> =  // kotlin.collections.List<kotlin.Int> | Optional output heights to include: 240, 360, 480, 720, 1080, 1440, 2160. Repeat for multiple values.
try {
    val result : LiveEncodingStatisticsModel = apiInstance.liveStatisticsGetEncodingStatistics2(libraryId, dateFrom, dateTo, hourly, liveStreamGuids, videoCodecs, resolutions)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LiveStatisticsApi#liveStatisticsGetEncodingStatistics2")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LiveStatisticsApi#liveStatisticsGetEncodingStatistics2")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **dateFrom** | **kotlin.String**| Optional start of the time range (UTC). If omitted, the last 14 days are returned. | [optional] |
| **dateTo** | **kotlin.String**| Optional end of the time range (UTC). If omitted, the current date is used. | [optional] |
| **hourly** | **kotlin.Boolean**| Optional. If true, returns hourly data when the range is within the last 30 days; otherwise daily (UTC). Default is daily. | [optional] [default to false] |
| **liveStreamGuids** | [**kotlin.collections.List&lt;kotlin.String&gt;**](kotlin.String.md)| Optional live stream IDs to include. Repeat the query parameter for multiple values (max 50). | [optional] |
| **videoCodecs** | [**kotlin.collections.List&lt;kotlin.String&gt;**](kotlin.String.md)| Optional video codecs to include: H264, VP9, HEVC, AV1. Repeat for multiple values. | [optional] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **resolutions** | [**kotlin.collections.List&lt;kotlin.Int&gt;**](kotlin.Int.md)| Optional output heights to include: 240, 360, 480, 720, 1080, 1440, 2160. Repeat for multiple values. | [optional] |

### Return type

[**LiveEncodingStatisticsModel**](LiveEncodingStatisticsModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

