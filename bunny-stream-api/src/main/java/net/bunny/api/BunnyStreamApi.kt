package net.bunny.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import net.bunny.api.api.ManageCollectionsApi
import net.bunny.api.api.ManageLiveStreamsApi
import net.bunny.api.api.ManageVideosApi
import net.bunny.api.collection.data.DefaultCollectionRepository
import net.bunny.api.collection.domain.CollectionRepository
import net.bunny.api.error.BunnyResult
import net.bunny.api.ktor.initHttpClient
import net.bunny.api.livestream.data.DefaultLiveStreamRepository
import net.bunny.api.settings.data.DefaultSettingsRepository
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.settings.domain.SettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.upload.DefaultVideoUploader
import net.bunny.api.upload.VideoUploader
import net.bunny.api.video.data.DefaultVideoRepository
import net.bunny.api.video.domain.VideoRepository
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
    override val config: BunnyStreamConfig,
) : StreamApi {

    companion object {
        private const val TUS_PREFS_PREFIX = "tusPrefs"
        private const val HTTP_LOG_TAG = "BunnyLive/HTTP"

        @Volatile
        private var defaultInstance: BunnyStreamApi? = null

        /**
         * Creates an SDK instance for one library and hands it back to you.
         *
         * The instance owns its own credentials, uploads and HTTP client, so several can be live
         * at once — one per library. Nothing is registered globally: keep the returned handle for
         * as long as you need it and call [StreamApi.release] when you are done.
         *
         * Use [initialize] instead if your app talks to a single library and you would rather the
         * SDK hold the instance for you.
         */
        fun create(context: Context, config: BunnyStreamConfig): StreamApi =
            BunnyStreamApi(context.applicationContext, config)

        /** Creates an instance from the two values most callers configure. @see create */
        fun create(context: Context, accessKey: String, libraryId: Long): StreamApi =
            create(context, BunnyStreamConfig(accessKey, libraryId))

        /**
         * Creates an instance and keeps it as the default one, reachable from [getInstance].
         *
         * The SDK's own views ([net.bunny.api.StreamApi] consumers in `:player` and `:recording`)
         * fall back to this instance when they are not given one, so a single-library app can call
         * this once and never pass a handle around.
         *
         * Calling it again replaces the default instance and releases the previous one, which
         * stops its in-flight uploads. Instances made with [create] are untouched.
         */
        fun initialize(context: Context, config: BunnyStreamConfig) {
            // Uploads run on a scope owned by the instance. Replacing the instance without
            // stopping them would leave transfers running against the previous library and key,
            // with no handle left to reach them.
            defaultInstance?.release()
            defaultInstance = BunnyStreamApi(context.applicationContext, config)
        }

        /**
         * Initialises the default instance. [accessKey] is the library API key and is required —
         * every SDK feature (REST, uploads, live streaming) authenticates with it.
         */
        fun initialize(context: Context, accessKey: String, libraryId: Long) {
            initialize(context, BunnyStreamConfig(accessKey, libraryId))
        }

        /**
         * The default instance.
         *
         * @throws IllegalStateException when [initialize] has not been called. Guard with
         *   [isInitialized] if you cannot be sure, or hold an instance from [create] instead.
         */
        fun getInstance(): StreamApi = defaultInstance ?: error(
            "BunnyStreamApi has no default instance. Call BunnyStreamApi.initialize(context, " +
                "accessKey, libraryId) before using the SDK, or create an instance with " +
                "BunnyStreamApi.create(...) and pass it in.",
        )

        /** True once [initialize] has been called and the default instance has not been released. */
        fun isInitialized(): Boolean = defaultInstance != null

        /** Releases the default instance, stopping its in-flight uploads. */
        fun release() {
            defaultInstance?.release()
            defaultInstance = null
        }
    }

    // OkHttp client that authenticates this instance and injects Referer for the /play endpoint
    private val okHttpClientWithReferer: OkHttpClient = ApiClient.defaultClient
        .newBuilder()
        // Authenticates every call this instance makes.
        //
        // The generated client keeps its key in a static map shared by every ApiClient in the
        // process, so filling it in would mean the most recently created instance authenticating
        // for all of them. We leave that map empty: the generated `updateAuthParams` only sets the
        // header when it isn't already there, so setting it here wins and each instance carries
        // its own key.
        .addInterceptor(Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("AccessKey", config.accessKey)
                    .build(),
            )
        })
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
                requestBuilder.header("Referer", BunnyCdn.REFERER)
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

    // The generated clients are implementation detail now — repositories below are the public
    // surface. They share the User-Agent-carrying OkHttp client so every call is identified.
    private val collectionsApi = ManageCollectionsApi(config.baseApi, okHttpClientWithReferer)

    private val videosApi = ManageVideosApi(config.baseApi, okHttpClientWithReferer)

    private val liveStreamsApi = ManageLiveStreamsApi(config.baseApi, okHttpClientWithReferer)

    // One resume store per library. TUS keys its store by a fingerprint of the file being
    // uploaded, so a single shared store would hand instance B the upload URL that instance A
    // created — resuming a transfer into the wrong library.
    private val prefs = context.getSharedPreferences(
        "$TUS_PREFS_PREFIX-${config.libraryId}",
        Context.MODE_PRIVATE,
    )

    private val ktorClient = initHttpClient(config.accessKey)

    private val basicUploaderService = BasicUploaderService(
        ktorClient,
        Dispatchers.IO
    )
    private val tusVideoUploaderService = TusUploaderService(
        preferences = prefs,
        chunkSize = 1024,
        accessKey = config.accessKey,
        dispatcher = Dispatchers.IO
    )

    @Volatile
    private var released = false

    /**
     * Stops every in-flight upload and frees what this instance holds: the uploader scopes and the
     * HTTP client behind player settings and plain uploads, which owns a thread pool and a
     * connection pool of its own.
     *
     * **Do not use the instance afterwards.** Calls made through a released instance fail; hold a
     * new one from [create] instead. Calling this twice is harmless.
     *
     * Releasing one instance leaves every other one running. The default instance is released for
     * you when [initialize] replaces it or [BunnyStreamApi.release] drops it.
     */
    override fun release() {
        if (released) return
        released = true
        // The private fields, not the guarded accessors — those refuse once `released` is set.
        videoUploaderImpl.shutdown()
        tusVideoUploaderImpl.shutdown()
        // The OkHttp client is a view onto the shared default one and has nothing of its own to
        // free, but Ktor built its own engine — leaving it open leaks a thread pool per instance.
        ktorClient.close()
    }

    private val videoRepositoryImpl = DefaultVideoRepository(
        videosApi = videosApi,
        coroutineDispatcher = Dispatchers.IO
    )

    private val videoUploaderImpl = DefaultVideoUploader(
        context = context,
        videoUploadService = basicUploaderService,
        ioDispatcher = Dispatchers.IO,
        videoRepository = videoRepositoryImpl,
    )

    private val tusVideoUploaderImpl = DefaultVideoUploader(
        context = context,
        videoUploadService = tusVideoUploaderService,
        ioDispatcher = Dispatchers.IO,
        videoRepository = videoRepositoryImpl,
    )

    private val collectionRepositoryImpl = DefaultCollectionRepository(
        collectionsApi = collectionsApi,
        coroutineDispatcher = Dispatchers.IO
    )

    private val settingsRepositoryImpl = DefaultSettingsRepository(
        httpClient = ktorClient,
        baseApi = config.baseApi,
        coroutineDispatcher = Dispatchers.IO
    )

    private val liveStreamRepositoryImpl = DefaultLiveStreamRepository(
        liveStreamsApi = liveStreamsApi,
        coroutineDispatcher = Dispatchers.IO
    )

    override val videoRepository: VideoRepository get() = usable(videoRepositoryImpl)

    override val videoUploader: VideoUploader get() = usable(videoUploaderImpl)

    override val tusVideoUploader: VideoUploader get() = usable(tusVideoUploaderImpl)

    override val collectionRepository: CollectionRepository get() = usable(collectionRepositoryImpl)

    override val settingsRepository: SettingsRepository get() = usable(settingsRepositoryImpl)

    override val liveStreamRepository: LiveStreamRepository get() = usable(liveStreamRepositoryImpl)

    /**
     * Guards every way into this instance against use after [release].
     *
     * Releasing closes the HTTP client, and a request issued through a closed one fails with a
     * cancellation that propagates into the *caller's* coroutine scope — cancelling work that has
     * nothing to do with the SDK. Saying so plainly is better than that.
     */
    private fun <T> usable(value: T): T {
        check(!released) {
            "This BunnyStreamApi instance has been released. Create another one with " +
                "BunnyStreamApi.create(...), or call BunnyStreamApi.initialize(...) again."
        }
        return value
    }

    override suspend fun fetchPlayerSettings(
        libraryId: Long,
        videoId: String,
        token: String?,
        expires: Long?,
    ): BunnyResult<PlayerSettings> {
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

/** Bodies larger than this are summarized rather than buffered into a String for the log. */
private const val HTTP_LOG_MAX_BODY_BYTES = 64L * 1024L

/**
 * Renders a request body for logging. Small textual bodies are returned verbatim; large or binary
 * bodies (image/video uploads) are summarized as `<N bytes type>` so we never buffer a whole file
 * into a String. Returns null when there's no body.
 */
private fun describeRequestBody(body: RequestBody?): String? {
    if (body == null) return null
    val contentLength = body.contentLength()
    return if (body.contentType().isTextual() && contentLength in 1..HTTP_LOG_MAX_BODY_BYTES) {
        val buffer = Buffer()
        body.writeTo(buffer)
        buffer.readUtf8()
    } else {
        "<$contentLength bytes ${body.contentType() ?: "binary"}>"
    }
}
