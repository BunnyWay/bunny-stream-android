package net.bunny.bunnystreamplayer.livestream

import net.bunny.api.BunnyStreamApi
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Compose builds a view model during composition, which can run before the host app has
 * initialised the SDK — a splash screen that shows the player while credentials are still loading
 * is enough to get there.
 *
 * The view model used to reach for the SDK instance in its constructor, so that ordering crashed
 * the app from inside composition, where nothing could catch it. Construction is now inert and
 * `start()` reports the problem through the same channel every other terminal failure uses.
 */
class LiveStreamPlayerInitOrderTest {

    @Before
    fun clearSdk() = BunnyStreamApi.release()

    @After
    fun releaseSdk() = BunnyStreamApi.release()

    @Test
    fun `building the view model without an initialised SDK does not throw`() {
        val viewModel = BunnyLiveStreamPlayerViewModel()

        // Nothing has been resolved yet, so there is nothing to report either.
        assertNull(viewModel.terminalError.value)
    }

    @Test
    fun `starting without an initialised SDK reports it instead of crashing`() {
        val viewModel = BunnyLiveStreamPlayerViewModel()

        viewModel.start(libraryId = 694192L, streamId = "stream-guid")

        val message = viewModel.terminalError.value
        assertNotNull("the failure has to reach the UI, not the coroutine that tripped it", message)
        assertTrue(message!!.contains("initialize"))
    }
}
