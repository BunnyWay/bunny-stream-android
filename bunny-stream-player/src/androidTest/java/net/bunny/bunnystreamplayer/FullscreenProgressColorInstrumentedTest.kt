package net.bunny.bunnystreamplayer

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.bunny.bunnystreamplayer.model.PlayerIconSet
import net.bunny.bunnystreamplayer.ui.fullscreen.FullScreenPlayerActivity
import net.bunny.bunnystreamplayer.ui.widget.BunnyPlayerView
import net.bunny.player.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenProgressColorInstrumentedTest {
    @Test
    fun fullscreenKeepsAutomaticColorSetting() {
        withFullscreenPlayer(autoColor = true, textColor = Color.WHITE) { playerView ->
            assertTrue(playerView.autoProgressTextColor)
        }
    }

    @Test
    fun fullscreenKeepsManualTextColor() {
        withFullscreenPlayer(autoColor = false, textColor = Color.YELLOW) { playerView ->
            assertEquals(Color.YELLOW, playerView.progressTextColor)
        }
    }

    private fun withFullscreenPlayer(
        autoColor: Boolean,
        textColor: Int,
        assertion: (BunnyPlayerView) -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val host = instrumentation.startActivitySync(
            Intent(instrumentation.context, ProgressContrastActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val monitor = Instrumentation.ActivityMonitor(FullScreenPlayerActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        try {
            instrumentation.runOnMainSync {
                FullScreenPlayerActivity.show(
                    host,
                    PlayerIconSet(),
                    autoColor,
                    textColor,
                ) {}
            }
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000L)
                as FullScreenPlayerActivity
            try {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertion(activity.findViewById(R.id.player_view))
                }
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { host.finish() }
        }
    }
}
