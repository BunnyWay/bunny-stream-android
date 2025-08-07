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
        AppCastContext.setUp(context)
        return context
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}