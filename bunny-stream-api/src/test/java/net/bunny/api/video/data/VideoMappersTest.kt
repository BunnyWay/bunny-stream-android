package net.bunny.api.video.data

import net.bunny.api.model.VideoModelStatus
import net.bunny.api.video.domain.model.TranscodingIssue
import net.bunny.api.video.domain.model.TranscodingSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.models.CaptionModel
import org.openapitools.client.models.ChapterModel
import org.openapitools.client.models.IssueCodes
import org.openapitools.client.models.MetaTagModel
import org.openapitools.client.models.MomentModel
import org.openapitools.client.models.PaginationListOfVideoModel
import org.openapitools.client.models.Severity
import org.openapitools.client.models.TranscodingMessageModel
import org.openapitools.client.models.VideoModel

/**
 * The domain mapping is where the SDK stops handing integrators the generator's output, so these
 * tests cover the decisions that mapping makes: what "the API guarantees this" means in practice,
 * how list-shaped strings are split, and how enums whose generated names carry no meaning are
 * resolved.
 */
class VideoMappersTest {

    // region — the two DTOs must not drift apart

    // The drift test that used to live here compared VideoModel.toDomain() against
    // VideoPlayDataModelVideo.toDomain(). openapi-generator 7.24 reuses VideoModel for play
    // data instead of emitting a second copy, so there is one mapper and nothing to drift.


    @Test
    fun `a fully populated video keeps every value`() {
        val domain = VideoModel(
            videoLibraryId = 42L,
            guid = "video-guid",
            title = "Clip",
            description = "A description",
            dateUploaded = "2026-07-01T10:00:00",
            views = 17L,
            isPublic = true,
            length = 120,
            status = VideoModelStatus.FINISHED,
            framerate = 29.97,
            rotation = 90,
            width = 1920,
            height = 1080,
            availableResolutions = "240p,360p,720p",
            outputCodecs = "x264",
            thumbnailCount = 6,
            encodeProgress = 100,
            storageSize = 4_016_825L,
            hasMP4Fallback = true,
            collectionId = "collection-guid",
            thumbnailFileName = "thumbnail.jpg",
            thumbnailBlurhash = "WnDkAUae",
            averageWatchTime = 30L,
            totalWatchTime = 510L,
            category = "documentary",
            jitEncodingEnabled = true,
            hasOriginal = true,
            originalHash = "ABC123",
            hasHighQualityPreview = true,
        ).toDomain()

        assertEquals("video-guid", domain.id)
        assertEquals(42L, domain.videoLibraryId)
        assertEquals("Clip", domain.title)
        assertEquals("A description", domain.description)
        assertEquals("collection-guid", domain.collectionId)
        assertEquals("documentary", domain.category)
        assertEquals("2026-07-01T10:00:00", domain.dateUploaded)
        assertTrue(domain.isPublic)
        assertEquals(VideoModelStatus.FINISHED, domain.status)
        assertEquals(120, domain.lengthSeconds)
        assertEquals(1920, domain.width)
        assertEquals(1080, domain.height)
        assertEquals(29.97, domain.framerate!!, 0.001)
        assertEquals(90, domain.rotation)
        assertTrue(domain.hasMp4Fallback)
        assertTrue(domain.jitEncodingEnabled)
        assertEquals(4_016_825L, domain.storageSizeBytes)
        assertEquals(100, domain.encodeProgress)
        assertTrue(domain.hasOriginal)
        assertEquals("ABC123", domain.originalHash)
        assertTrue(domain.hasHighQualityPreview)
        assertEquals(6, domain.thumbnailCount)
        assertEquals("thumbnail.jpg", domain.thumbnailFileName)
        assertEquals("WnDkAUae", domain.thumbnailBlurhash)
        assertEquals(17L, domain.views)
        assertEquals(30L, domain.averageWatchTimeSeconds)
        assertEquals(510L, domain.totalWatchTimeSeconds)
    }

    // endregion

    // region — "the API guarantees this" defaults

    @Test
    fun `an empty DTO produces a usable video rather than nulls everywhere`() {
        val domain = VideoModel().toDomain()

        // Identity and counters are non-null in the domain model, so a caller never writes `?: 0`.
        assertEquals("", domain.id)
        assertEquals(0L, domain.videoLibraryId)
        assertEquals("", domain.title)
        assertEquals(VideoModelStatus.CREATED, domain.status)
        assertEquals(0, domain.lengthSeconds)
        assertEquals(0L, domain.views)
        assertEquals(0L, domain.storageSizeBytes)
        assertEquals(0, domain.encodeProgress)
        assertTrue(domain.captions.isEmpty())
        assertTrue(domain.chapters.isEmpty())
        assertTrue(domain.moments.isEmpty())
        assertTrue(domain.metaTags.isEmpty())
        assertTrue(domain.transcodingMessages.isEmpty())

        // Genuinely-absent values stay null instead of being invented.
        assertNull(domain.description)
        assertNull(domain.width)
        assertNull(domain.height)
        assertNull(domain.framerate)
        assertNull(domain.thumbnailFileName)
        assertNull(domain.smartGenerateFeatures)
    }

