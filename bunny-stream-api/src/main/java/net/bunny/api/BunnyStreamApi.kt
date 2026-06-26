package net.bunny.api

import android.content.Context
import android.util.Log
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import net.bunny.api.api.ManageCollectionsApi
import net.bunny.api.api.ManageLiveStreamsApi
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.ktor.initHttpClient
import net.bunny.api.livestream.data.DefaultLiveStreamRepository
import net.bunny.api.settings.data.DefaultSettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.upload.DefaultVideoUploader
import net.bunny.api.upload.service.basic.BasicUploaderService
import net.bunny.api.upload.service.tus.TusUploaderService
import org.openapitools.client.infrastructure.ApiClient
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

class BunnyStreamApi private constructor(
    context: Context,
    accessKey: String?,
) : StreamApi {

    companion object {
        private const val TUS_PREFS_FILE = "tusPrefs"
        private const val HTTP_LOG_TAG = "BunnyLive/HTTP"
        private const val HTTP_LOG_MAX_BODY_BYTES = 64L * 1024L

        const val baseApi = BuildConfig.BASE_API

        lateinit var cdnHostname: String
            private set

        var libraryId: Long = -1
            private set

        @Volatile
        private var instance: StreamApi? = null

        fun initialize(context: Context, accessKey: String?, libraryId: Long) {
            instance = BunnyStreamApi(
                context.applicationContext,
                accessKey,
            )

            this.libraryId = libraryId
            accessKey?.let {
                ApiClient.apiKey["AccessKey"] = it
            }
        }

        fun getInstance(): StreamApi {
            return instance!!
        }

        fun isInitialized(): Boolean {
            return instance != null
        }

        fun release() {
            instance = null
        }
    }

    // OkHttp client that injects Referer for the /play endpoint
    private val okHttpClientWithReferer: OkHttpClient = ApiClient.defaultClient
        .newBuilder()
        // Identifies the SDK on every request, e.g. "bunny-stream-android/1.3.2".
        .addInterceptor(Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", BuildConfig.USER_AGENT)
                    .build(),
            )
        })
        .addInterceptor(Interceptor { chain ->
            val originalRequest = chain.request()
            val path = originalRequest.url.encodedPath

            val isPlayEndpoint = path.endsWith("/play")
            val requestBuilder = originalRequest.newBuilder()

            if (isPlayEndpoint) {
                requestBuilder.header("Referer", "https://iframe.mediadelivery.net/")
            }

            chain.proceed(requestBuilder.build())
        })
        // Logs full request/response headers and bodies for every API call so behavior is
        // inspectable from logcat (tag: BunnyLive/HTTP). Bodies are logged only when they're small
        // and textual (JSON/text/form/xml); large or binary payloads — image/video uploads — are
        // summarized as "<N bytes type>" instead of being buffered into a String, which would OOM
        // the app. Secret header values (AccessKey, AuthorizationSignature) are redacted.
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()

            Log.d(
                HTTP_LOG_TAG,
                "--> ${request.method} ${request.url}" +
                    formatHeaders(request.headers) +
                    describeRequestBody(request.body)?.let { "\nbody: $it" }.orEmpty()
            )

            val response = chain.proceed(request)

            val responseBody = if (response.isTextBody() &&
                (response.body?.contentLength() ?: 0L) <= HTTP_LOG_MAX_BODY_BYTES
            ) {
                response.peekBody(HTTP_LOG_MAX_BODY_BYTES).string()
            } else {
                response.body?.let { "<${it.contentLength()} bytes ${it.contentType() ?: "binary"}>" }
                    .orEmpty()
            }
            Log.d(
                HTTP_LOG_TAG,
                "<-- ${response.code} ${request.method} ${request.url}" +
                    formatHeaders(response.headers) +
                    if (responseBody.isNotEmpty()) "\nbody: $responseBody" else ""
            )

            response
        })
        .build()

    // Shares the User-Agent-carrying client so collections calls are identified too.
    override val collectionsApi = ManageCollectionsApi(baseApi, okHttpClientWithReferer)

    override val videosApi = ManageVideosApi(baseApi, okHttpClientWithReferer)

    override val liveStreamsApi = ManageLiveStreamsApi(baseApi, okHttpClientWithReferer)

    private val prefs = context.getSharedPreferences(TUS_PREFS_FILE, Context.MODE_PRIVATE)

    private val ktorClient = initHttpClient(accessKey)

    private val basicUploaderService = BasicUploaderService(
        ktorClient,
        Dispatchers.IO
    )
    private val tusVideoUploaderService = TusUploaderService(
        preferences = prefs,
        chunkSize = 1024,
        accessKey = accessKey ?: run {
            /**
             * AccessKey is required for TusUploaderService, if not provided fallback to
             * BasicUploaderService which will be used instead.
             */
            throw IllegalStateException("AccessKey must be provided for TusUploaderService")
        },
        dispatcher = Dispatchers.IO
    )

    override val videoUploader = DefaultVideoUploader(
        context = context,
        videoUploadService = basicUploaderService,
        ioDispatcher = Dispatchers.IO,
        videosApi
    )

    override val tusVideoUploader = DefaultVideoUploader(
        context = context,
        videoUploadService = tusVideoUploaderService,
        ioDispatcher = Dispatchers.IO,
        videosApi
    )

    override val settingsRepository = DefaultSettingsRepository(
        httpClient = ktorClient,
        coroutineDispatcher = Dispatchers.IO
    )

    override val liveStreamRepository = DefaultLiveStreamRepository(
        liveStreamsApi = liveStreamsApi,
        coroutineDispatcher = Dispatchers.IO
    )

    override suspend fun fetchPlayerSettings(libraryId: Long, videoId: String, token: String?, expires: Long?): Either<String, PlayerSettings> {
        return settingsRepository.fetchSettings(libraryId, videoId, token, expires)
    }
}

