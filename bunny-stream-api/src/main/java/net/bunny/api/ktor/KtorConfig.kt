package net.bunny.api.ktor

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.bunny.api.BuildConfig
import net.bunny.api.BunnyCdn
import net.bunny.api.http.MissingAccessKeyException
import net.bunny.api.http.isPublicPlaybackPath

val defaultJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = true
}

fun initHttpClient(
    accessKey: String?,
    userAgent: String = BuildConfig.USER_AGENT,
): HttpClient {

    val client = HttpClient(OkHttp) {

        install(ContentNegotiation) {
            json(defaultJson)
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("Logger Ktor =>", message)
                }
            }
            // Full request/response logging, including bodies, for everything EXCEPT uploads.
            // LogLevel.ALL buffers the whole body into a String to log it; for a video upload that
            // body is the entire file, which allocates tens of MB and OOMs the app. Uploads are the
            // only PUT requests here (reads are GET, creates POST), so we skip logging PUTs and log
            // every other call in full.
            level = LogLevel.ALL
            filter { request -> request.method != HttpMethod.Put }
            // The library API key rides on every request. Without this it is written to logcat in
            // full on release builds too, where any app holding READ_LOGS — or anyone with the
            // device attached — can read it. The OkHttp path redacts the same headers.
            sanitizeHeader { header ->
                header.equals("AccessKey", ignoreCase = true) ||
                    header.equals("AuthorizationSignature", ignoreCase = true)
            }
        }

        install(ResponseObserver) {
            onResponse { response ->
                Log.d("HTTP status:", "${response.status.value}")
            }
        }

        install(DefaultRequest) {
            header(HttpHeaders.Accept, "*/*")
        }

        // Identify the device and the SDK on every request, e.g.
    // "Dalvik/2.1.0 (Linux; U; Android 15; Pixel 8 Build/AP4A.250105.002) bunny-stream-android/4.2.1 (mobile)".
    // The caller (BunnyStreamApi) already bound the device and the integrator suffix into [userAgent].
    install(UserAgent) {
        agent = userAgent
    }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    client.plugin(HttpSend).intercept { request ->
        val isPlayback = isPublicPlaybackPath(request.url.encodedPath)

        // Same rule as the OkHttp client: a playback-only instance (no key) only reaches the
        // public /play endpoints. Anything else would answer 401, so it is refused before it
        // leaves the device and surfaces as a terminal InvalidState.
        when {
            accessKey != null -> request.header("AccessKey", accessKey)
            !isPlayback ->
                throw MissingAccessKeyException("${request.method.value} ${request.url.encodedPath}")
        }

        // The /play endpoints get the same Referer the OkHttp client sends. A library that blocks
        // requests without a referrer answers 401 without it, and on a playback-only instance
        // there is no AccessKey to authorize the call instead, so the player settings never load.
        if (isPlayback) {
            request.header(HttpHeaders.Referrer, BunnyCdn.REFERER)
        }

        execute(request)
    }

    return client
}