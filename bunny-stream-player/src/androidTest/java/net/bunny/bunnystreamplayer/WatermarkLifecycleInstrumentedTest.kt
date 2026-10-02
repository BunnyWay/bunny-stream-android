package net.bunny.bunnystreamplayer

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.bunny.bunnystreamplayer.model.PlayerWatermark
import net.bunny.bunnystreamplayer.ui.widget.BunnyPlayerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatermarkLifecycleInstrumentedTest {
    // The URL need not resolve: these tests exercise overlay attachment and request cleanup.
    private val logo = PlayerWatermark("https://example.invalid/logo.png")

    @Test
    fun watermarkReturnsAfterReattachingSamePlayerView() = withPlayerView { instrumentation, view ->
        lateinit var firstImage: ImageView
        lateinit var parent: ViewGroup
        var position = -1
        lateinit var params: ViewGroup.LayoutParams
        instrumentation.runOnMainSync {
            view.watermark = logo
            firstImage = requireWatermarkImage(view)
            assertSame(view, firstImage.parent)
            parent = view.parent as ViewGroup
            position = parent.indexOfChild(view)
            params = view.layoutParams
            parent.removeView(view)
            assertTrue(!view.isAttachedToWindow)
            assertNull(firstImage.parent)
            assertNull(watermarkImage(view))
            parent.addView(view, position, params)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertTrue(view.isAttachedToWindow)
            val restored = requireWatermarkImage(view)
            assertNotSame(firstImage, restored)
            assertSame(view, restored.parent)
        }
    }

    @Test
    fun watermarkReturnsAfterClearingAndSettingAgain() = withPlayerView { instrumentation, view ->
        instrumentation.runOnMainSync {
            view.watermark = logo
            val firstImage = requireWatermarkImage(view)
            assertSame(view, firstImage.parent)

            view.watermark = null
            assertNull(firstImage.parent)
            assertNull(watermarkImage(view))

            view.watermark = logo
            val restored = requireWatermarkImage(view)
            assertNotSame(firstImage, restored)
            assertSame(view, restored.parent)
        }
    }

    @Test
    fun watermarkSetWhileDetachedAppearsOnAttach() = withPlayerView { instrumentation, view ->
        instrumentation.runOnMainSync {
            val parent = view.parent as ViewGroup
            val position = parent.indexOfChild(view)
            val params = view.layoutParams
            parent.removeView(view)
            assertTrue(!view.isAttachedToWindow)

            view.watermark = logo
            assertNull(watermarkImage(view))

            parent.addView(view, position, params)
            assertSame(view, requireWatermarkImage(view).parent)
        }
    }

    @Test
    fun configurationChangedWhileDetachedIsAppliedOnAttach() = withPlayerView { instrumentation, view ->
        instrumentation.runOnMainSync {
            view.watermark = logo
            val parent = view.parent as ViewGroup
            val position = parent.indexOfChild(view)
            val params = view.layoutParams
            parent.removeView(view)

            view.watermark = logo.copy(position = PlayerWatermark.Position.BOTTOM_LEADING, opacity = 0.4f)
            assertNull(watermarkImage(view))

            parent.addView(view, position, params)
            val restored = requireWatermarkImage(view)
            assertSame(view, restored.parent)
            assertEquals(0.4f, restored.alpha, 0f)
            assertEquals(Gravity.BOTTOM or Gravity.START, (restored.layoutParams as FrameLayout.LayoutParams).gravity)
        }
    }

    @Test
    fun finishingActivityWithWatermarkDoesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = startActivity(instrumentation)
        instrumentation.runOnMainSync {
            val view = activity.playerView
            view.watermark = logo
            assertSame(view, requireWatermarkImage(view).parent)
            activity.finish()
        }
        awaitDestroyed(instrumentation, activity)
    }

    private fun withPlayerView(check: (Instrumentation, BunnyPlayerView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = startActivity(instrumentation)
        try {
            instrumentation.runOnMainSync { activity.playerView.layoutTransition = null }
            check(instrumentation, activity.playerView)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            awaitDestroyed(instrumentation, activity)
        }
    }

    private fun startActivity(instrumentation: Instrumentation): ProgressContrastActivity {
        val intent = Intent(instrumentation.context, ProgressContrastActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ProgressContrastActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertTrue(activity.playerView.isAttachedToWindow) }
        return activity
    }

    private fun awaitDestroyed(instrumentation: Instrumentation, activity: ProgressContrastActivity) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (!activity.isDestroyed && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (!activity.isDestroyed) SystemClock.sleep(50L)
        }
        assertTrue("Activity did not reach destroyed state", activity.isDestroyed)
    }

    private fun requireWatermarkImage(view: BunnyPlayerView): ImageView =
        requireNotNull(watermarkImage(view)) { "Watermark overlay was not created" }

    private fun watermarkImage(view: BunnyPlayerView): ImageView? {
        val field = BunnyPlayerView::class.java.getDeclaredField("watermarkView")
        field.isAccessible = true
        return field.get(view) as ImageView?
    }
}
