package net.bunny.bunnystreamplayer

import net.bunny.api.settings.domain.model.PlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WidevinePolicy]'s decisions. The cases that matter: an L3 device on an SD-only-for-L3 library
 * is capped (and nothing else is), a device below the library's minimum level is refused, and an
 * unknown device level changes nothing.
 */
class WidevinePolicyTest {

    private fun settings(
        drmEnabled: Boolean = true,
        sdOnlyForL3: Boolean = false,
        maxSoftwareResolution: Int? = null,
        minSecurityLevel: Int? = null,
    ) = PlayerSettings(
        thumbnailUrl = "",
        controls = "",
        keyColor = 0,
        captionsFontSize = 0,
        captionsFontColor = null,
        captionsBackgroundColor = null,
        uiLanguage = "",
        showHeatmap = false,
        fontFamily = "",
        playbackSpeeds = emptyList(),
        drmEnabled = drmEnabled,
        vastTagUrl = null,
        videoUrl = "",
        seekPath = "",
        captionsPath = "",
        widevineMinClientSecurityLevel = minSecurityLevel,
        widevineSdOnlyForL3 = sdOnlyForL3,
        maxSoftwareWidevineResolution = maxSoftwareResolution,
    )

    // region parseSecurityLevel

    @Test
    fun `parses the MediaDrm security level property`() {
        assertEquals(1, WidevinePolicy.parseSecurityLevel("L1"))
        assertEquals(2, WidevinePolicy.parseSecurityLevel("L2"))
        assertEquals(3, WidevinePolicy.parseSecurityLevel("L3"))
        assertEquals(3, WidevinePolicy.parseSecurityLevel(" l3 "))
    }

    @Test
    fun `unknown security level values parse to null`() {
        assertNull(WidevinePolicy.parseSecurityLevel(null))
        assertNull(WidevinePolicy.parseSecurityLevel(""))
        assertNull(WidevinePolicy.parseSecurityLevel("Unknown"))
    }

    // endregion

    // region maxResolution

    @Test
    fun `L3 on an sd only library is capped to the library's resolution`() {
        val sdOnly = settings(sdOnlyForL3 = true, maxSoftwareResolution = 480)
        assertEquals(480, WidevinePolicy.maxResolution(sdOnly, deviceLevel = 3))
    }

    @Test
    fun `L3 on an sd only library without a resolution falls back to 480`() {
        assertEquals(480, WidevinePolicy.maxResolution(settings(sdOnlyForL3 = true), deviceLevel = 3))
        val zero = settings(sdOnlyForL3 = true, maxSoftwareResolution = 0)
        assertEquals(480, WidevinePolicy.maxResolution(zero, deviceLevel = 3))
    }

    @Test
    fun `L1 and L2 devices are never capped`() {
        val sdOnly = settings(sdOnlyForL3 = true, maxSoftwareResolution = 480)
        assertNull(WidevinePolicy.maxResolution(sdOnly, deviceLevel = 1))
        assertNull(WidevinePolicy.maxResolution(sdOnly, deviceLevel = 2))
    }

    @Test
    fun `unknown device level is not capped`() {
        val sdOnly = settings(sdOnlyForL3 = true, maxSoftwareResolution = 480)
        assertNull(WidevinePolicy.maxResolution(sdOnly, deviceLevel = null))
    }

    @Test
    fun `no cap without the sd only flag or without DRM`() {
        assertNull(WidevinePolicy.maxResolution(settings(maxSoftwareResolution = 480), deviceLevel = 3))
        val noDrm = settings(drmEnabled = false, sdOnlyForL3 = true, maxSoftwareResolution = 480)
        assertNull(WidevinePolicy.maxResolution(noDrm, deviceLevel = 3))
    }

    // endregion

    // region isSecurityLevelSupported

    @Test
    fun `L1 only library refuses L2 and L3 devices`() {
        val l1Only = settings(minSecurityLevel = 1)
        assertTrue(WidevinePolicy.isSecurityLevelSupported(l1Only, deviceLevel = 1))
        assertFalse(WidevinePolicy.isSecurityLevelSupported(l1Only, deviceLevel = 2))
        assertFalse(WidevinePolicy.isSecurityLevelSupported(l1Only, deviceLevel = 3))
    }

    @Test
    fun `L3 minimum accepts every device`() {
        val anyLevel = settings(minSecurityLevel = 3)
        assertTrue(WidevinePolicy.isSecurityLevelSupported(anyLevel, deviceLevel = 1))
        assertTrue(WidevinePolicy.isSecurityLevelSupported(anyLevel, deviceLevel = 3))
    }

    @Test
    fun `no minimum, no DRM or unknown device level never refuses`() {
        assertTrue(WidevinePolicy.isSecurityLevelSupported(settings(), deviceLevel = 3))
        val noDrm = settings(drmEnabled = false, minSecurityLevel = 1)
        assertTrue(WidevinePolicy.isSecurityLevelSupported(noDrm, deviceLevel = 3))
        assertTrue(WidevinePolicy.isSecurityLevelSupported(settings(minSecurityLevel = 1), deviceLevel = null))
    }

    @Test
    fun `minimum outside L1 to L3 restricts nothing`() {
        assertTrue(WidevinePolicy.isSecurityLevelSupported(settings(minSecurityLevel = 0), deviceLevel = 3))
        assertTrue(WidevinePolicy.isSecurityLevelSupported(settings(minSecurityLevel = 4), deviceLevel = 3))
    }

    // endregion

    // region maxVideoSize / fits

    @Test
    fun `landscape and unknown shapes are capped on height`() {
        assertEquals(Int.MAX_VALUE to 480, WidevinePolicy.maxVideoSize(480, 1920, 1080))
        assertEquals(Int.MAX_VALUE to 480, WidevinePolicy.maxVideoSize(480, null, null))
        assertEquals(Int.MAX_VALUE to 480, WidevinePolicy.maxVideoSize(480, 0, 0))
    }

    @Test
    fun `portrait video is capped on width`() {
        assertEquals(480 to Int.MAX_VALUE, WidevinePolicy.maxVideoSize(480, 1080, 1920))
    }

    @Test
    fun `renditions are judged by their shorter side`() {
        assertTrue(WidevinePolicy.fits(854, 480, 480))
        assertTrue(WidevinePolicy.fits(480, 854, 480))
        assertTrue(WidevinePolicy.fits(426, 240, 480))
        assertFalse(WidevinePolicy.fits(1280, 720, 480))
        assertFalse(WidevinePolicy.fits(1920, 1080, 480))
    }

    @Test
    fun `renditions with unknown dimensions are kept`() {
        assertTrue(WidevinePolicy.fits(-1, -1, 480))
        assertTrue(WidevinePolicy.fits(-1, 360, 480))
        assertFalse(WidevinePolicy.fits(-1, 720, 480))
    }

    // endregion
}
