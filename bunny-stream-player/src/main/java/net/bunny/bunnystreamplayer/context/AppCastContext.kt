package net.bunny.bunnystreamplayer.context

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext

internal object AppCastContext {

    private const val TAG = "AppCastContext"

    @SuppressLint("StaticFieldLeak")
    private lateinit var context: CastContext

    fun setUp(context: Context) {
        Log.d(TAG, "setup context=$context")
        AppCastContext.context = CastContext.getSharedInstance(context)
    }

    fun get(): CastContext {
        return context
    }
}