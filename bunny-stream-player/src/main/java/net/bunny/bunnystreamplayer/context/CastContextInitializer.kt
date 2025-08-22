package net.bunny.bunnystreamplayer.context

import android.content.Context
import android.util.Log
import androidx.startup.Initializer

/**
 * Necessary since `CastContext.getSharedInstance()` needs to be called very early in app lifecycle
 * but we don't have a hosting activity to override it's `onCreate()`
 */
internal class CastContextInitializer : Initializer<Context> {
    companion object {
        private const val TAG = "CastContextInitializer"
    }
    override fun create(context: Context): Context {
        Log.d(TAG, "create")
        try {
            AppCastContext.setUp(context)
        } catch (e: Exception) {
            Log.w(TAG, "Cast framework not available, continuing without Cast support", e)
            // Don't throw the exception, just log it and continue
            // This allows the app to work on devices without Google Play Services or Cast support
        }
        return context
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}