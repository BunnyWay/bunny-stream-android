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
    fun automaticReadoutRemainsWhiteOnBrightAndMixedFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.context, ProgressContrastActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ProgressContrastActivity
        try {
            instrumentation.waitForIdleSync()
            awaitColorDuring(activity, Color.WHITE, 0L, 2_000L)
            awaitColorDuring(activity, Color.WHITE, 2_500L, 6_000L)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun automaticReadoutRemainsWhiteOnBlackAndIntermediateGrayFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.context, ProgressContrastActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ProgressContrastActivity.EXTRA_CLIP_NAME, "progress_contrast_uniform")
        val activity = instrumentation.startActivitySync(intent) as ProgressContrastActivity
        try {
            instrumentation.waitForIdleSync()
            // The clip holds each color for five seconds; verify the actual playback phase.
            awaitColorDuring(activity, Color.WHITE, 1_500L, 5_000L) // white frame
            awaitColorDuring(activity, Color.WHITE, 6_500L, 10_000L) // black frame
            awaitColorDuring(activity, Color.WHITE, 11_500L, 15_000L) // 60% gray
            awaitColorDuring(activity, Color.WHITE, 16_500L, 20_000L) // 50% gray
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitColorDuring(
        activity: ProgressContrastActivity,
        expected: Int,
        startPositionMs: Long,
        endPositionMs: Long,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 25_000L
        var actual = 0
        var position = 0L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                actual = activity.playerView.progressTextColor
                position = activity.playerPositionMs
            }
            if (position in startPositionMs until endPositionMs && actual == expected) return
            if (position >= endPositionMs) break
            SystemClock.sleep(100)
        }
        fail("Expected color $expected at ${startPositionMs}–${endPositionMs}ms; got $actual at ${position}ms")
    }
}

class ProgressContrastActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CLIP_NAME = "EXTRA_CLIP_NAME"
    }

    lateinit var playerView: BunnyPlayerView
        private set
    private lateinit var player: ExoPlayer
    val playerPositionMs: Long get() = player.currentPosition

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

        val clipName = intent.getStringExtra(EXTRA_CLIP_NAME) ?: "progress_contrast_mixed"
        val clipId = resources.getIdentifier(clipName, "raw", packageName)
        require(clipId != 0) { "Missing contrast test video" }
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://$packageName/$clipId")))
        player.repeatMode = if (clipName == "progress_contrast_uniform") {
            ExoPlayer.REPEAT_MODE_OFF
        } else {
            ExoPlayer.REPEAT_MODE_ONE
        }
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
