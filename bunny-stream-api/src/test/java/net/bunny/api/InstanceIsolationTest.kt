package net.bunny.api

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Two instances have to stay out of each other's way in everything they persist, not just in what
 * they send. TUS keeps its resume state in shared preferences keyed by a fingerprint of the file
 * being uploaded — with one store for the whole process, uploading the same file from two
 * instances would let one resume into the other's library.
 */
class InstanceIsolationTest {

    @Before
    fun clearDefaultInstance() = BunnyStreamApi.release()

    @After
    fun releaseDefaultInstance() = BunnyStreamApi.release()

    @Test
    fun `each library gets its own tus resume store`() {
        val names = mutableListOf<String>()
        val context = fakeContext(names)

        BunnyStreamApi.create(context, KEY_A, LIBRARY_A)
        BunnyStreamApi.create(context, KEY_B, LIBRARY_B)

        assertEquals(2, names.size)
        assertNotEquals(names[0], names[1])
        assertTrue(names[0].endsWith(LIBRARY_A.toString()))
        assertTrue(names[1].endsWith(LIBRARY_B.toString()))
    }

    @Test
    fun `two instances on the same library share one resume store`() {
        // Same library means the same TUS endpoints, so a resumable upload is genuinely the same
        // upload — splitting the store here would restart transfers for no reason.
        val names = mutableListOf<String>()
        val context = fakeContext(names)

        BunnyStreamApi.create(context, KEY_A, LIBRARY_A)
        BunnyStreamApi.create(context, KEY_B, LIBRARY_A)

        assertEquals(names[0], names[1])
    }

    @Test
    fun `releasing the default instance does not touch one you created`() {
        val context = fakeContext()
        BunnyStreamApi.initialize(context, KEY_A, LIBRARY_A)
        val standalone = BunnyStreamApi.create(context, KEY_B, LIBRARY_B)

        BunnyStreamApi.release()

        // Still usable: its repositories and config are its own.
        assertEquals(LIBRARY_B, standalone.libraryId)
        assertEquals(KEY_B, standalone.config.accessKey)
    }

    @Test
    fun `the config does not print the access key`() {
        // Configs reach log lines and crash reports; a data class would print the key verbatim.
        val rendered = BunnyStreamConfig(KEY_A, LIBRARY_A).toString()

        assertTrue(rendered.contains(LIBRARY_A.toString()))
        assertTrue("the key must not appear in full", !rendered.contains(KEY_A))
        assertTrue("nor enough of it to reconstruct", !rendered.contains(KEY_A.substring(8)))
    }

    private fun fakeContext(prefsNames: MutableList<String>? = null): Context {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val name = slot<String>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(capture(name), any()) } answers {
            prefsNames?.add(name.captured)
            preferences
        }
        return context
    }

    private companion object {
        const val KEY_A = "8f2c1a94-0e3d-4b77-9a51-6c8de2f04b13"
        const val KEY_B = "b41e7d62-95af-4c08-8e33-1d7a6f9c2054"
        const val LIBRARY_A = 694192L
        const val LIBRARY_B = 111222L
    }
}
