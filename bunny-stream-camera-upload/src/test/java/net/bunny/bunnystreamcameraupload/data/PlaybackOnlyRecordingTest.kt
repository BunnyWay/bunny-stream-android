package net.bunny.bunnystreamcameraupload.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bunny.api.BunnyStreamConfig
import net.bunny.api.StreamApi
import net.bunny.api.collection.domain.CollectionRepository
import net.bunny.api.error.BunnyError
import net.bunny.api.error.BunnyResult
import net.bunny.api.livestream.domain.LiveStreamRepository
import net.bunny.api.settings.domain.SettingsRepository
import net.bunny.api.settings.domain.model.PlayerSettings
import net.bunny.api.upload.VideoUploader
import net.bunny.api.video.domain.VideoRepository
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The VOD ingest URL carries the access key itself, so an SDK instance created without one (a
 * playback-only app) cannot record. It has to say so before creating anything: an empty video
 * left in the library, or an ingest URL reading `accessKey=null`, are both worse than a clear no.
 */
class PlaybackOnlyRecordingTest {

    @Test
    fun `recording on a playback-only instance is refused before a video is created`() {
        val repository = DefaultRecordingRepository(
            coroutineDispatcher = Dispatchers.Unconfined,
            sdk = { PlaybackOnlySdk },
        )

        val result = runBlocking { repository.prepareRecording(libraryId = 694192L) }

        val error = (result as? BunnyResult.Err)?.error
        assertTrue("expected InvalidState, was $result", error is BunnyError.InvalidState)
        assertTrue(error!!.isTerminal)
        assertTrue(error.message, error.message.contains("access key"))
    }

    /** Only [config] may be touched: reaching any repository means a request was about to go out. */
    private object PlaybackOnlySdk : StreamApi {
        override val config = BunnyStreamConfig(libraryId = 694192L)
        override val videoRepository: VideoRepository get() = untouched()
        override val collectionRepository: CollectionRepository get() = untouched()
        override val videoUploader: VideoUploader get() = untouched()
        override val tusVideoUploader: VideoUploader get() = untouched()
        override val liveStreamRepository: LiveStreamRepository get() = untouched()
        override val settingsRepository: SettingsRepository get() = untouched()
        override suspend fun fetchPlayerSettings(
            libraryId: Long,
            videoId: String,
            token: String?,
            expires: Long?,
        ): BunnyResult<PlayerSettings> = untouched()
        override fun release() = Unit

        private fun untouched(): Nothing = throw AssertionError("must not be reached without a key")
    }
}
