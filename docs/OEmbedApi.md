# OEmbedApi

All URIs are relative to *https://video.bunnycdn.com*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**oEmbedGetOEmbed**](OEmbedApi.md#oEmbedGetOEmbed) | **GET** /OEmbed |  |


<a id="oEmbedGetOEmbed"></a>
# **oEmbedGetOEmbed**
> VideoOEmbedModel oEmbedGetOEmbed(url, maxWidth, maxHeight, token, expires)



### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = OEmbedApi()
val url : kotlin.String = url_example // kotlin.String | 
val maxWidth : kotlin.Int = 56 // kotlin.Int | 
val maxHeight : kotlin.Int = 56 // kotlin.Int | 
val token : kotlin.String = token_example // kotlin.String | 
val expires : kotlin.Long = 789 // kotlin.Long | 
try {
    val result : VideoOEmbedModel = apiInstance.oEmbedGetOEmbed(url, maxWidth, maxHeight, token, expires)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling OEmbedApi#oEmbedGetOEmbed")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling OEmbedApi#oEmbedGetOEmbed")
    e.printStackTrace()
}
```

### Parameters
| **url** | **kotlin.String**|  | [optional] |
| **maxWidth** | **kotlin.Int**|  | [optional] |
| **maxHeight** | **kotlin.Int**|  | [optional] |
| **token** | **kotlin.String**|  | [optional] [default to &quot;&quot;] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **expires** | **kotlin.Long**|  | [optional] [default to 0L] |

### Return type

[**VideoOEmbedModel**](VideoOEmbedModel.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

