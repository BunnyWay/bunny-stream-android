package net.bunny.bunnystreamplayer

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.bunny.bunnystreamplayer.ui.widget.BunnyPlayerView
import net.bunny.player.R
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** First two seconds are white; the rest have a dark left edge behind the time readout. */
@RunWith(AndroidJUnit4::class)
class ProgressContrastInstrumentedTest {
    @Test
    fun mixedFrameReturnsReadoutToWhite() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.context, ProgressContrastActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ProgressContrastActivity
        try {
            instrumentation.waitForIdleSync()
            awaitColor(activity, Color.BLACK, 10_000L)
            awaitColor(activity, Color.WHITE, 10_000L)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitColor(activity: ProgressContrastActivity, expected: Int, timeoutMs: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var actual = 0
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { actual = activity.playerView.progressTextColor }
            if (actual == expected) return
            SystemClock.sleep(100)
        }
        fail("Expected time-label color $expected, but it stayed $actual")
    }
}

class ProgressContrastActivity : AppCompatActivity() {
    lateinit var playerView: BunnyPlayerView
        private set
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val frame = FrameLayout(this)
        root.addView(
            frame,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.widthPixels * 9 / 16,
            ),
        )
        setContentView(root)
        LayoutInflater.from(this).inflate(R.layout.view_bunny_video_player, frame, true)
        playerView = frame.findViewById(R.id.player_view)
        playerView.controllerShowTimeoutMs = 0
        for (id in listOf(R.id.exo_position, R.id.position_duration_divider, R.id.exo_duration)) {
            playerView.findViewById<TextView>(id).visibility = View.VISIBLE
        }
        playerView.autoProgressTextColor = true

        val clipId = resources.getIdentifier("progress_contrast_mixed", "raw", packageName)
        require(clipId != 0) { "Missing contrast test video" }
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://$packageName/$clipId")))
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        player.prepare()
        player.play()
        playerView.showController()
    }

    override fun onDestroy() {
        playerView.releaseAutoProgressTextColorResources()
        player.release()
        super.onDestroy()
    }
}
