package net.bunny.bunnystreamplayer

import android.media.MediaDrm
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import net.bunny.api.settings.domain.model.PlayerSettings

/**
 * Bunny's Enterprise DRM rules from the library's play data, checked against the Widevine
 * security level of this device.
 *
 * Two of them matter to the player:
 * - `widevineSdOnlyForL3`: the license server only hands L3 (software-only) devices the keys for
 *   the SD renditions. A player that picks 720p or 1080p anyway gets a license that does not cover
 *   the track and dies in the decoder, so on L3 the track selector has to stay at or below
 *   `maxSoftwareWidevineResolution`, the way Bunny's web player does.
 * - `widevineMinClientSecurityLevel`: devices below that level get no license at all. Nothing on
 *   the device can fix that, so the viewer is told so instead of seeing a raw DRM error code.
 *
 * Widevine levels count downwards: 1 (L1) is hardware-backed and the strictest, 3 (L3) is
 * software only. When the device level is unknown (no Widevine, or MediaDrm failed to answer),
 * neither rule applies and playback goes ahead as before.
 *
 * The decisions are pure functions of their arguments; only [deviceSecurityLevel] touches the
 * platform.
 */
internal object WidevinePolicy {

    /** The SD ceiling when a library turns `widevineSdOnlyForL3` on without a resolution. */
    const val DEFAULT_SD_RESOLUTION = 480

    private const val TAG = "WidevinePolicy"

    /**
     * The Widevine security level of this device (1, 2 or 3), or null when it can't be read.
     * Read once per process: it is a property of the hardware and never changes.
     */
    val deviceSecurityLevel: Int? by lazy { readDeviceSecurityLevel() }

    /** Maps MediaDrm's `securityLevel` property ("L1", "L2", "L3") to its number. */
    fun parseSecurityLevel(value: String?): Int? = when (value?.trim()?.uppercase()) {
        "L1" -> 1
        "L2" -> 2
        "L3" -> 3
        else -> null
    }

    /**
     * The highest resolution this device may play, as the shorter side of the picture (480 for
     * 480p), or null when every rendition is allowed. Only L3 is capped: the dashboard setting is
     * "SD only for L3", and the license server applies it to L3 alone.
     */
    fun maxResolution(settings: PlayerSettings, deviceLevel: Int?): Int? {
        if (!settings.drmEnabled || !settings.widevineSdOnlyForL3) return null
        if (deviceLevel != 3) return null
        return settings.maxSoftwareWidevineResolution?.takeIf { it > 0 } ?: DEFAULT_SD_RESOLUTION
    }

    /**
     * False when the library requires a stronger Widevine level than this device has, so no
     * license will be issued. An unknown device level is let through: the license server is the
     * one that decides, and blocking on a failed query would lock out devices that can play.
     */
    fun isSecurityLevelSupported(settings: PlayerSettings, deviceLevel: Int?): Boolean {
        if (!settings.drmEnabled) return true
        // Anything outside L1..L3 is not a level a device can have, so it restricts nothing.
        val required = settings.widevineMinClientSecurityLevel?.takeIf { it in 1..3 } ?: return true
        if (deviceLevel == null) return true
        return deviceLevel <= required
    }

    /**
     * The track selector's max video size (width to height) for a [maxResolution] cap. "480p" is
     * the shorter side, so a portrait video is capped on its width; a video of unknown shape is
     * treated as landscape, which is how Bunny's renditions are named.
     */
    fun maxVideoSize(maxResolution: Int, videoWidth: Int?, videoHeight: Int?): Pair<Int, Int> {
        val portrait = videoWidth != null && videoHeight != null &&
            videoWidth > 0 && videoHeight > 0 && videoWidth < videoHeight
        return if (portrait) maxResolution to Int.MAX_VALUE else Int.MAX_VALUE to maxResolution
    }

    /**
     * Whether a rendition of [width] x [height] fits under [maxResolution]. Unknown dimensions
     * (non-positive, media3's `Format.NO_VALUE`) don't count; a rendition with neither known is
     * kept, since there is nothing to judge it by.
     */
    fun fits(width: Int, height: Int, maxResolution: Int): Boolean {
        val shorterSide = listOf(width, height).filter { it > 0 }.minOrNull() ?: return true
        return shorterSide <= maxResolution
    }

    private fun readDeviceSecurityLevel(): Int? {
        val drm = try {
            MediaDrm(C.WIDEVINE_UUID)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w(TAG, "Widevine is not available on this device", e)
            return null
        }
        return try {
            val raw = drm.getPropertyString("securityLevel")
            parseSecurityLevel(raw).also { Log.d(TAG, "Widevine security level: $raw") }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w(TAG, "Could not read the Widevine security level", e)
            null
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                drm.close()
            } else {
                @Suppress("DEPRECATION")
                drm.release()
            }
        }
    }
}