    @Test
    fun `dimensions and framerate are null before transcoding measures them`() {
        // The API reports 0 for "not known yet", which is not a real dimension — surfacing it as
        // 0 would make a layout compute an aspect ratio of NaN.
        val domain = VideoModel(width = 0, height = 0, framerate = 0.0).toDomain()

        assertNull(domain.width)
        assertNull(domain.height)
        assertNull(domain.framerate)
    }

    @Test
    fun `a blank collection id means no collection`() {
        // The list endpoint returns "" for a video that belongs to no collection.
        assertNull(VideoModel(collectionId = "").toDomain().collectionId)
        assertEquals("c-1", VideoModel(collectionId = "c-1").toDomain().collectionId)
    }

    // endregion

    // region — list-shaped strings

    @Test
    fun `resolutions are split so a quality picker does not parse strings`() {
        assertEquals(
            listOf("240p", "360p", "720p", "1080p"),
            VideoModel(availableResolutions = "240p,360p,720p,1080p").toDomain().availableResolutions,
        )
    }

    @Test
    fun `splitting tolerates spacing, blanks and a missing value`() {
        assertEquals(
            listOf("240p", "720p"),
            VideoModel(availableResolutions = " 240p , ,720p, ").toDomain().availableResolutions,
        )
        assertTrue(VideoModel(availableResolutions = null).toDomain().availableResolutions.isEmpty())
        assertTrue(VideoModel(availableResolutions = "").toDomain().availableResolutions.isEmpty())
    }

    @Test
    fun `a single value still yields a one-element list`() {
        assertEquals(listOf("x264"), VideoModel(outputCodecs = "x264").toDomain().outputCodecs)
    }

    // endregion

    // region — nested content

    @Test
    fun `captions chapters moments and tags are mapped with their own names`() {
        val domain = VideoModel(
            captions = listOf(CaptionModel(srclang = "pl", label = "Polski", version = 2)),
            chapters = listOf(ChapterModel(title = "Intro", start = 0, end = 15)),
            moments = listOf(MomentModel(label = "Goal", timestamp = 42)),
            metaTags = listOf(MetaTagModel(property = "campaign", value = "summer")),
        ).toDomain()

        // srclang/start/end/timestamp get clearer names on the way in.
        assertEquals("pl", domain.captions.single().languageCode)
        assertEquals("Polski", domain.captions.single().label)
        assertEquals(0, domain.chapters.single().startSeconds)
        assertEquals(15, domain.chapters.single().endSeconds)
        assertEquals(42, domain.moments.single().timestampSeconds)
        assertEquals("campaign", domain.metaTags.single().property)
    }

    // endregion

    // region — transcoding diagnostics: numbers become names

    @Test
    fun `severity and issue code resolve to named values`() {
        val domain = VideoModel(
            transcodingMessages = listOf(
                TranscodingMessageModel(
                    timeStamp = "2026-07-15T14:10:35Z",
                    level = Severity._2,
                    issueCode = IssueCodes._4,
                    message = "Source video stream has variable framerate",
                    value = "31.56",
                ),
            ),
        ).toDomain()

        val message = domain.transcodingMessages.single()
        assertEquals(TranscodingSeverity.WARNING, message.severity)
        assertEquals(TranscodingIssue.INVALID_FRAMERATE, message.issue)
        assertEquals("31.56", message.value)
        assertEquals("2026-07-15T14:10:35Z", message.timestamp)
    }

    @Test
    fun `an absent severity or issue falls back to undefined instead of crashing`() {
        val domain = VideoModel(
            transcodingMessages = listOf(TranscodingMessageModel(message = "no codes")),
        ).toDomain()

        assertEquals(TranscodingSeverity.UNDEFINED, domain.transcodingMessages.single().severity)
        assertEquals(TranscodingIssue.UNDEFINED, domain.transcodingMessages.single().issue)
    }

    @Test
    fun `an unknown code added server-side maps to undefined rather than throwing`() {
        // A new issue code shipped by Bunny must not crash an app built against this release.
        assertEquals(TranscodingIssue.UNDEFINED, TranscodingIssue.from(99))
        assertEquals(TranscodingSeverity.UNDEFINED, TranscodingSeverity.from(99))
        assertEquals(TranscodingIssue.UNDEFINED, TranscodingIssue.from(null))
    }

    // endregion

    // region — pagination

    @Test
    fun `a page maps its items and counters`() {
        val page = PaginationListOfVideoModel(
            totalItems = 8L,
            currentPage = 2L,
            itemsPerPage = 4,
            items = listOf(VideoModel(guid = "a"), VideoModel(guid = "b")),
        ).toDomain()

        assertEquals(8L, page.totalItems)
        assertEquals(2L, page.currentPage)
        assertEquals(4, page.itemsPerPage)
        assertEquals(listOf("a", "b"), page.items.map { it.id })
    }

    @Test
    fun `an empty page reads as page one with no items`() {
        val page = PaginationListOfVideoModel().toDomain()

        assertEquals(0L, page.totalItems)
        assertEquals(1L, page.currentPage)
        assertTrue(page.items.isEmpty())
    }

    // endregion
}
