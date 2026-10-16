package net.bunny.api

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceUserAgentTest {

    @Test
    fun `the runtime's own user agent is kept as it is`() {
        val httpAgent = "Dalvik/2.1.0 (Linux; U; Android 15; Pixel 8 Build/AP4A.250105.002)"
        assertEquals(
            httpAgent,
            DeviceUserAgent.platform(httpAgent, release = "14", model = "other", buildId = "X"),
        )
    }

    @Test
    fun `non-ASCII characters are dropped, OkHttp would reject the header`() {
        assertEquals(
            "Dalvik/2.1.0 (Linux; U; Android 14; Galaxy Tab S9 Build/UP1A)",
            DeviceUserAgent.platform(
                httpAgent = "Dalvik/2.1.0 (Linux; U; Android 14; Galaxy™ Tab S9 Build/UP1A)",
                release = null,
                model = null,
                buildId = null,
            ),
        )
    }

    @Test
    fun `without http agent the same shape is built from the build fields`() {
        // Parentheses and semicolons in the model would split the comment into fields.
        assertEquals(
            "Dalvik/2.1.0 (Linux; U; Android 15; Pixel 8 5G Build/AP4A)",
            DeviceUserAgent.platform(httpAgent = null, release = "15", model = "Pixel 8 (5G)", buildId = "AP4A"),
        )
        assertEquals(
            "Dalvik/2.1.0 (Linux; U; Android unknown; unknown Build/unknown)",
            DeviceUserAgent.platform(httpAgent = " ", release = null, model = "", buildId = null),
        )
    }

    @Test
    fun `a television UI mode is a tv, whatever its width`() {
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
        assertEquals(DeviceUserAgent.TV, DeviceUserAgent.deviceType(uiMode, smallestScreenWidthDp = 960))
    }

    @Test
    fun `sw600dp and up is a tablet, below is a phone`() {
        val uiMode = Configuration.UI_MODE_TYPE_NORMAL
        assertEquals(DeviceUserAgent.TABLET, DeviceUserAgent.deviceType(uiMode, smallestScreenWidthDp = 600))
        assertEquals(DeviceUserAgent.MOBILE, DeviceUserAgent.deviceType(uiMode, smallestScreenWidthDp = 411))
    }

    @Test
    fun `an undefined width is a phone`() {
        assertEquals(
            DeviceUserAgent.MOBILE,
            DeviceUserAgent.deviceType(uiMode = 0, smallestScreenWidthDp = 0),
        )
    }
}
