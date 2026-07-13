package net.bunny.bunnystreamplayer.livestream

import net.bunny.api.livestream.domain.model.LiveStreamPlayData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the server-driven live customization mapping ([livePlayerSettings]): the dashboard's
 * `/play` payload (controls, colour, font, language, captions styling) drives the synthetic
 * [net.bunny.api.settings.domain.model.PlayerSettings] the engine consumes, with SDK defaults
 * when play-data is missing — mirroring the iOS SDK's `resolvedTheme` / `resolvedConfig`.
 */
class LivePlayerSettingsTest {

    private fun playData(
        controls: String = "play-large,play,progress,current-time,duration,mute,settings,fullscreen",
        keyColor: Int = 0xFF123456.toInt(),
        uiLanguage: String? = "de",
        fontFamily: String? = "Rubik",
        showHeatmap: Boolean = true,
        enableCompactControls: Boolean = true,
    ) = LiveStreamPlayData(
        liveStream = null,
        libraryName = null,
        captionsPath = "captions/path",
        seekPath = "seek/path",
        thumbnailUrl = "https://cdn/thumb.jpg",
        fallbackUrl = null,
        videoPlaylistUrl = null,
        originalUrl = null,
        previewUrl = null,
        controls = controls,
        enableDRM = false,
        drmVersion = 0,
        keyColor = keyColor,
        vastTagUrl = null,
        captionsFontSize = 18,
        captionsFontColor = 0xFFFFFFFF.toInt(),
        captionsBackgroundColor = 0x80000000.toInt(),
        uiLanguage = uiLanguage,
        allowEarlyPlay = false,
        tokenAuthEnabled = false,
        enableMP4Fallback = false,
        showHeatmap = showHeatmap,
        fontFamily = fontFamily,
        playbackSpeeds = listOf(0.5f, 1f, 2f),
        widevineMinClientSecurityLevel = null,
        zoneTier = null,
        rememberPlayerPosition = false,
        enableCompactControls = enableCompactControls,
    )

    @Test
    fun `null play-data falls back to the default live control set`() {
        val s = livePlayerSettings(playData = null, hlsUrl = "https://x/playlist.m3u8", dvrEnabled = true)

        assertEquals(DEFAULT_LIVE_CONTROLS, s.controls)
        assertTrue(s.playButtonEnabled)
        assertTrue(s.progressEnabled)
        assertTrue(s.muteEnabled)
        assertTrue(s.settingsEnabled)
        assertTrue(s.pipEnabled)
        assertTrue(s.fullScreenEnabled)
        assertTrue(s.castButtonEnabled)
        assertEquals(0, s.keyColor)
        assertEquals("", s.uiLanguage)
        assertEquals("", s.fontFamily)
        assertFalse(s.showHeatmap)
        assertEquals("https://x/playlist.m3u8", s.videoUrl)
    }

    @Test
    fun `server customization is honored verbatim`() {
        val s = livePlayerSettings(playData = playData(), hlsUrl = "https://x/live.m3u8", dvrEnabled = true)

        assertEquals(0xFF123456.toInt(), s.keyColor)
        assertEquals("de", s.uiLanguage)
        assertEquals("Rubik", s.fontFamily)
        assertTrue(s.showHeatmap)
        assertEquals(18, s.captionsFontSize)
        assertEquals(0xFFFFFFFF.toInt(), s.captionsFontColor)
        assertEquals(0x80000000.toInt(), s.captionsBackgroundColor)
        assertEquals("seek/path", s.seekPath)
        assertEquals("captions/path", s.captionsPath)
        // Server controls pass through untouched for a DVR stream.
        assertTrue(s.progressEnabled)
        assertTrue(s.currentTimeEnabled)
        assertTrue(s.durationEnabled)
    }

    @Test
    fun `blank server controls fall back to the default set`() {
        val s = livePlayerSettings(playData = playData(controls = ""), hlsUrl = "u", dvrEnabled = true)
        assertEquals(DEFAULT_LIVE_CONTROLS, s.controls)
    }

