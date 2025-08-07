package net.bunny.api.ktor

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
            level = LogLevel.ALL
        }

        install(ResponseObserver) {
            onResponse { response ->
                Log.d("HTTP status:", "${response.status.value}")
            }
        }

        install(DefaultRequest) {
            header(HttpHeaders.Accept, "*/*")
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