package net.bunny.android.demo.settings

import android.content.SharedPreferences
import net.bunny.android.demo.BuildConfig

class LocalPrefs(private val prefs: SharedPreferences) {

    companion object {
        private const val ACCESS_KEY = "accessKey"
        private const val LIBRARY_ID = "libraryId"
        private const val TOKEN_AUTH_KEY = "tokenAuthKey"

        // Sourced from BuildConfig: dev convenience values in debug/staging,
        // empty in release so no API key is shipped. Set them in local.properties
        // (bunny.demo.accessKey / bunny.demo.libraryId / bunny.demo.tokenAuthKey)
        // or via env vars.
        val DEFAULT_LIBRARY_ID: Long = BuildConfig.DEMO_LIBRARY_ID
        val DEFAULT_ACCESS_KEY: String = BuildConfig.DEMO_ACCESS_KEY
        val DEFAULT_TOKEN_AUTH_KEY: String = BuildConfig.DEMO_TOKEN_AUTH_KEY
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

    /**
     * Library "Token authentication key" used by the demo to sign playback tokens when token auth
     * is enabled. Debug convenience only — blank means token auth is off / no token is sent.
     */
    var tokenAuthKey: String
        set(value) {
            prefs.edit().putString(TOKEN_AUTH_KEY, value).apply()
        }
        get() = prefs.getString(TOKEN_AUTH_KEY, DEFAULT_TOKEN_AUTH_KEY) ?: DEFAULT_TOKEN_AUTH_KEY
}
