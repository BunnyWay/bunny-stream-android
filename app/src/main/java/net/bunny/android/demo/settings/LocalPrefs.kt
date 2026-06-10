package net.bunny.android.demo.settings

import android.content.SharedPreferences

class LocalPrefs(private val prefs: SharedPreferences) {

    companion object {
        private const val ACCESS_KEY = "accessKey"
        private const val LIBRARY_ID = "libraryId"

        const val DEFAULT_LIBRARY_ID: Long = 661704L
        const val DEFAULT_ACCESS_KEY: String = "74a52dcd-eae4-494d-9517ce413220-7518-49e2"
    }

    var accessKey: String
        set(value) {
            prefs.edit().putString(ACCESS_KEY, value).apply()
        }
        get() = prefs.getString(ACCESS_KEY, DEFAULT_ACCESS_KEY) ?: DEFAULT_ACCESS_KEY

    var libraryId: Long
        set(value) {
            prefs.edit().putLong(LIBRARY_ID, value).apply()
        }
        get() = prefs.getLong(LIBRARY_ID, DEFAULT_LIBRARY_ID)
}