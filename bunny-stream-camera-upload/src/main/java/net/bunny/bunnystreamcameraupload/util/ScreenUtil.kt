package net.bunny.bunnystreamcameraupload.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.View

class ScreenUtil {
    companion object {
        fun lockCurrentOrientation(view: View) {
            val activity = view.context.findActivity() ?: return

            val rotation = activity.windowManager.defaultDisplay.rotation
            val orientation = activity.resources.configuration.orientation

            val lockedOrientation = when {
                rotation == Surface.ROTATION_0 && orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ->
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                rotation == Surface.ROTATION_180 && orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ->
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                rotation == Surface.ROTATION_90 && orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ->
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                rotation == Surface.ROTATION_270 && orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ->
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            activity.requestedOrientation = lockedOrientation
        }

        fun unlockScreenOrientation(view: View) {
            val activity = view.context.findActivity() ?: return
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        private fun Context.findActivity(): Activity? {
            var context = this
            while (context is ContextWrapper) {
                if (context is Activity) return context
                context = context.baseContext
            }
            return null
        }
    }
}