    @Test
    fun `live playback speeds stay pinned regardless of the server list`() {
        val s = livePlayerSettings(playData = playData(), hlsUrl = "u", dvrEnabled = false)
        assertEquals(listOf(1.0f), s.playbackSpeeds)
    }

    @Test
    fun `enableSubtitles appends the captions token once`() {
        val s = livePlayerSettings(
            playData = playData(controls = "play,captions"),
            hlsUrl = "u",
            dvrEnabled = true,
            enableSubtitles = true,
        )
        assertEquals("play,captions", s.controls)

        val appended = livePlayerSettings(
            playData = playData(controls = "play"),
            hlsUrl = "u",
            dvrEnabled = true,
            enableSubtitles = true,
        )
        assertEquals("play,captions", appended.controls)
    }

    // region — liveControlsFor (DVR-aware timeline gating)

    @Test
    fun `liveControlsFor is a no-op when dvr is enabled`() {
        val base = "play-large,play,progress,current-time,duration,mute,fullscreen"
        assertEquals(base, liveControlsFor(base, dvrEnabled = true))
    }

    @Test
    fun `liveControlsFor drops the timeline tokens when dvr is off`() {
        val base = "play-large,play,progress,current-time,duration,mute,fullscreen"
        assertEquals("play-large,play,mute,fullscreen", liveControlsFor(base, dvrEnabled = false))
    }

    @Test
    fun `liveControlsFor also drops seek buttons from a web-style server control set`() {
        // Dashboard control strings can carry the web player's rewind/fast-forward — meaningless
        // without a timeline, so a non-DVR live drops them too.
        val web = "play-large,play,rewind,fast-forward,progress,current-time,duration,mute,settings"
        assertEquals(
            "play-large,play,mute,settings",
            liveControlsFor(web, dvrEnabled = false),
        )
    }

    @Test
    fun `non-dvr live hides the scrub bar and time counter but keeps the essentials`() {
        val s = livePlayerSettings(playData = null, hlsUrl = "u", dvrEnabled = false)

        assertFalse("no scrub bar without DVR", s.progressEnabled)
        assertFalse("no position readout without DVR", s.currentTimeEnabled)
        assertFalse("no duration readout without DVR", s.durationEnabled)

        assertTrue(s.playButtonEnabled)
        assertTrue(s.muteEnabled)
        assertTrue(s.fullScreenEnabled)
        assertTrue(s.settingsEnabled)
        assertTrue(s.castButtonEnabled)
    }

    @Test
    fun `dvr live keeps the scrub bar and time counter`() {
        val s = livePlayerSettings(playData = null, hlsUrl = "u", dvrEnabled = true)

        assertTrue(s.progressEnabled)
        assertTrue(s.currentTimeEnabled)
        assertTrue(s.durationEnabled)
    }

    @Test
    fun `ended stream's recording keeps the full timeline regardless of dvr`() {
        // Live→VOD hand-off: the recording is a fully seekable VOD, so the timeline must NOT be
        // stripped even though the stream itself had no DVR.
        val s = livePlayerSettings(
            playData = null,
            hlsUrl = "https://vod.test/recording.m3u8",
            dvrEnabled = false,
            isVodRecording = true,
        )

        assertTrue("recording must keep the scrub bar", s.progressEnabled)
        assertTrue("recording must keep the position readout", s.currentTimeEnabled)
        assertTrue("recording must keep the duration readout", s.durationEnabled)
    }

    @Test
    fun `compact flag rides on play-data not on settings`() {
        // enableCompactControls is a view-level concern forwarded by playLiveUrl; assert it's
        // present on the domain model the surface reads.
        assertTrue(playData(enableCompactControls = true).enableCompactControls)
        assertFalse(playData(enableCompactControls = false).enableCompactControls)
    }
}
