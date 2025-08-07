package net.bunny.android.demo.settings

import android.content.SharedPreferences

class LocalPrefs(private val prefs: SharedPreferences) {

    companion object {
        private const val ACCESS_KEY = "accessKey"
        private const val LIBRARY_ID = "libraryId"
    }

    var accessKey: String
        set(value) {
            prefs.edit().putString(ACCESS_KEY, value).apply()
        }
        get() = prefs.getString(ACCESS_KEY, "") ?: ""

    var libraryId: Long
        set(value) {
            prefs.edit().putLong(LIBRARY_ID, value).apply()
        }
        get() = prefs.getLong(LIBRARY_ID, -1)
}