package net.bunny.android.demo

import android.annotation.SuppressLint
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import net.bunny.api.BunnyCdn
import net.bunny.android.demo.di.Di
import net.bunny.android.demo.worker.PositionCleanupWorker
import okhttp3.Call
import okhttp3.OkHttpClient

class App : Application(), SingletonImageLoader.Factory {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var di: Di
    }

    override fun onCreate() {
        super.onCreate()
        di = Di(this)

        // Schedule periodic cleanup of expired resume positions
        // Use default retention of 7 days, or get from preferences if you have them
        PositionCleanupWorker.schedulePeriodicCleanup(
            context = this,
            retentionDays = 7 // You can make this configurable later
        )
    }

    /**
     * Coil loads Bunny thumbnails / posters straight from the CDN. When the library has
     * "Block direct url file access" enabled, the CDN rejects requests without an allowed
     * `Referer` (403 → blank images). The video player already sends this header on its own data
     * source; here we add it to every Coil request bound for a Bunny CDN host, so the demo's
     * images keep loading with the block on. Non-Bunny hosts are left untouched.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val callFactory: () -> Call.Factory = {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val out = if (host.endsWith("b-cdn.net") || host.contains("mediadelivery")) {
                        request.newBuilder()
                            .header("Referer", BunnyCdn.REFERER)
                            .build()
                    } else {
                        request
                    }
                    chain.proceed(out)
                }
                .build()
        }
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = callFactory)) }
            .build()
    }
}
