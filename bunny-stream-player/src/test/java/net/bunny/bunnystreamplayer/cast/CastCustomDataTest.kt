package net.bunny.bunnystreamplayer.cast

import net.bunny.api.settings.domain.model.PlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastCustomDataTest {

    private fun settings(
        keyColor: Int = 0xFFFF7755.toInt(),
        captionsFontSize: Int = 18,
        captionsFontColor: Int? = 0xFFC3C3C3.toInt(),
        captionsBackgroundColor: Int? = 0xFF4343FF.toInt(),
        fontFamily: String = "rubik",
    ) = PlayerSettings(
        thumbnailUrl = "",
        controls = "",
        keyColor = keyColor,
        captionsFontSize = captionsFontSize,
        captionsFontColor = captionsFontColor,
        captionsBackgroundColor = captionsBackgroundColor,
        uiLanguage = "en",
        showHeatmap = false,
        fontFamily = fontFamily,
        playbackSpeeds = listOf(1f),
        drmEnabled = false,
        vastTagUrl = null,
        videoUrl = "https://example.b-cdn.net/playlist.m3u8",
        seekPath = "",
        captionsPath = "",
    )

    @Test
    fun `builds the full sender contract`() {
        val customData = CastCustomData.build(
            settings(),
            "https://video.bunnycdn.com/WidevineLicense/1/vid?contentId=vid&token=t&expires=1",
        )

        assertEquals(
            mapOf(
                "drm" to mapOf(
                    "widevine" to mapOf(
                        "licenseUrl" to
                            "https://video.bunnycdn.com/WidevineLicense/1/vid?contentId=vid&token=t&expires=1",
                        "withCredentials" to false,
                    ),
                ),
                "style" to mapOf(
                    "keyColor" to "#ff7755",
                    "captions" to mapOf(
                        "fontColor" to "#c3c3c3",
                        "background" to "#4343ff",
                        "fontSize" to "18px",
                        "fontFamily" to "rubik",
                    ),
                ),
            ),
            customData,
        )
    }

    @Test
    fun `omits drm when no license url`() {
        val customData = CastCustomData.build(settings(), null)
        assertNull(customData["drm"])
        assertEquals("#ff7755", (customData["style"] as Map<*, *>)["keyColor"])
    }

    @Test
    fun `omits unconfigured style values`() {
        val customData = CastCustomData.build(
            settings(
                keyColor = 0,
                captionsFontSize = 0,
                captionsFontColor = null,
                captionsBackgroundColor = null,
                fontFamily = "",
            ),
            null,
        )
        assertEquals(emptyMap<String, Any>(), customData)
    }

    @Test
    fun `translucent colors keep their alpha channel`() {
        assertEquals("#00000080", CastCustomData.colorToCss(0x80000000.toInt()))
        assertEquals("#ffffff", CastCustomData.colorToCss(0xFFFFFFFF.toInt()))
        assertNull(CastCustomData.colorToCss(0x00FF0000))
    }
}
