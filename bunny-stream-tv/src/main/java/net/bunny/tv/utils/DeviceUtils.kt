// bunny-stream-tv/src/main/java/net/bunny/tv/utils/DeviceUtils.kt
package net.bunny.tv.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Device type enumeration for different Android form factors
 */
enum class DeviceType {
    MOBILE,
    TABLET,
    TV,
    WATCH,
    AUTO,
    UNKNOWN
}

/**
 * Utility object for detecting device capabilities and types
 */
object DeviceUtils {
    
    /**
     * Checks if the current device is running on Android TV
     */
    fun isRunningOnTV(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val currentModeType = uiModeManager.currentModeType
        
        return when {
            // Check if device is in TV mode
            currentModeType == Configuration.UI_MODE_TYPE_TELEVISION -> true
            
            // Check for leanback feature (Android TV)
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> true
            
            // Check for leanback launcher (Android TV)
            context.packageManager.hasSystemFeature("android.software.leanback") -> true
            
            // Additional check for TV-only mode
            !hasTouchScreen(context) && hasLeanbackLauncher(context) -> true
            
            else -> false
        }
    }
    
    /**
     * Checks if the device has a touchscreen
     */
    fun hasTouchScreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
    
    /**
     * Checks if the device has leanback launcher support
     */
    private fun hasLeanbackLauncher(context: Context): Boolean {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        return intent != null
    }
    
    /**
     * Gets the device type based on various characteristics
     */
    fun getDeviceType(context: Context): DeviceType {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        
        return when (uiModeManager.currentModeType) {
            Configuration.UI_MODE_TYPE_TELEVISION -> DeviceType.TV
            Configuration.UI_MODE_TYPE_WATCH -> DeviceType.WATCH
            Configuration.UI_MODE_TYPE_CAR -> DeviceType.AUTO
            Configuration.UI_MODE_TYPE_NORMAL -> {
                // Further distinguish between mobile and tablet
                val isTablet = isTablet(context)
                if (isTablet) DeviceType.TABLET else DeviceType.MOBILE
            }
            else -> DeviceType.UNKNOWN
        }
    }
    
    /**
     * Determines if the device is a tablet based on screen size and density
     */
    private fun isTablet(context: Context): Boolean {
        val configuration = context.resources.configuration
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        
        return when (screenLayout) {
            Configuration.SCREENLAYOUT_SIZE_LARGE,
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> true
            else -> {
                // Additional check based on smallest width
                val smallestScreenWidthDp = configuration.smallestScreenWidthDp
                smallestScreenWidthDp >= 600 // 600dp is typically the tablet threshold
            }
        }
    }
}

/**
 * Extension function to check if running on TV
 */
fun Context.isRunningOnTV(): Boolean = DeviceUtils.isRunningOnTV(this)

/**
 * Extension function to get device type
 */
fun Context.getDeviceType(): DeviceType = DeviceUtils.getDeviceType(this)

/**
 * Extension function to check if device has touchscreen
 */
fun Context.hasTouchScreen(): Boolean = DeviceUtils.hasTouchScreen(this)
