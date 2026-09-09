package net.bunny.bunnystreamcameraupload.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Single-publish reconnect policy — must match the iOS SDK's broadcaster: exponential backoff
 * 1, 2, 4, 8, 8 s and primary <-> backup alternation on every attempt when a backup exists.
 */
class ReconnectPolicyTest {

    @Test
    fun `backoff is exponential capped at 8s`() {
        assertEquals(1_000L, ReconnectPolicy.reconnectDelayMs(1))
        assertEquals(2_000L, ReconnectPolicy.reconnectDelayMs(2))
        assertEquals(4_000L, ReconnectPolicy.reconnectDelayMs(3))
        assertEquals(8_000L, ReconnectPolicy.reconnectDelayMs(4))
        assertEquals(8_000L, ReconnectPolicy.reconnectDelayMs(5))
        assertEquals(8_000L, ReconnectPolicy.reconnectDelayMs(99))
    }

    @Test
    fun `alternates primary and backup when a backup exists`() {
        assertTrue(ReconnectPolicy.nextUsesBackup(currentlyUsingBackup = false, hasBackup = true))
        assertFalse(ReconnectPolicy.nextUsesBackup(currentlyUsingBackup = true, hasBackup = true))
    }

    @Test
    fun `stays on primary when there is no backup`() {
        assertFalse(ReconnectPolicy.nextUsesBackup(currentlyUsingBackup = false, hasBackup = false))
        assertFalse(ReconnectPolicy.nextUsesBackup(currentlyUsingBackup = true, hasBackup = false))
    }

    @Test
    fun `retry budget matches the iOS SDK`() {
        assertEquals(5, ReconnectPolicy.MAX_SINGLE_RETRIES)
    }
}
