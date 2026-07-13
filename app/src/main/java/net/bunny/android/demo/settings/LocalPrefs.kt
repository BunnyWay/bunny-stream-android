package net.bunny.android.demo.settings

import android.content.SharedPreferences
import net.bunny.android.demo.BuildConfig
import net.bunny.bunnystreamplayer.livestream.LiveControls
import net.bunny.bunnystreamplayer.livestream.LivePlayerConfig

class LocalPrefs(private val prefs: SharedPreferences) {

    companion object {
        private const val ACCESS_KEY = "accessKey"
        private const val LIBRARY_ID = "libraryId"
        private const val TOKEN_AUTH_KEY = "tokenAuthKey"

        // Live player customization (demo). Persists a LivePlayerConfig the "Customize Player" screen
        // edits and the live player screen applies. NO_COLOR is a sentinel for primaryColor == null
        // (0 would be a valid transparent-black, so it can't mean "unset").
        private const val LP_COLOR = "livePlayer.primaryColor"
        private const val LP_FONT = "livePlayer.fontFamily"
        private const val LP_LANG = "livePlayer.uiLanguage"
        private const val LP_COMPACT = "livePlayer.compactControls"
        private const val LP_C_PLAY = "livePlayer.controls.livePlayPause"
        private const val LP_C_PROGRESS = "livePlayer.controls.progress"
        private const val LP_C_DURATION = "livePlayer.controls.duration"
        private const val LP_C_MUTE = "livePlayer.controls.mute"
        private const val LP_C_FULLSCREEN = "livePlayer.controls.fullScreen"
        private const val LP_C_SETTINGS = "livePlayer.controls.settings"
        private const val LP_C_PIP = "livePlayer.controls.pip"
        private const val LP_C_CAST = "livePlayer.controls.chromecast"
        private const val LP_C_DVR = "livePlayer.controls.dvr"
        private const val NO_COLOR = Int.MIN_VALUE

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

    /**
     * The [LivePlayerConfig] the demo passes to the live player, edited on the "Customize Player"
     * screen. Only the fields that meaningfully affect the live player are persisted; the rest of
     * [LiveControls] keep their defaults. Defaults to `LivePlayerConfig()` on a fresh install.
     */
    var livePlayerConfig: LivePlayerConfig
        get() {
            val default = LiveControls()
            val color = prefs.getInt(LP_COLOR, NO_COLOR)
            return LivePlayerConfig(
                primaryColor = color.takeIf { it != NO_COLOR },
                fontFamily = prefs.getString(LP_FONT, "")?.takeIf { it.isNotBlank() },
                uiLanguage = prefs.getString(LP_LANG, "")?.takeIf { it.isNotBlank() },
                compactControls = prefs.getBoolean(LP_COMPACT, false),
                controls = LiveControls(
                    livePlayPause = prefs.getBoolean(LP_C_PLAY, default.livePlayPause),
                    progress = prefs.getBoolean(LP_C_PROGRESS, default.progress),
                    duration = prefs.getBoolean(LP_C_DURATION, default.duration),
                    mute = prefs.getBoolean(LP_C_MUTE, default.mute),
                    fullScreen = prefs.getBoolean(LP_C_FULLSCREEN, default.fullScreen),
                    settings = prefs.getBoolean(LP_C_SETTINGS, default.settings),
                    pip = prefs.getBoolean(LP_C_PIP, default.pip),
                    chromecast = prefs.getBoolean(LP_C_CAST, default.chromecast),
                    dvr = prefs.getBoolean(LP_C_DVR, default.dvr),
                ),
            )
        }
        set(value) {
            prefs.edit()
                .putInt(LP_COLOR, value.primaryColor ?: NO_COLOR)
                .putString(LP_FONT, value.fontFamily.orEmpty())
                .putString(LP_LANG, value.uiLanguage.orEmpty())
                .putBoolean(LP_COMPACT, value.compactControls)
                .putBoolean(LP_C_PLAY, value.controls.livePlayPause)
                .putBoolean(LP_C_PROGRESS, value.controls.progress)
                .putBoolean(LP_C_DURATION, value.controls.duration)
                .putBoolean(LP_C_MUTE, value.controls.mute)
                .putBoolean(LP_C_FULLSCREEN, value.controls.fullScreen)
                .putBoolean(LP_C_SETTINGS, value.controls.settings)
                .putBoolean(LP_C_PIP, value.controls.pip)
                .putBoolean(LP_C_CAST, value.controls.chromecast)
                .putBoolean(LP_C_DVR, value.controls.dvr)
                .apply()
        }
}