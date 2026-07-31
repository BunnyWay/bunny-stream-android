package net.bunny.api

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The SDK used to be a process-wide singleton with the library id parked on its companion, so an
 * app could only ever address one library and "initialise again with a different key" meant
 * rewriting state underneath whatever was already running.
 *
 * These tests pin the instance model that replaced it: instances are independent, the default one
 * is just an instance the SDK happens to hold for you, and asking for it before there is one says
 * so instead of throwing a bare NPE.
 */
class BunnyStreamApiTest {

    @Before
    fun clearDefaultInstance() = BunnyStreamApi.release()

    @After
    fun releaseDefaultInstance() = BunnyStreamApi.release()

    // region — config rejects what used to be accepted and quietly broken

    @Test
    fun `a blank access key is rejected at construction`() {
        // 3.x accepted it and every call then failed with 401, far from the cause.
        val error = runCatching { BunnyStreamConfig(accessKey = "  ", libraryId = LIBRARY) }
            .exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("accessKey"))
    }

    @Test
    fun `a library id that is not a real library is rejected`() {
        // -1 was the "unset" sentinel the companion held; 0 is not a library either.
        assertTrue(
            runCatching { BunnyStreamConfig(KEY, libraryId = -1) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { BunnyStreamConfig(KEY, libraryId = 0) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `the api host defaults to Bunny and can be pointed elsewhere`() {
        assertEquals(BuildConfig.BASE_API, BunnyStreamConfig(KEY, LIBRARY).baseApi)
        assertEquals(
            "https://staging.example.net",
            BunnyStreamConfig(KEY, LIBRARY, baseApi = "https://staging.example.net").baseApi,
        )
        assertTrue(
            runCatching { BunnyStreamConfig(KEY, LIBRARY, baseApi = " ") }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    // endregion

    // region — the default instance

    @Test
    fun `asking for the default instance before there is one explains what to do`() {
        val error = runCatching { BunnyStreamApi.getInstance() }.exceptionOrNull()

        // 3.x did `instance!!`, so this surfaced as a bare NPE with no hint at the cause.
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("initialize"))
        assertTrue(error.message!!.contains("create"))
    }

    @Test
    fun `initialize registers a default instance and release drops it`() {
        assertFalse(BunnyStreamApi.isInitialized())

        BunnyStreamApi.initialize(fakeContext(), KEY, LIBRARY)

        assertTrue(BunnyStreamApi.isInitialized())
        assertEquals(LIBRARY, BunnyStreamApi.getInstance().libraryId)

        BunnyStreamApi.release()

        assertFalse(BunnyStreamApi.isInitialized())
        assertTrue(runCatching { BunnyStreamApi.getInstance() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `initializing again replaces the default instance`() {
        val context = fakeContext()
        BunnyStreamApi.initialize(context, KEY, LIBRARY)
        val first = BunnyStreamApi.getInstance()

        BunnyStreamApi.initialize(context, OTHER_KEY, OTHER_LIBRARY)

        assertNotSame(first, BunnyStreamApi.getInstance())
        assertEquals(OTHER_LIBRARY, BunnyStreamApi.getInstance().libraryId)
    }

    // endregion

    // region — instances are independent

    @Test
    fun `two instances address two libraries at once`() {
        val context = fakeContext()

        val first = BunnyStreamApi.create(context, KEY, LIBRARY)
        val second = BunnyStreamApi.create(context, OTHER_KEY, OTHER_LIBRARY)

        assertNotSame(first, second)
        assertEquals(LIBRARY, first.libraryId)
        assertEquals(OTHER_LIBRARY, second.libraryId)
        // Creating the second one must not have rewritten the first, which is what the companion's
        // mutable libraryId did.
        assertEquals(LIBRARY, first.libraryId)
    }

    @Test
    fun `an instance you create yourself is not registered as the default`() {
        val instance = BunnyStreamApi.create(fakeContext(), KEY, LIBRARY)

        assertFalse(BunnyStreamApi.isInitialized())
        assertTrue(runCatching { BunnyStreamApi.getInstance() }.exceptionOrNull() is IllegalStateException)
        assertEquals(LIBRARY, instance.libraryId)
    }

    @Test
    fun `releasing one instance leaves the others alone`() {
        val context = fakeContext()
        BunnyStreamApi.initialize(context, KEY, LIBRARY)
        val standalone = BunnyStreamApi.create(context, OTHER_KEY, OTHER_LIBRARY)

        standalone.release()

        // The default instance is untouched: release is per-instance, not process-wide.
        assertTrue(BunnyStreamApi.isInitialized())
        assertEquals(LIBRARY, BunnyStreamApi.getInstance().libraryId)
    }

    @Test
    fun `each instance carries its own repositories`() {
        val context = fakeContext()

        val first = BunnyStreamApi.create(context, KEY, LIBRARY)
        val second = BunnyStreamApi.create(context, OTHER_KEY, OTHER_LIBRARY)

        assertNotSame(first.videoRepository, second.videoRepository)
        assertNotSame(first.liveStreamRepository, second.liveStreamRepository)
        assertNotSame(first.videoUploader, second.videoUploader)
        // Within one instance the same repository is handed out every time, so a caller holding it
        // keeps working.
        assertSame(first.videoRepository, first.videoRepository)
    }

    // endregion

    private fun fakeContext(): Context {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
        return context
    }

    private companion object {
        const val KEY = "8f2c1a94-0e3d-4b77-9a51-6c8de2f04b13"
        const val OTHER_KEY = "b41e7d62-95af-4c08-8e33-1d7a6f9c2054"
        const val LIBRARY = 694192L
        const val OTHER_LIBRARY = 111222L
    }
}
