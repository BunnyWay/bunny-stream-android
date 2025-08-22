package net.bunny.bunnystreamplayer.context

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext

internal object AppCastContext {

    private const val TAG = "AppCastContext"

    @SuppressLint("StaticFieldLeak")
    private var context: CastContext? = null
    private var isAvailable: Boolean = false

    fun setUp(context: Context) {
        Log.d(TAG, "setup context=$context")
        try {
            AppCastContext.context = CastContext.getSharedInstance(context)
            isAvailable = true
            Log.d(TAG, "Cast context initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Cast framework not available: ${e.message}")
            AppCastContext.context = null
            isAvailable = false
            // Don't throw the exception, just mark as unavailable
        }
    }

    fun get(): CastContext {
        return context ?: throw IllegalStateException(
            "Cast context not available. Make sure Google Play Services and Cast framework are available on this device."
        )
    }

    fun isAvailable(): Boolean = isAvailable

    fun getOrNull(): CastContext? = context
}