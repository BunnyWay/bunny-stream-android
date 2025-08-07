package net.bunny.bunnystreamplayer.common

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

class I18n(private val context: Context) {

    companion object {
        private const val TAG = "I18n"
    }

    private var languageCode: String? = null

    fun load(lang: String) {
        languageCode = lang
    }

    /**
     * Gets a localized string for the specified language and string resource ID.
     *
     * @param resId ID from R.string.*
     * @param languageCode ISO 639 language code, e.g., "en", "bg", "fr"
     */
    fun getTranslation(resId: Int): String {
        return try {
            val lang = languageCode ?: return context.getString(resId)
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale(lang))
            }
            val localizedContext = context.createConfigurationContext(config)
            localizedContext.getString(resId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load string for '$languageCode': ${e.message}")
            context.getString(resId) // fallback to default
        }
    }
}
