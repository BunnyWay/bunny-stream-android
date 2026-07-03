package net.bunny.android.demo.settings

import android.content.SharedPreferences

/**
 * Remembers, per live stream, whether the broadcast should dual-publish (primary + backup at once).
 *
 * Dual-publish is a client-side broadcast option, not a property Bunny stores on the stream, so the
 * demo keeps the choice locally keyed by stream id: the editor sets it on create/save, and
 * [net.bunny.android.demo.recording.GoLiveActivity] reads it when going live to that stream.
 */
class DualPublishPreferences(private val prefs: SharedPreferences) {

    fun isDualPublish(streamId: String): Boolean = prefs.getBoolean(streamId, false)

    fun setDualPublish(streamId: String, enabled: Boolean) {
        prefs.edit().putBoolean(streamId, enabled).apply()
    }
}
