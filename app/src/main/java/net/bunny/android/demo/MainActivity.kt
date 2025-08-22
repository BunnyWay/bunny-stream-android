package net.bunny.android.demo

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import net.bunny.android.demo.ui.App
import net.bunny.android.demo.ui.theme.BunnyStreamTheme

class MainActivity : AppCompatActivity() {

    companion object {
        fun isRunningOnTV(packageManager: PackageManager): Boolean {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if running on TV using the utility function
        if (isRunningOnTV(packageManager)) {
            // For TV, you might want to launch a different interface
            // For now, we'll continue with the same interface but with TV detection
            setupTVInterface()
        } else {
            // Continue with normal mobile interface
            setupMobileInterface()
        }
    }

    private fun setupTVInterface() {
        // You can customize this for TV
        // For now, using the same interface but marking as TV mode
        setContent {
            BunnyStreamTheme {
                App()
            }
        }
    }

    private fun setupMobileInterface() {
        setContent {
            BunnyStreamTheme {
                App()
            }
        }
    }
}