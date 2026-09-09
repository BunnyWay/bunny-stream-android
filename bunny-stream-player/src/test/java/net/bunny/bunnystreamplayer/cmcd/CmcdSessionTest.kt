package net.bunny.bunnystreamplayer.cmcd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the CMCD **v2** `CMCD=` query string produced by [CmcdSession] — the CMCD field set is
 * byte-compatible with the iOS `CMCDHeaderBuilder` (`sid`, `cid`, `sf=h`, `st`, `v=2`, `bl`, `su`,
 * `bs`, `ot`; only `sid`/`cid` quoted) and follows CTA-5004 query encoding (single `CMCD=`,
 * alphabetically ordered). Uses the Uri-free helpers so no Android framework (or Robolectric) is
 * needed.
 */
class CmcdSessionTest {

    private val sid = "11111111-1111-1111-1111-111111111111"
    private val cid = "abc-guid"

    private fun session(
        streamType: CmcdStreamType = CmcdStreamType.LIVE,
        streamingFormat: String = "h",
        snapshot: () -> CmcdPlayerSnapshot = { CmcdPlayerSnapshot() },
    ) = CmcdSession(
        contentId = cid,
        streamType = streamType,
        streamingFormat = streamingFormat,
        sessionId = sid,
        snapshotProvider = snapshot,
    )

    @Test
    fun `query value is alphabetically ordered with quoting and v2`() {
        // Manifest, startup, calm buffer -> keys bl,cid,ot,sf,sid,st,su,v (alphabetical), bl=0.
        val s = session(CmcdStreamType.LIVE) { CmcdPlayerSnapshot(0, false) }
        assertEquals(
            "bl=0,cid=\"$cid\",ot=m,sf=h,sid=\"$sid\",st=l,su,v=2",
            s.queryValueForSegment("live.m3u8"),
        )
    }

    @Test
    fun `stream type maps to st l v e`() {
        assertTrue(session(CmcdStreamType.LIVE).queryValueForSegment("live.m3u8").contains(",st=l,"))
        assertTrue(session(CmcdStreamType.VOD).queryValueForSegment("live.m3u8").contains(",st=v,"))
        assertTrue(session(CmcdStreamType.EVENT).queryValueForSegment("live.m3u8").contains(",st=e,"))
    }

    @Test
    fun `streaming format drives sf - default h for HLS, d for DASH`() {
        assertTrue("HLS default", session().queryValueForSegment("live.m3u8").contains(",sf=h,"))
        assertTrue(
            "DASH reports sf=d",
            session(streamingFormat = "d").queryValueForSegment("manifest.mpd").contains(",sf=d,"),
        )
        // A DASH init/media segment on a DASH session still carries sf=d.
        assertTrue(session(streamingFormat = "d").queryValueForSegment("seg1.m4s").contains(",sf=d,"))
    }

    @Test
    fun `bs is present only when the buffer is starved`() {
        assertFalse(session { CmcdPlayerSnapshot(bufferStarved = false) }.queryValueForSegment("live.m3u8").contains("bs"))
        assertTrue(session { CmcdPlayerSnapshot(bufferStarved = true) }.queryValueForSegment("live.m3u8").contains(",bs,"))
    }

    @Test
    fun `buffer length and bs on a starved segment, su drops after the first segment`() {
        val s = session(CmcdStreamType.VOD) { CmcdPlayerSnapshot(bufferLengthMs = 1200, bufferStarved = true) }
        // First (segment) request: su still present, bs present, ot=v.
        assertEquals(
            "bl=1200,bs,cid=\"$cid\",ot=v,sf=h,sid=\"$sid\",st=v,su,v=2",
            s.queryValueForSegment("segment_1.ts"),
        )
        // Second request: su gone.
        assertEquals(
            "bl=1200,bs,cid=\"$cid\",ot=v,sf=h,sid=\"$sid\",st=v,v=2",
            s.queryValueForSegment("segment_2.ts"),
        )
    }

    @Test
    fun `su survives manifests and the first segment then drops`() {
        val s = session { CmcdPlayerSnapshot(bufferLengthMs = 10) }
        assertTrue("master manifest keeps su", s.queryValueForSegment("master.m3u8").contains(",su,"))
        assertTrue("media manifest keeps su", s.queryValueForSegment("chunklist.m3u8").contains(",su,"))
        assertTrue("first segment keeps su", s.queryValueForSegment("seg0.ts").contains(",su,"))
        assertFalse("second segment drops su", s.queryValueForSegment("seg1.ts").contains("su"))
    }

    @Test
    fun `object type is derived from the last path segment`() {
        val s = session()
        assertEquals(CmcdObjectType.MANIFEST, s.objectTypeForSegment("live.m3u8"))
        assertEquals(CmcdObjectType.MANIFEST, s.objectTypeForSegment("manifest.mpd"))
        assertEquals(CmcdObjectType.VIDEO, s.objectTypeForSegment("segment_00042.ts"))
        assertEquals(CmcdObjectType.VIDEO, s.objectTypeForSegment("chunk.m4s"))
        assertEquals(CmcdObjectType.VIDEO, s.objectTypeForSegment("bbb_1920x1080_15.m4v"))
        assertEquals(CmcdObjectType.AUDIO, s.objectTypeForSegment("audio.aac"))
        assertEquals(CmcdObjectType.AUDIO, s.objectTypeForSegment("audio.m4a"))
        assertEquals(CmcdObjectType.INIT, s.objectTypeForSegment("init.mp4"))
        assertEquals(CmcdObjectType.VIDEO, s.objectTypeForSegment("movie.mp4"))
        assertEquals(CmcdObjectType.OTHER, s.objectTypeForSegment("license.key"))
        assertEquals(CmcdObjectType.OTHER, s.objectTypeForSegment(null))
    }

    @Test
    fun `ot is reflected in the query`() {
        // Fresh session per case so startup (su) doesn't cross-contaminate; default st=l avoids a
        // collision with the ot=v token.
        assertTrue(session().queryValueForSegment("live.m3u8").contains(",ot=m,"))
        assertTrue(session().queryValueForSegment("seg.ts").contains(",ot=v,"))
        assertTrue(session().queryValueForSegment("audio.m4a").contains(",ot=a,"))
        assertTrue(session().queryValueForSegment("init.mp4").contains(",ot=i,"))
    }
}