/** True when [this] media type is textual (JSON/text/xml/form) and safe to log inline. */
private fun MediaType?.isTextual(): Boolean {
    if (this == null) return false
    if (type == "text") return true
    return subtype.contains("json", ignoreCase = true) ||
        subtype.contains("xml", ignoreCase = true) ||
        subtype.contains("urlencoded", ignoreCase = true)
}

private fun Response.isTextBody(): Boolean = body?.contentType().isTextual()

/** Header names whose values are secret and must never be written to logs. */
private val REDACTED_HEADERS = setOf("AccessKey", "AuthorizationSignature")

/**
 * Renders HTTP [headers] for logging, one `name: value` per line. Values of [REDACTED_HEADERS]
 * (API key / upload signature) are masked so secrets don't leak into logcat. Returns "" when empty.
 */
private fun formatHeaders(headers: Headers): String {
    if (headers.size == 0) return ""
    return "\nheaders:\n" + (0 until headers.size).joinToString("\n") { i ->
        val name = headers.name(i)
        val value = if (REDACTED_HEADERS.any { it.equals(name, ignoreCase = true) }) {
            "██ (redacted)"
        } else {
            headers.value(i)
        }
        "  $name: $value"
    }
}

/**
 * Renders a request body for logging. Small textual bodies are returned verbatim; large or binary
 * bodies (image/video uploads) are summarized as `<N bytes type>` so we never buffer a whole file
 * into a String. Returns null when there's no body.
 */
private fun describeRequestBody(body: RequestBody?): String? {
    if (body == null) return null
    val contentLength = body.contentLength()
    return if (body.contentType().isTextual() && contentLength in 1..(64L * 1024L)) {
        val buffer = Buffer()
        body.writeTo(buffer)
        buffer.readUtf8()
    } else {
        "<$contentLength bytes ${body.contentType() ?: "binary"}>"
    }
}
