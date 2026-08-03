package net.bunny.bunnystreamplayer.cast

import android.util.Log
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import net.bunny.bunnystreamplayer.context.AppCastContext

/**
 * A receiver media track, decoupled from the GMS [MediaTrack] class so the
 * matching logic stays pure and unit-testable.
 */
data class RemoteTrack(
    val id: Long,
    val isAudio: Boolean,
    val language: String?,
    val name: String?,
)

/**
 * Receiver track IDs are assigned by the receiver from its own manifest
 * parse (plus the sender's sideloaded caption tracks), so local track order
 * need not match. Matching mirrors the web sender: language exact →
 * language primary subtag → name.
 */
object CastTrackMatcher {

    fun pickTrack(
        tracks: List<RemoteTrack>,
        audio: Boolean,
        language: String?,
        name: String?,
    ): RemoteTrack? {
        val candidates = tracks.filter { it.isAudio == audio }
        if (candidates.isEmpty()) return null

        val wantedLanguage = language?.trim()?.lowercase().takeUnless { it.isNullOrEmpty() }
        if (wantedLanguage != null) {
            candidates.firstOrNull { it.language?.lowercase() == wantedLanguage }
                ?.let { return it }

            val wantedPrimary = wantedLanguage.substringBefore('-')
            candidates.firstOrNull {
                it.language?.lowercase()?.substringBefore('-') == wantedPrimary
            }?.let { return it }
        }

        val wantedName = name?.trim()?.lowercase().takeUnless { it.isNullOrEmpty() }
        if (wantedName != null) {
            candidates.firstOrNull { it.name?.trim()?.lowercase() == wantedName }
                ?.let { return it }
        }

        return null
    }

    /**
     * Build the replacement active-track-id list. The request replaces the
     * whole active set, so active tracks of the other type must be
     * preserved (switching audio must not turn captions off and vice
     * versa). Passing a null [newTrackId] deactivates the type entirely
     * (captions off).
     */
    fun buildActiveTrackIds(
        tracks: List<RemoteTrack>,
        currentActiveIds: List<Long>,
        replaceAudio: Boolean,
        newTrackId: Long?,
    ): LongArray {
        val replacedTypeIds = tracks.filter { it.isAudio == replaceAudio }.map { it.id }.toSet()
        val preserved = currentActiveIds.filter { it !in replacedTypeIds }
        return (preserved + listOfNotNull(newTrackId)).toLongArray()
    }
}

/**
 * Applies local track/rate selections to the active cast session via the
 * standard media commands — the same messages the receiver gets from every
 * other sender platform, so it needs no custom handling.
 */
object CastTrackBridge {

    private const val TAG = "CastTrackBridge"

    private fun remoteMediaClient(): RemoteMediaClient? = try {
        AppCastContext.getOrNull()?.sessionManager?.currentCastSession?.remoteMediaClient
    } catch (e: Exception) {
        Log.w(TAG, "Cast session unavailable: ${e.message}")
        null
    }

    private fun remoteTracks(client: RemoteMediaClient): List<RemoteTrack> =
        client.mediaStatus?.mediaInfo?.mediaTracks.orEmpty().mapNotNull { track ->
            when (track.type) {
                MediaTrack.TYPE_AUDIO -> RemoteTrack(track.id, true, track.language, track.name)
                MediaTrack.TYPE_TEXT -> RemoteTrack(track.id, false, track.language, track.name)
                else -> null
            }
        }

    /** Switch the receiver's audio track to the given language/label. */
    fun selectAudioTrack(language: String?, name: String?): Boolean {
        val client = remoteMediaClient() ?: return false
        val tracks = remoteTracks(client)
        val target = CastTrackMatcher.pickTrack(tracks, audio = true, language, name)
            ?: return false

        val activeIds = client.mediaStatus?.activeTrackIds?.toList().orEmpty()
        if (target.id in activeIds) return true

        client.setActiveMediaTracks(
            CastTrackMatcher.buildActiveTrackIds(tracks, activeIds, replaceAudio = true, target.id),
        )
        return true
    }

    /** Switch the receiver's caption track; null language turns captions off. */
    fun selectTextTrack(language: String?): Boolean {
        val client = remoteMediaClient() ?: return false
        val tracks = remoteTracks(client)

        val target = language?.let {
            CastTrackMatcher.pickTrack(tracks, audio = false, it, name = null) ?: return false
        }

        val activeIds = client.mediaStatus?.activeTrackIds?.toList().orEmpty()
        if (target != null && target.id in activeIds) return true

        client.setActiveMediaTracks(
            CastTrackMatcher.buildActiveTrackIds(tracks, activeIds, replaceAudio = false, target?.id),
        )
        return true
    }
}
