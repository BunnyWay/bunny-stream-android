package net.bunny.bunnystreamplayer.cast

import android.annotation.SuppressLint
import android.net.Uri
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject

/**
 * Converts Media3 [MediaItem]s to the Cast [MediaInfo] shape the Bunny
 * receiver expects. media3's DefaultMediaItemConverter serializes a
 * Media3-specific JSON payload the Bunny receiver ignores; this converter
 * instead emits the documented Bunny sender contract:
 *
 * - the signed HLS URL as contentId/contentUrl,
 * - video title + poster as standard MediaMetadata (shown on the TV),
 * - sideloaded VTT caption tracks (same VTTs the local player uses),
 * - customData carrying Widevine DRM config + embed theming
 *   ([castCustomData], refreshed by the player on every playVideo).
 */
@SuppressLint("UnsafeOptInUsageError")
class BunnyMediaItemConverter : MediaItemConverter {

    /** Set by the player when a video loads; sent with every LoadRequest. */
    @Volatile
    var castCustomData: JSONObject? = null

    /**
     * Whether the current playback is a live stream — the receiver needs
     * STREAM_TYPE_LIVE to show live UI instead of a broken VOD seek bar.
     * Set by the player alongside the CMCD stream type.
     */
    @Volatile
    var isLiveStream: Boolean = false

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val localConfiguration = requireNotNull(mediaItem.localConfiguration) {
            "MediaItem has no localConfiguration"
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            mediaItem.mediaMetadata.title?.let {
                putString(MediaMetadata.KEY_TITLE, it.toString())
            }
            mediaItem.mediaMetadata.artworkUri?.let { addImage(WebImage(it)) }
        }

        val subtitleTracks = localConfiguration.subtitleConfigurations
            .mapIndexed { index, subtitle ->
                MediaTrack.Builder(index + 1L, MediaTrack.TYPE_TEXT)
                    .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                    .setContentId(subtitle.uri.toString())
                    .setContentType(subtitle.mimeType ?: MimeTypes.TEXT_VTT)
                    .setLanguage(subtitle.language)
                    .setName(subtitle.label ?: subtitle.language)
                    .build()
            }

        val mediaInfo = MediaInfo.Builder(localConfiguration.uri.toString())
            .setStreamType(
                if (isLiveStream) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
            )
            .setContentType(localConfiguration.mimeType ?: MimeTypes.APPLICATION_M3U8)
            .setContentUrl(localConfiguration.uri.toString())
            .setMetadata(metadata)
            .apply {
                if (subtitleTracks.isNotEmpty()) setMediaTracks(subtitleTracks)
                castCustomData?.let { setCustomData(it) }
            }
            .build()

        return MediaQueueItem.Builder(mediaInfo).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media
        val builder = MediaItem.Builder()

        mediaInfo?.let { info ->
            (info.contentUrl ?: info.contentId).let { builder.setUri(Uri.parse(it)) }
            info.contentType?.let { builder.setMimeType(it) }

            val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            info.metadata?.let { metadata ->
                metadata.getString(MediaMetadata.KEY_TITLE)?.let {
                    metadataBuilder.setTitle(it)
                }
                metadata.images.firstOrNull()?.url?.let {
                    metadataBuilder.setArtworkUri(it)
                }
            }
            builder.setMediaMetadata(metadataBuilder.build())
        }

        return builder.build()
    }
}
