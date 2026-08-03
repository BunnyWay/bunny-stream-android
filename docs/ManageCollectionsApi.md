# ManageCollectionsApi

All URIs are relative to *https://video.bunnycdn.com*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**collectionCreateCollection**](ManageCollectionsApi.md#collectionCreateCollection) | **POST** /library/{libraryId}/collections | Create Collection |
| [**collectionDeleteCollection**](ManageCollectionsApi.md#collectionDeleteCollection) | **DELETE** /library/{libraryId}/collections/{collectionId} | Delete Collection |
| [**collectionGetCollection**](ManageCollectionsApi.md#collectionGetCollection) | **GET** /library/{libraryId}/collections/{collectionId} | Get Collection |
| [**collectionList**](ManageCollectionsApi.md#collectionList) | **GET** /library/{libraryId}/collections | Get Collection List |
| [**collectionUpdateCollection**](ManageCollectionsApi.md#collectionUpdateCollection) | **POST** /library/{libraryId}/collections/{collectionId} | Update Collection |


<a id="collectionCreateCollection"></a>
# **collectionCreateCollection**
> CollectionModel collectionCreateCollection(libraryId, updateCollectionModel)

Create Collection

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageCollectionsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val updateCollectionModel : UpdateCollectionModel =  // UpdateCollectionModel | 
try {
    val result : CollectionModel = apiInstance.collectionCreateCollection(libraryId, updateCollectionModel)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageCollectionsApi#collectionCreateCollection")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageCollectionsApi#collectionCreateCollection")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **updateCollectionModel** | [**UpdateCollectionModel**](UpdateCollectionModel.md)|  | |

### Return type

[**CollectionModel**](CollectionModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="collectionDeleteCollection"></a>
# **collectionDeleteCollection**
> StatusModel collectionDeleteCollection(libraryId, collectionId)

Delete Collection

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageCollectionsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val collectionId : kotlin.String = collectionId_example // kotlin.String | 
try {
    val result : StatusModel = apiInstance.collectionDeleteCollection(libraryId, collectionId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageCollectionsApi#collectionDeleteCollection")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageCollectionsApi#collectionDeleteCollection")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **collectionId** | **kotlin.String**|  | |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="collectionGetCollection"></a>
# **collectionGetCollection**
> CollectionModel collectionGetCollection(libraryId, collectionId, includeThumbnails)

Get Collection

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageCollectionsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val collectionId : kotlin.String = collectionId_example // kotlin.String | 
val includeThumbnails : kotlin.Boolean = true // kotlin.Boolean | 
try {
    val result : CollectionModel = apiInstance.collectionGetCollection(libraryId, collectionId, includeThumbnails)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageCollectionsApi#collectionGetCollection")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageCollectionsApi#collectionGetCollection")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **collectionId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **includeThumbnails** | **kotlin.Boolean**|  | [optional] [default to false] |

### Return type

[**CollectionModel**](CollectionModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="collectionList"></a>
# **collectionList**
> PaginationListOfCollectionModel collectionList(libraryId, page, itemsPerPage, search, orderBy, includeThumbnails)

Get Collection List

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageCollectionsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val page : kotlin.Int = 56 // kotlin.Int | 
val itemsPerPage : kotlin.Int = 56 // kotlin.Int | 
val search : kotlin.String = search_example // kotlin.String | 
val orderBy : kotlin.String = orderBy_example // kotlin.String | 
val includeThumbnails : kotlin.Boolean = true // kotlin.Boolean | 
try {
    val result : PaginationListOfCollectionModel = apiInstance.collectionList(libraryId, page, itemsPerPage, search, orderBy, includeThumbnails)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageCollectionsApi#collectionList")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageCollectionsApi#collectionList")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **page** | **kotlin.Int**|  | [optional] [default to 1] |
| **itemsPerPage** | **kotlin.Int**|  | [optional] [default to 100] |
| **search** | **kotlin.String**|  | [optional] [default to &quot;&quot;] |
| **orderBy** | **kotlin.String**|  | [optional] [default to &quot;date&quot;] |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **includeThumbnails** | **kotlin.Boolean**|  | [optional] [default to false] |

### Return type

[**PaginationListOfCollectionModel**](PaginationListOfCollectionModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="collectionUpdateCollection"></a>
# **collectionUpdateCollection**
> StatusModel collectionUpdateCollection(libraryId, collectionId, updateCollectionModel)

Update Collection

### Example
```kotlin
// Import classes:
//import org.openapitools.client.infrastructure.*
//import org.openapitools.client.models.*

val apiInstance = ManageCollectionsApi()
val libraryId : kotlin.Long = 789 // kotlin.Long | 
val collectionId : kotlin.String = collectionId_example // kotlin.String | 
val updateCollectionModel : UpdateCollectionModel =  // UpdateCollectionModel | 
try {
    val result : StatusModel = apiInstance.collectionUpdateCollection(libraryId, collectionId, updateCollectionModel)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ManageCollectionsApi#collectionUpdateCollection")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ManageCollectionsApi#collectionUpdateCollection")
    e.printStackTrace()
}
```

### Parameters
| **libraryId** | **kotlin.Long**|  | |
| **collectionId** | **kotlin.String**|  | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **updateCollectionModel** | [**UpdateCollectionModel**](UpdateCollectionModel.md)|  | |

### Return type

[**StatusModel**](StatusModel.md)

### Authorization


Configure AccessKey:
    ApiClient.apiKey["AccessKey"] = ""
    ApiClient.apiKeyPrefix["AccessKey"] = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

