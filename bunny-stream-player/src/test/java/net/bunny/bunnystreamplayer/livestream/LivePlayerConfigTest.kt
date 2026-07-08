package net.bunny.bunnystreamplayer.livestream

import net.bunny.api.settings.domain.model.PlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the [LivePlayerConfig] -> controls-token-string mapping ([toControlsString]) and that the
 * resulting string drives the expected [PlayerSettings] flags. This is the contract the live player
 * relies on to translate a programmatic config into the engine's control model.
 */
class LivePlayerConfigTest {

    private fun settingsFor(config: LivePlayerConfig) =
        PlayerSettings(
            thumbnailUrl = "",
            controls = config.toControlsString(),
            keyColor = 0,
            captionsFontSize = 0,
            captionsFontColor = null,
            captionsBackgroundColor = null,
            uiLanguage = "",
            showHeatmap = false,
            fontFamily = "",
            playbackSpeeds = listOf(1f),
            drmEnabled = false,
            vastTagUrl = null,
            videoUrl = "",
            seekPath = "",
            captionsPath = "",
        )

    @Test
    fun `defaults enable the standard live control set`() {
        val s = settingsFor(LivePlayerConfig())

        assertTrue(s.bigPlayButtonEnabled)
        assertTrue(s.playButtonEnabled)
        assertTrue(s.progressEnabled)
        assertTrue(s.durationEnabled)
        assertTrue(s.currentTimeEnabled)
        assertTrue(s.muteEnabled)
        assertTrue(s.volumeEnabled)
        assertTrue(s.fullScreenEnabled)
        assertTrue(s.settingsEnabled)
        assertTrue(s.pipEnabled)
        assertTrue(s.castButtonEnabled)
        // AirPlay defaults off and is a no-op on Android.
        assertFalse(s.airPlayEnabled)
    }

    @Test
    fun `disabling dvr hides the progress bar even when progress is on`() {
        val s = settingsFor(
            LivePlayerConfig(controls = LiveControls(progress = true, dvr = false)),
        )
        assertFalse("progress requires dvr for live", s.progressEnabled)
    }

    @Test
    fun `progress stays enabled when both progress and dvr are on`() {
        val s = settingsFor(
            LivePlayerConfig(controls = LiveControls(progress = true, dvr = true)),
        )
        assertTrue(s.progressEnabled)
    }

    @Test
    fun `volume implies the mute control`() {
        val s = settingsFor(
            LivePlayerConfig(controls = LiveControls(mute = false, volume = true)),
        )
        assertTrue("volume shares the mute affordance on mobile", s.muteEnabled)
        assertTrue(s.volumeEnabled)
    }

    @Test
    fun `everything off yields an empty control set`() {
        val s = settingsFor(
            LivePlayerConfig(
                controls = LiveControls(
                    bigPlayButton = false,
                    livePlayPause = false,
                    progress = false,
                    duration = false,
                    mute = false,
                    volume = false,
                    fullScreen = false,
                    settings = false,
                    pip = false,
                    chromecast = false,
                    airplay = false,
                    dvr = false,
                ),
            ),
        )
        assertEquals("", s.controls)
        assertFalse(s.playButtonEnabled)
        assertFalse(s.fullScreenEnabled)
    }

    @Test
    fun `airplay token is emitted for config fidelity`() {
        val s = settingsFor(
            LivePlayerConfig(controls = LiveControls(airplay = true)),
        )
        assertTrue(s.airPlayEnabled)
    }

    // region — liveControlsFor (DVR-aware timeline gating, bug 4)

    private fun settingsForControls(controls: String) =
        settingsFor(LivePlayerConfig()).copy(controls = controls)

    @Test
    fun `liveControlsFor is a no-op when dvr is enabled`() {
        val base = "play-large,play,progress,current-time,duration,mute,fullscreen"
        assertEquals(base, liveControlsFor(base, dvrEnabled = true))
    }

    @Test
    fun `liveControlsFor drops only the timeline tokens when dvr is off`() {
        val base = "play-large,play,progress,current-time,duration,mute,fullscreen"
        assertEquals("play-large,play,mute,fullscreen", liveControlsFor(base, dvrEnabled = false))
    }

    @Test
    fun `non-dvr live hides the scrub bar and time counter but keeps the essentials`() {
        val s = settingsForControls(liveControlsFor(LivePlayerConfig().toControlsString(), dvrEnabled = false))

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
        val s = settingsForControls(liveControlsFor(LivePlayerConfig().toControlsString(), dvrEnabled = true))

        assertTrue(s.progressEnabled)
        assertTrue(s.currentTimeEnabled)
        assertTrue(s.durationEnabled)
    }
}
