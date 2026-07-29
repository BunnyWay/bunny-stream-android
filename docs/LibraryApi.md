# LibraryApi

All URIs are relative to *https://video.bunnycdn.com*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**libraryStatus**](LibraryApi.md#libraryStatus) | **GET** /library/{libraryId} |  |


<a id="libraryStatus"></a>
# **libraryStatus**
> VideoLibraryStatus libraryStatus(libraryId)



### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = LibraryApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
try {
    val result : VideoLibraryStatus = apiInstance.libraryStatus(libraryId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling LibraryApi#libraryStatus")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling LibraryApi#libraryStatus")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **libraryId** | **kotlin.Long**|  | |

### Return type

[**VideoLibraryStatus**](VideoLibraryStatus.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

