package net.bunnystream.android.demo.settings

import android.content.SharedPreferences
import net.bunnystream.api.playback.ResumeConfig

class ResumePositionPreferences(private val prefs: SharedPreferences) {

    companion object {
        private const val RESUME_ENABLED = "resumeEnabled"
        private const val RETENTION_DAYS = "retentionDays"
        private const val MINIMUM_WATCH_TIME = "minimumWatchTime"
        private const val RESUME_THRESHOLD = "resumeThreshold"
        private const val NEAR_END_THRESHOLD = "nearEndThreshold"
    }

    var resumeEnabled: Boolean
        set(value) {
            prefs.edit().putBoolean(RESUME_ENABLED, value).apply()
        }
        get() = prefs.getBoolean(RESUME_ENABLED, true)

    var retentionDays: Int
        set(value) {
            prefs.edit().putInt(RETENTION_DAYS, value).apply()
        }
        get() = prefs.getInt(RETENTION_DAYS, 7)

    var minimumWatchTime: Long
        set(value) {
            prefs.edit().putLong(MINIMUM_WATCH_TIME, value).apply()
        }
        get() = prefs.getLong(MINIMUM_WATCH_TIME, 30_000L)

    var resumeThreshold: Float
        set(value) {
            prefs.edit().putFloat(RESUME_THRESHOLD, value).apply()
        }
        get() = prefs.getFloat(RESUME_THRESHOLD, 0.05f)

    var nearEndThreshold: Float
        set(value) {
            prefs.edit().putFloat(NEAR_END_THRESHOLD, value).apply()
        }
        get() = prefs.getFloat(NEAR_END_THRESHOLD, 0.95f)

    fun getResumeConfig(): ResumeConfig {
        return ResumeConfig(
            retentionDays = retentionDays,
            minimumWatchTime = minimumWatchTime,
            resumeThreshold = resumeThreshold,
            nearEndThreshold = nearEndThreshold
        )
    }

    fun isResumeEnabled(): Boolean = resumeEnabled
}
