package net.bunny.api.livestream.data

import arrow.core.Either
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bunny.api.api.ManageLiveStreamsApi
import net.bunny.api.livestream.domain.LiveStreamPollResult
import net.bunny.api.livestream.domain.model.LiveStreamCreateRequest
import net.bunny.api.model.LiveStreamStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException
import org.openapitools.client.infrastructure.Success
import org.openapitools.client.models.IngestEndpoints
import org.openapitools.client.models.IngestEndpointsRtmp
import org.openapitools.client.models.LiveStreamModel
import org.openapitools.client.models.LiveStreamPlayDataModel
import org.openapitools.client.models.LiveStreamPlayDataModelLiveStream
import org.openapitools.client.models.LiveStreamStatusModel
import org.openapitools.client.models.PaginationListOfLiveStreamModel
import org.openapitools.client.models.RtmpOutput as GeneratedRtmpOutput
import org.openapitools.client.models.LiveStreamCreateRequest as GeneratedLiveStreamCreateRequest
import java.net.URI

/**
 * Tests for [DefaultLiveStreamRepository]. Focus areas, in priority order:
 *
 *  1. [DefaultLiveStreamRepository.pollLiveStream] HTTP-status-code translation — this is the
 *     polling loop's terminal-vs-transient signal (web spec section 3). If a status code goes
 *     into the wrong bucket the player either gives up too early or hammers the API forever.
 *  2. Friendly error messages from the [Either]-String surface — what the existing CRUD UI
 *     surfaces in toasts. Vocabulary needs to stay stable.
 *  3. DTO → domain mapping — field defaults for nullable proto fields, RtmpOutput's java.net.URI
 *     flattening, parity between the two `toDomain()` mappers (`LiveStreamModel` and the
 *     parallel `LiveStreamPlayDataModelLiveStream`).
 *  4. Mutations: success=false on HTTP 200 must be treated as failure (Bunny quirk), and the
 *     request DTO must carry every field from the domain request.
 *
 * Uses mockk to stub the generated [ManageLiveStreamsApi] (final class — mockk handles those
 * out of the box). The repository's `withContext(coroutineDispatcher)` is fed a
 * [StandardTestDispatcher] so the suspend functions return synchronously under `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLiveStreamRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val api: ManageLiveStreamsApi = mockk()
    private val repo = DefaultLiveStreamRepository(api, dispatcher)

    // region — pollLiveStream: HTTP-status preservation

    @Test fun `pollLiveStream returns Success with domain stream on 2xx`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns liveStreamModel(
            guid = STREAM_ID,
            title = "Hello",
            status = LiveStreamStatus.RUNNING,
        )

        val result = repo.pollLiveStream(LIBRARY_ID, STREAM_ID)

        assertTrue("Expected Success", result is LiveStreamPollResult.Success)
        val stream = (result as LiveStreamPollResult.Success).stream
        assertEquals(STREAM_ID, stream.id)
        assertEquals("Hello", stream.title)
        assertEquals(LiveStreamStatus.RUNNING, stream.status)
    }

    @Test fun `pollLiveStream preserves 401 status code on ClientException`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
            ClientException(message = "nope", statusCode = 401)

        val result = repo.pollLiveStream(LIBRARY_ID, STREAM_ID)
        val failure = result as LiveStreamPollResult.Failure
        assertEquals(401, failure.statusCode)
    }

    @Test fun `pollLiveStream preserves 410 status code on ClientException`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
            ClientException(message = "Gone", statusCode = 410)

        val result = repo.pollLiveStream(LIBRARY_ID, STREAM_ID)
        val failure = result as LiveStreamPollResult.Failure
        assertEquals(410, failure.statusCode)
    }

    @Test fun `pollLiveStream preserves 500 status code on ServerException`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
            ServerException(message = "server broke", statusCode = 500)

        val result = repo.pollLiveStream(LIBRARY_ID, STREAM_ID)
        val failure = result as LiveStreamPollResult.Failure
        assertEquals(500, failure.statusCode)
    }

    @Test fun `pollLiveStream maps generic IOException to statusCode 0`() = runTest(dispatcher) {
        // Transport-level errors — DNS, socket, timeout — don't carry an HTTP status. The poll
        // loop relies on statusCode == 0 to classify these as transient (per spec).
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
            java.net.SocketTimeoutException("timeout")

        val result = repo.pollLiveStream(LIBRARY_ID, STREAM_ID)
        val failure = result as LiveStreamPollResult.Failure
        assertEquals(0, failure.statusCode)
    }

    // endregion

    // region — getLiveStream: Either<String, _> error vocabulary

    @Test fun `getLiveStream returns Right on 2xx`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns liveStreamModel(
            guid = STREAM_ID,
        )

        val result = repo.getLiveStream(LIBRARY_ID, STREAM_ID)
        assertTrue(result is Either.Right)
        assertEquals(STREAM_ID, (result as Either.Right).value.id)
    }

    @Test fun `getLiveStream maps 401 to friendly 'Authorization required' message`() =
        runTest(dispatcher) {
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
                ClientException(message = "ignored", statusCode = 401)

            val result = repo.getLiveStream(LIBRARY_ID, STREAM_ID)
            assertEquals(
                Either.Left("Authorization required Unauthorized"),
                result,
            )
        }

    @Test fun `getLiveStream maps 403 to 'Forbidden'`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
            ClientException(message = "ignored", statusCode = 403)

        assertEquals(Either.Left("Forbidden"), repo.getLiveStream(LIBRARY_ID, STREAM_ID))
    }

    @Test fun `getLiveStream maps 404 to 'Not Found'`() = runTest(dispatcher) {
        every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
            ClientException(message = "ignored", statusCode = 404)

        assertEquals(Either.Left("Not Found"), repo.getLiveStream(LIBRARY_ID, STREAM_ID))
    }

    @Test fun `getLiveStream falls back to upstream message for unmapped status codes`() =
        runTest(dispatcher) {
            // 418 isn't in the special-case list; the upstream message comes through verbatim.
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } throws
                ClientException(message = "I'm a teapot", statusCode = 418)

            assertEquals(
                Either.Left("I'm a teapot"),
                repo.getLiveStream(LIBRARY_ID, STREAM_ID),
            )
        }

    // endregion

    // region — DTO → domain mapping (LiveStreamModel)

    @Test fun `LiveStreamModel toDomain defaults nullable proto fields safely`() =
        runTest(dispatcher) {
            // A nearly-empty response (most fields null) should still produce a non-null domain
            // object with sensible defaults — empty strings for required Strings, false for
            // required Booleans, empty list for rtmpOutputs.
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns
                LiveStreamModel(guid = STREAM_ID)

            val result = repo.getLiveStream(LIBRARY_ID, STREAM_ID)
            val stream = (result as Either.Right).value

            assertEquals(STREAM_ID, stream.id)
            assertEquals("", stream.title) // title was null → empty
            assertEquals(0L, stream.videoLibraryId)
            assertEquals(false, stream.isPublic)
            assertEquals(LiveStreamStatus.UNKNOWN, stream.status) // null status → UNKNOWN
            assertEquals(false, stream.dvrEnabled)
            assertEquals(false, stream.recordVod)
            assertEquals(emptyList<Any>(), stream.rtmpOutputs)
        }

    @Test fun `LiveStreamModel toDomain flattens RtmpOutput URI to String`() =
        runTest(dispatcher) {
            // The OpenAPI generator emits `java.net.URI` for `format: uri` on RtmpOutput.endpoint.
            // The mapper deliberately flattens to String so the domain model stays Android-
            // friendly. Regressing this would force every SDK consumer to depend on `java.net`.
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns liveStreamModel(
                guid = STREAM_ID,
                rtmpOutputs = listOf(
                    GeneratedRtmpOutput(
                        endpoint = URI.create("rtmp://example.test/live"),
                        streamKey = "key-1",
                    ),
                ),
            )

            val stream = (repo.getLiveStream(LIBRARY_ID, STREAM_ID) as Either.Right).value
            assertEquals(1, stream.rtmpOutputs.size)
            val out = stream.rtmpOutputs.single()
            assertEquals("rtmp://example.test/live", out.endpoint)
            assertEquals("key-1", out.streamKey)
        }

    @Test fun `LiveStreamModel toDomain passes through live-spec-relevant fields`() =
        runTest(dispatcher) {
            // The player state resolver reads status / startedAt / recordVod / scheduledStartTime
            // / enableCountdown / preStreamTrailerVideoId. Make sure none of them get dropped on
            // the way through the mapper — otherwise the player will misclassify state.
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns liveStreamModel(
                guid = STREAM_ID,
                status = LiveStreamStatus.SCHEDULED,
                startedAt = "2026-05-29T17:00:00Z",
                recordVod = true,
                scheduledStartTime = "2026-05-30T10:00:00Z",
                enableCountdown = true,
                preStreamTrailerVideoId = "trailer-guid",
                playbackUrlHls = "https://list.test/p.m3u8",
            )

            val stream = (repo.getLiveStream(LIBRARY_ID, STREAM_ID) as Either.Right).value
            assertEquals(LiveStreamStatus.SCHEDULED, stream.status)
            assertEquals("2026-05-29T17:00:00Z", stream.startedAt)
            assertEquals(true, stream.recordVod)
            assertEquals("2026-05-30T10:00:00Z", stream.scheduledStartTime)
            assertEquals(true, stream.enableCountdown)
            assertEquals("trailer-guid", stream.preStreamTrailerVideoId)
            assertEquals("https://list.test/p.m3u8", stream.playbackUrlHls)
        }

    @Test fun `LiveStreamModel toDomain maps primary and backup ingest URLs`() =
        runTest(dispatcher) {
            // The API returns distinct primary/backup RTMP ingest endpoints under
            // ingestEndpoints.rtmp; the mapper must surface both. These used to be dropped
            // (the spec didn't model ingestEndpoints), so the demo showed the same hardcoded
            // URL for both the Primary and Backup badges.
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns liveStreamModel(
                guid = STREAM_ID,
                ingestEndpoints = IngestEndpoints(
                    rtmp = IngestEndpointsRtmp(
                        primaryIngestUrl = "rtmp://global.rtmp.mediadelivery.net/live",
                        backupIngestUrl = "rtmp://global-backup.rtmp.mediadelivery.net/live",
                    ),
                ),
            )

            val stream = (repo.getLiveStream(LIBRARY_ID, STREAM_ID) as Either.Right).value
            assertEquals("rtmp://global.rtmp.mediadelivery.net/live", stream.primaryIngestUrl)
            assertEquals("rtmp://global-backup.rtmp.mediadelivery.net/live", stream.backupIngestUrl)
        }

    @Test fun `LiveStreamModel toDomain leaves ingest URLs null when ingestEndpoints absent`() =
        runTest(dispatcher) {
            every { api.liveStreamGetByStreamId(LIBRARY_ID, STREAM_ID) } returns liveStreamModel(guid = STREAM_ID)

            val stream = (repo.getLiveStream(LIBRARY_ID, STREAM_ID) as Either.Right).value
            assertNull(stream.primaryIngestUrl)
            assertNull(stream.backupIngestUrl)
        }

    // endregion

    // region — fetchLiveStreamPlayData

    @Test fun `fetchLiveStreamPlayData preserves all candidate URLs`() = runTest(dispatcher) {
        // The live player's URL resolver picks videoPlaylistUrl > fallbackUrl > playbackUrlHls.
        // Losing any of these on the wire-to-domain mapping would change resolver behaviour
        // silently.
        every {
            api.liveStreamGetStreamPlayData(LIBRARY_ID, STREAM_ID, null, null)
        } returns LiveStreamPlayDataModel(
            videoPlaylistUrl = "https://pd.test/play.m3u8",
            fallbackUrl = "https://pd.test/fallback.m3u8",
            originalUrl = "https://pd.test/original.m3u8",
            previewUrl = "https://pd.test/preview.m3u8",
        )

        val playData = (
            repo.fetchLiveStreamPlayData(LIBRARY_ID, STREAM_ID) as Either.Right
        ).value
        assertEquals("https://pd.test/play.m3u8", playData.videoPlaylistUrl)
        assertEquals("https://pd.test/fallback.m3u8", playData.fallbackUrl)
        assertEquals("https://pd.test/original.m3u8", playData.originalUrl)
        assertEquals("https://pd.test/preview.m3u8", playData.previewUrl)
    }

    @Test fun `fetchLiveStreamPlayData maps embedded liveStream snapshot`() =
        runTest(dispatcher) {
            // The play-data response carries its own copy of the stream — distinct DTO from the
            // list endpoint's [LiveStreamModel] because of OpenAPI's `oneOf` quirks. The
            // [LiveStreamPlayDataModelLiveStream.toDomain] mapper has to produce the same shape
            // as the [LiveStreamModel] one, since the VM treats either snapshot identically.
            every {
                api.liveStreamGetStreamPlayData(LIBRARY_ID, STREAM_ID, null, null)
            } returns LiveStreamPlayDataModel(
                liveStream = LiveStreamPlayDataModelLiveStream(
                    guid = STREAM_ID,
                    title = "Embedded",
                    status = LiveStreamStatus.RUNNING,
                    recordVod = true,
                    startedAt = "2026-05-29T18:00:00Z",
                ),
            )

            val playData = (
                repo.fetchLiveStreamPlayData(LIBRARY_ID, STREAM_ID) as Either.Right
            ).value
            val embedded = playData.liveStream
            assertEquals(STREAM_ID, embedded?.id)
            assertEquals("Embedded", embedded?.title)
            assertEquals(LiveStreamStatus.RUNNING, embedded?.status)
            assertEquals(true, embedded?.recordVod)
            assertEquals("2026-05-29T18:00:00Z", embedded?.startedAt)
        }

    @Test fun `fetchLiveStreamPlayData forwards token and expires to the API`() =
        runTest(dispatcher) {
            // mockk's `capture` rejects nullable types (`T : Any` bound), and the generated API's
            // token/expires parameters are nullable. Instead of capturing, we wire the mock to
            // match the exact forwarded values and then `verify` the call — same coverage, no
            // captureNullable noise.
            every {
                api.liveStreamGetStreamPlayData(LIBRARY_ID, STREAM_ID, "secret", 1_700_000_000L)
            } returns LiveStreamPlayDataModel()

            repo.fetchLiveStreamPlayData(
                libraryId = LIBRARY_ID,
                streamId = STREAM_ID,
                token = "secret",
                expires = 1_700_000_000L,
            )

            verify(exactly = 1) {
                api.liveStreamGetStreamPlayData(LIBRARY_ID, STREAM_ID, "secret", 1_700_000_000L)
            }
        }

    @Test fun `fetchLiveStreamPlayData defaults missing flags safely`() =
        runTest(dispatcher) {
            // Almost-empty play-data response. The mapper should fill in: enableDRM=false,
            // drmVersion=0, keyColor=WHITE, tokenAuthEnabled=false, rememberPlayerPosition=false,
            // playbackSpeeds parsed from null → safe default.
            every {
                api.liveStreamGetStreamPlayData(LIBRARY_ID, STREAM_ID, null, null)
            } returns LiveStreamPlayDataModel()

            val playData = (
                repo.fetchLiveStreamPlayData(LIBRARY_ID, STREAM_ID) as Either.Right
            ).value
            assertEquals(false, playData.enableDRM)
            assertEquals(0, playData.drmVersion)
            assertEquals(false, playData.tokenAuthEnabled)
            assertEquals(false, playData.rememberPlayerPosition)
            assertEquals(false, playData.enableCompactControls)
            assertNull(playData.fallbackUrl)
            assertNull(playData.videoPlaylistUrl)
        }

    // endregion

    // region — Mutations (createLiveStream / updateLiveStream / deleteLiveStream)

    @Test fun `createLiveStream forwards every field of the domain request to the DTO`() =
        runTest(dispatcher) {
            val request = LiveStreamCreateRequest(
                title = "title",
                description = "desc",
                collectionId = "coll",
                isPublic = true,
                scheduledStartTime = "2026-05-30T10:00:00Z",
                scheduledEndTime = "2026-05-30T11:00:00Z",
                dvrEnabled = true,
                dvrWindowSeconds = 600,
                recordVod = true,
                enableCountdown = true,
                preStreamTrailerVideoId = "trailer",
            )
            val dtoSlot = slot<GeneratedLiveStreamCreateRequest>()
            every {
                api.liveStreamCreate(LIBRARY_ID, capture(dtoSlot))
            } returns liveStreamModel(guid = "created-id")

            val result = repo.createLiveStream(LIBRARY_ID, request)
            assertTrue(result is Either.Right)

            val dto = dtoSlot.captured
            assertEquals("title", dto.title)
            assertEquals("desc", dto.description)
            assertEquals("coll", dto.collectionId)
            assertEquals(true, dto.`public`)
            assertEquals("2026-05-30T10:00:00Z", dto.scheduledStartTime)
            assertEquals("2026-05-30T11:00:00Z", dto.scheduledEndTime)
            assertEquals(true, dto.dvrEnabled)
            assertEquals(600, dto.dvrWindowSeconds)
            assertEquals(true, dto.recordVod)
            assertEquals(true, dto.enableCountdown)
            assertEquals("trailer", dto.preStreamTrailerVideoId)
        }

    @Test fun `updateLiveStream maps 400 ClientException to Left`() =
        runTest(dispatcher) {
            // The official preview API uses PUT and signals validation failures with a 400
            // (the HTTP-200-with-success=false quirk no longer applies to this endpoint).
            every {
                api.liveStreamUpdate(LIBRARY_ID, STREAM_ID, any())
            } throws ClientException(message = "bad input", statusCode = 400)

            val result = repo.updateLiveStream(
                LIBRARY_ID,
                STREAM_ID,
                LiveStreamCreateRequest(title = "x"),
            )
            assertTrue("Expected Left for HTTP 400", result is Either.Left)
        }

    @Test fun `updateLiveStream returns Right(Unit) and discards the echoed model`() =
        runTest(dispatcher) {
            every {
                api.liveStreamUpdate(LIBRARY_ID, STREAM_ID, any())
            } returns liveStreamModel(guid = STREAM_ID)

            val result = repo.updateLiveStream(
                LIBRARY_ID, STREAM_ID, LiveStreamCreateRequest(title = "x"),
            )
            assertEquals(Either.Right(Unit), result)
        }

    @Test fun `startLiveStream maps the returned model to domain`() = runTest(dispatcher) {
        every {
            api.liveStreamStartStream(LIBRARY_ID, STREAM_ID)
        } returns liveStreamModel(guid = STREAM_ID, status = LiveStreamStatus.RUNNING)

        val result = repo.startLiveStream(LIBRARY_ID, STREAM_ID)
        assertTrue(result is Either.Right)
        assertEquals(LiveStreamStatus.RUNNING, (result as Either.Right).value.status)
        verify(exactly = 1) { api.liveStreamStartStream(LIBRARY_ID, STREAM_ID) }
    }

    @Test fun `stopLiveStream maps the returned model to domain`() = runTest(dispatcher) {
        every {
            api.liveStreamStopStream(LIBRARY_ID, STREAM_ID)
        } returns liveStreamModel(guid = STREAM_ID, status = LiveStreamStatus.ENDED)

        val result = repo.stopLiveStream(LIBRARY_ID, STREAM_ID)
        assertTrue(result is Either.Right)
        assertEquals(LiveStreamStatus.ENDED, (result as Either.Right).value.status)
        verify(exactly = 1) { api.liveStreamStopStream(LIBRARY_ID, STREAM_ID) }
    }

    @Test fun `deleteLiveStream returns Right(Unit) and discards the echoed model`() =
        runTest(dispatcher) {
            // The repo uses the *WithHttpInfo variant — the plain liveStreamDelete() NPEs casting
            // the empty 2xx body to a non-null model — and treats any 2xx as success.
            every {
                api.liveStreamDeleteWithHttpInfo(LIBRARY_ID, STREAM_ID)
            } returns Success<LiveStreamModel?>(data = null, statusCode = 200)

            assertEquals(Either.Right(Unit), repo.deleteLiveStream(LIBRARY_ID, STREAM_ID))
            verify(exactly = 1) { api.liveStreamDeleteWithHttpInfo(LIBRARY_ID, STREAM_ID) }
        }

    // endregion

    // region — listLiveStreams pagination wrapper

    @Test fun `listLiveStreams maps pagination metadata and items`() = runTest(dispatcher) {
        every {
            api.liveStreamList(LIBRARY_ID, null, null, null, null, null)
        } returns PaginationListOfLiveStreamModel(
            totalItems = 42L,
            currentPage = 1L,
            itemsPerPage = 10,
            items = listOf(
                liveStreamModel(guid = "id-1", title = "First"),
                liveStreamModel(guid = "id-2", title = "Second"),
            ),
        )

        val list = (repo.listLiveStreams(LIBRARY_ID) as Either.Right).value
        assertEquals(42L, list.totalItems)
        assertEquals(1L, list.currentPage)
        assertEquals(10, list.itemsPerPage)
        assertEquals(2, list.items.size)
        assertEquals("id-1", list.items[0].id)
        assertEquals("First", list.items[0].title)
        assertEquals("id-2", list.items[1].id)
    }

    @Test fun `listLiveStreams handles a fully-empty pagination response`() = runTest(dispatcher) {
        // No null-pointer crashes when the server returns null counts and null items.
        every {
            api.liveStreamList(LIBRARY_ID, null, null, null, null, null)
        } returns PaginationListOfLiveStreamModel()

        val list = (repo.listLiveStreams(LIBRARY_ID) as Either.Right).value
        assertEquals(0L, list.totalItems)
        assertEquals(0L, list.currentPage)
        assertEquals(0, list.itemsPerPage)
        assertTrue("items should be empty when API returned null", list.items.isEmpty())
    }

    @Test fun `listLiveStreams maps 404 to an empty list`() = runTest(dispatcher) {
        // Bunny returns 404 for a library with no live streams yet — that's an empty list, not
        // an error (the iOS demo maps it the same way; the Android demo used to show a toast).
        every {
            api.liveStreamList(LIBRARY_ID, 1, 10, null, null, null)
        } throws ClientException(message = "Not Found", statusCode = 404)

        val result = repo.listLiveStreams(LIBRARY_ID, page = 1, itemsPerPage = 10)

        assertTrue("404 must map to Right(empty list)", result is Either.Right)
        val list = (result as Either.Right).value
        assertEquals(0L, list.totalItems)
        assertTrue(list.items.isEmpty())
    }

    @Test fun `listLiveStreams keeps non-404 client errors as Left`() = runTest(dispatcher) {
        every {
            api.liveStreamList(LIBRARY_ID, null, null, null, null, null)
        } throws ClientException(message = "nope", statusCode = 401)

        assertTrue(repo.listLiveStreams(LIBRARY_ID) is Either.Left)
    }

    // endregion

    // region — getLiveStreamStatus (ingest /status)

    @Test fun `getLiveStreamStatus maps the generated model to domain`() = runTest(dispatcher) {
        every {
            api.liveStreamGetStreamStatus(LIBRARY_ID, STREAM_ID)
        } returns LiveStreamStatusModel(
            readyToStart = true,
            primaryLive = true,
            backupLive = false,
            lastPingAgo = 1234L,
            duration = 60,
        )

        val status = (repo.getLiveStreamStatus(LIBRARY_ID, STREAM_ID) as Either.Right).value
        assertEquals(true, status.readyToStart)
        assertEquals(true, status.primaryLive)
        assertEquals(false, status.backupLive)
        assertEquals(1234L, status.lastPingAgoMs)
        assertEquals(60, status.durationSeconds)
    }

    @Test fun `getLiveStreamStatus defaults null flags to false`() = runTest(dispatcher) {
        every {
            api.liveStreamGetStreamStatus(LIBRARY_ID, STREAM_ID)
        } returns LiveStreamStatusModel()

        val status = (repo.getLiveStreamStatus(LIBRARY_ID, STREAM_ID) as Either.Right).value
        assertEquals(false, status.readyToStart)
        assertEquals(false, status.primaryLive)
        assertEquals(false, status.backupLive)
        assertNull(status.lastPingAgoMs)
        assertNull(status.durationSeconds)
    }

    @Test fun `getLiveStreamStatus maps errors through the shared vocabulary`() = runTest(dispatcher) {
        every {
            api.liveStreamGetStreamStatus(LIBRARY_ID, STREAM_ID)
        } throws ClientException(message = "nope", statusCode = 401)

        val result = repo.getLiveStreamStatus(LIBRARY_ID, STREAM_ID)
        assertEquals(Either.Left("Authorization required Unauthorized"), result)
    }

    // endregion

    // region — Fixtures

    private fun liveStreamModel(
        guid: String = STREAM_ID,
        title: String? = null,
        status: LiveStreamStatus? = null,
        startedAt: String? = null,
        recordVod: Boolean? = null,
        scheduledStartTime: String? = null,
        enableCountdown: Boolean? = null,
        preStreamTrailerVideoId: String? = null,
        playbackUrlHls: String? = null,
        rtmpOutputs: List<GeneratedRtmpOutput>? = null,
        ingestEndpoints: IngestEndpoints? = null,
    ): LiveStreamModel = LiveStreamModel(
        guid = guid,
        title = title,
        status = status,
        startedAt = startedAt,
        recordVod = recordVod,
        scheduledStartTime = scheduledStartTime,
        enableCountdown = enableCountdown,
        preStreamTrailerVideoId = preStreamTrailerVideoId,
        playbackUrlHls = playbackUrlHls,
        rtmpOutputs = rtmpOutputs,
        ingestEndpoints = ingestEndpoints,
    )

    private companion object {
        const val LIBRARY_ID = 1L
        const val STREAM_ID = "stream-guid"
    }

    // endregion
}
