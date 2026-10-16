package net.bunny.api.settings

import net.bunny.api.ktor.defaultJson
import net.bunny.api.settings.data.model.PlayerSettingsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Enterprise DRM fields of `/play`, in the three shapes the API actually sends: an SD-only-for-L3
 * library, an L1-only library (resolution null), and a response that has none of the fields.
 */
class PlayerSettingsResponseTest {

    private fun parse(extraFields: String) = defaultJson
        .decodeFromString<PlayerSettingsResponse>(
            """
            {
              "thumbnailUrl": "https://example.b-cdn.net/thumbnail.jpg",
              "controls": "play,progress,settings",
              "playerKeyColor": "#ff7755",
              "captionsFontSize": 20,
              "captionsFontColor": null,
              "captionsBackground": null,
              "uiLanguage": "en",
              "showHeatmap": false,
              "fontFamily": "Rubik",
              "playbackSpeeds": "0.5,1,2",
              "enableDRM": true,
              "vastTagUrl": null,
              "captionsPath": "https://example.b-cdn.net/captions/",
              "seekPath": "https://example.b-cdn.net/seek/",
              "videoPlaylistUrl": "https://example.b-cdn.net/playlist.m3u8"
              $extraFields
            }
            """.trimIndent()
        )
        .toModel()

    @Test
    fun `sd only for L3 library carries its resolution`() {
        val settings = parse(
            """
            , "widevineMinClientSecurityLevel": null,
              "widevineEmeRobustness": null,
              "widevineSdOnlyForL3": true,
              "maxSoftwareWidevineResolution": 480
            """
        )

        assertTrue(settings.widevineSdOnlyForL3)
        assertEquals(480, settings.maxSoftwareWidevineResolution)
        assertNull(settings.widevineMinClientSecurityLevel)
    }

    @Test
    fun `L1 only library carries its minimum level`() {
        val settings = parse(
            """
            , "widevineMinClientSecurityLevel": 1,
              "widevineEmeRobustness": "HW_SECURE_DECODE",
              "widevineSdOnlyForL3": false,
              "maxSoftwareWidevineResolution": null
            """
        )

        assertEquals(1, settings.widevineMinClientSecurityLevel)
        assertFalse(settings.widevineSdOnlyForL3)
        assertNull(settings.maxSoftwareWidevineResolution)
    }

    @Test
    fun `missing fields fall back to no restrictions`() {
        val settings = parse("")

        assertNull(settings.widevineMinClientSecurityLevel)
        assertFalse(settings.widevineSdOnlyForL3)
        assertNull(settings.maxSoftwareWidevineResolution)
    }

    @Test
    fun `explicit null flag reads as off`() {
        val settings = parse(""", "widevineSdOnlyForL3": null""")

        assertFalse(settings.widevineSdOnlyForL3)
    }
}
