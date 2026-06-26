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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.bunny.api.BuildConfig

val defaultJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = true
}

fun initHttpClient(accessKey: String?): HttpClient {

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
        }

        install(ResponseObserver) {
            onResponse { response ->
                Log.d("HTTP status:", "${response.status.value}")
            }
        }

        install(DefaultRequest) {
            header(HttpHeaders.Accept, "*/*")
        }

        // Identify the SDK on every request, e.g. "bunny-stream-android/1.3.2".
        install(UserAgent) {
            agent = BuildConfig.USER_AGENT
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    client.plugin(HttpSend).intercept { request ->
        accessKey?.let { request.header("AccessKey", it) }

        execute(request)
    }

    return client
}