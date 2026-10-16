package net.bunny.bunnystreamplayer.cmcd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CmcdResolverTest {

    @Test
    fun `requests to the manifest host carry CMCD`() {
        assertTrue(isCmcdHost("vz-1234.b-cdn.net", "vz-1234.b-cdn.net"))
        assertTrue(isCmcdHost("VZ-1234.B-CDN.NET", "vz-1234.b-cdn.net"))
    }

    @Test
    fun `ad creatives from another host do not`() {
        assertFalse(isCmcdHost("redirector.gvt1.com", "vz-1234.b-cdn.net"))
        assertFalse(isCmcdHost(null, "vz-1234.b-cdn.net"))
    }

    @Test
    fun `an unknown manifest host keeps CMCD on everything`() {
        assertTrue(isCmcdHost("vz-1234.b-cdn.net", null))
        assertTrue(isCmcdHost("redirector.gvt1.com", ""))
    }
}
