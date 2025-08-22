package net.bunny.api.utils

import android.content.Context
import android.content.pm.PackageManager

object DeviceUtils {
    
    /**
     * Check if the app is running on Android TV
     */
    fun isRunningOnTV(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /**
     * Check if device has touchscreen
     */
    fun hasTouchScreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /**
     * Get device type
     */
    fun getDeviceType(context: Context): DeviceType {
        return when {
            isRunningOnTV(context) -> DeviceType.TV
            hasTouchScreen(context) -> DeviceType.MOBILE
            else -> DeviceType.UNKNOWN
        }
    }

    /**
     * Check if device supports certain features
     */
    fun hasFeature(context: Context, feature: String): Boolean {
        return context.packageManager.hasSystemFeature(feature)
    }
}

enum class DeviceType {
    MOBILE,
    TV,
    TABLET,
    UNKNOWN
}

// Extension functions for easy access
fun Context.isRunningOnTV(): Boolean = DeviceUtils.isRunningOnTV(this)
fun Context.getDeviceType(): DeviceType = DeviceUtils.getDeviceType(this)
fun Context.hasTouchScreen(): Boolean = DeviceUtils.hasTouchScreen(this)
