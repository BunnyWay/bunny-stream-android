package net.bunny.bunnystreamplayer.cast

import net.bunny.api.settings.domain.model.PlayerSettings
import org.json.JSONObject

/**
 * Builds the Bunny receiver's LoadRequest customData: Widevine DRM
 * configuration plus embed theming, mirroring what the web sender ships.
 * The receiver validates every value and falls back to its defaults for
 * anything missing, so fields are simply omitted when not configured.
 *
 * Kept as pure Map-building (JSON conversion at the edge) so the contract
 * is unit-testable without Android's org.json stubs.
 */
object CastCustomData {

    /**
     * @param drmLicenseUrl token-aware Widevine license URL, or null when
     *   the video is not DRM-protected. The TV fetches the license itself
     *   (no Referer header), so token authentication must ride on the URL.
     */
    fun build(playerSettings: PlayerSettings?, drmLicenseUrl: String?): Map<String, Any> {
        val customData = mutableMapOf<String, Any>()

        if (!drmLicenseUrl.isNullOrBlank()) {
            customData["drm"] = mapOf(
                "widevine" to mapOf(
                    "licenseUrl" to drmLicenseUrl,
                    "withCredentials" to false,
                ),
            )
        }

        buildStyle(playerSettings)?.let { customData["style"] = it }

        return customData
    }

    private fun buildStyle(settings: PlayerSettings?): Map<String, Any>? {
        if (settings == null) return null
        val style = mutableMapOf<String, Any>()

        colorToCss(settings.keyColor)?.let { style["keyColor"] = it }

        val captions = mutableMapOf<String, Any>()
        settings.captionsFontColor?.let { color ->
            colorToCss(color)?.let { captions["fontColor"] = it }
        }
        settings.captionsBackgroundColor?.let { color ->
            colorToCss(color)?.let { captions["background"] = it }
        }
        if (settings.captionsFontSize > 0) {
            captions["fontSize"] = "${settings.captionsFontSize}px"
        }
        if (settings.fontFamily.isNotBlank()) {
            captions["fontFamily"] = settings.fontFamily
        }
        if (captions.isNotEmpty()) style["captions"] = captions

        return style.ifEmpty { null }
    }

    /**
     * Android AARRGGBB color int → CSS hex (#rrggbb, or #rrggbbaa when the
     * color is translucent). Fully transparent colors mean "not configured"
     * (e.g. the zeroed fallback PlayerSettings) and are omitted.
     */
    fun colorToCss(color: Int): String? {
        val alpha = (color ushr 24) and 0xFF
        if (alpha == 0) return null
        val rgb = String.format("#%06x", color and 0xFFFFFF)
        return if (alpha == 0xFF) rgb else String.format("%s%02x", rgb, alpha)
    }

    fun toJson(map: Map<String, Any>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, if (value is Map<*, *>) toJson(@Suppress("UNCHECKED_CAST") (value as Map<String, Any>)) else value)
        }
        return json
    }
}
