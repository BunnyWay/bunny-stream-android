package net.bunny.api.upload.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UploadControl] is the only channel through which a pause button reaches an upload running in
 * someone else's coroutine, so its edge cases are the ones that strand a transfer: a cancel that
 * a paused upload never notices, or a resume that revives a cancelled one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadControlTest {

    @Test
    fun `starts running and uncancelled`() {
        val control = UploadControl()

        assertFalse(control.isPaused)
        assertFalse(control.isCancelled)
    }

    @Test
    fun `pause and resume toggle the flag`() {
        val control = UploadControl()

        control.pause()
        assertTrue(control.isPaused)

        control.resume()
        assertFalse(control.isPaused)
    }

    @Test
    fun `cancel clears a pause so the transfer does not sit in its delay loop`() {
        val control = UploadControl()
        control.pause()

        control.cancel()

        // The TUS loop checks isCancelled at the top of each iteration but sleeps while paused.
        // Leaving the pause flag set would keep it sleeping instead of noticing the cancel.
        assertTrue(control.isCancelled)
        assertFalse(control.isPaused)
    }

    @Test
    fun `cancellation latches - pause and resume are ignored afterwards`() {
        val control = UploadControl()
        control.cancel()

        control.pause()
        control.resume()

        assertTrue(control.isCancelled)
        assertFalse(control.isPaused)
    }

    @Test
    fun `cancel is idempotent`() {
        val control = UploadControl()

        control.cancel()
        control.cancel()

        assertTrue(control.isCancelled)
    }

    @Test
    fun `awaitCancellation resumes once cancelled`() = runTest {
        val control = UploadControl()
        var released = false

        val waiter = launch {
            control.awaitCancellation()
            released = true
        }

        assertFalse(released)
        control.cancel()
        waiter.join()

        assertTrue(released)
    }

    @Test
    fun `awaitCancellation returns immediately when already cancelled`() = runTest {
        val control = UploadControl()
        control.cancel()

        control.awaitCancellation()

        assertTrue(control.isCancelled)
    }
}
