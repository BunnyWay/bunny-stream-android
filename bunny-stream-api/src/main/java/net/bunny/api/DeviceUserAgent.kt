package net.bunny.api

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build

/**
 * The device side of the SDK's User-Agent.
 *
 * The header starts with the platform's own User-Agent, the one Android's HTTP stack sends by
 * default (`Dalvik/2.1.0 (Linux; U; Android 15; Pixel 8 Build/AP4A.250105.002)`), so a standard
 * parser still finds the OS, its version and the device. The SDK's product token follows, with the
 * kind of device as its comment: `mobile`, `tablet` or `tv`.
 */
internal object DeviceUserAgent {
    const val MOBILE = "mobile"
    const val TABLET = "tablet"
    const val TV = "tv"

    /** Android's own threshold for the tablet layouts (`sw600dp`). */
    private const val TABLET_SMALLEST_WIDTH_DP = 600

    /** Computed once: the OS, the model and the kind of device do not change while the app runs. */
    val platform: String by lazy {
        platform(
            httpAgent = System.getProperty("http.agent"),
            release = Build.VERSION.RELEASE,
            model = Build.MODEL,
            buildId = Build.ID,
        )
    }

    /** @see platform */
    val type: String by lazy { deviceType() }

    /**
     * The runtime's `http.agent`, or the same shape built by hand when a runtime does not set it.
     */
    fun platform(httpAgent: String?, release: String?, model: String?, buildId: String?): String =
        clean(httpAgent)
            ?: "Dalvik/2.1.0 (Linux; U; Android ${clean(release) ?: "unknown"}; " +
            "${clean(model)?.filter { it !in "();" } ?: "unknown"} Build/${clean(buildId) ?: "unknown"})"

    /**
     * The same split the platform uses for its resource qualifiers: a TV by its UI mode, a tablet
     * by `sw600dp`, a phone otherwise.
     */
    fun deviceType(uiMode: Int, smallestScreenWidthDp: Int): String = when {
        uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION -> TV
        smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP -> TABLET
        else -> MOBILE
    }

    // The system configuration describes the device, not the app's window: a phone app in split
    // screen is still a phone. It also needs no Context, which BunnyStreamConfig does not have.
    private fun deviceType(): String {
        val configuration = runCatching { Resources.getSystem().configuration }.getOrNull()
            ?: return MOBILE
        return deviceType(configuration.uiMode, configuration.smallestScreenWidthDp)
    }

    // OkHttp rejects a header with non-ASCII characters, and `http.agent` carries the model as the
    // vendor wrote it, trademark signs included.
    private fun clean(value: String?): String? =
        value
            ?.filter { it in ' '..'~' }
            ?.replace(Regex(" {2,}"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
