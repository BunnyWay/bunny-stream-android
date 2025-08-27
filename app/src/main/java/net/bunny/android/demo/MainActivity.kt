package net.bunny.android.demo

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHost
import androidx.navigation.compose.NavHost
import net.bunny.android.demo.home.HOME_ROUTE
import net.bunny.android.demo.library.libraryScreen
import net.bunny.android.demo.library.navigateToLibrary
import net.bunny.android.demo.navigation.navigateToResumeManagement
import net.bunny.android.demo.navigation.navigateToResumeSettings
import net.bunny.android.demo.navigation.resumePositionManagementScreen
import net.bunny.android.demo.navigation.resumePositionSettingsScreen
import net.bunny.android.demo.navigation.tvHomeScreen
import net.bunny.android.demo.recording.RecordingActivity
import net.bunny.android.demo.settings.navigateToSettings
import net.bunny.android.demo.settings.settingsScreen
import net.bunny.android.demo.ui.App
import net.bunny.android.demo.ui.AppState
import net.bunny.android.demo.ui.rememberAppState
import net.bunny.android.demo.ui.theme.BunnyStreamTheme
import net.bunny.tv.ui.BunnyTVPlayerActivity

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
        // For TV, use a different compose layout optimized for TV navigation
        setContent {
            BunnyStreamTheme {
                TVApp() // Use TV-specific app instead of regular App()
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

    @Composable
    fun TVApp() {
        val appState = rememberAppState()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            modifier = Modifier,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TVAppNavHost(
                    appState = appState,
                    onShowSnackbar = { message, action ->
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = action,
                            duration = SnackbarDuration.Short,
                        ) == SnackbarResult.ActionPerformed
                    }
                )
            }
        }
    }

    @Composable
    fun TVAppNavHost(
        appState: AppState,
        onShowSnackbar: suspend (String, String?) -> Boolean,
        modifier: Modifier = Modifier,
        startDestination: String = HOME_ROUTE,
    ) {
        val navController = appState.navController
        val context = LocalContext.current

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
        ) {
            // TV-optimized home screen
            tvHomeScreen(
                appState = appState,
                navigateToSettings = navController::navigateToSettings,
                navigateToVideoList = navController::navigateToLibrary,
                navigateToUpload = { navController.navigateToLibrary(showUpload = true) },
                navigateToTVPlayer = { videoId, libraryId ->
                    // Use TV player directly
                    BunnyTVPlayerActivity.start(
                        context = context,
                        videoId = videoId,
                        libraryId = libraryId
                    )
                },
                navigateToStreaming = {
                    context.startActivity(Intent(context, RecordingActivity::class.java))
                },
                navigateToResumeSettings = navController::navigateToResumeSettings,
                navigateToResumeManagement = navController::navigateToResumeManagement,
                modifier = modifier
            )

            // Keep existing screens but with TV navigation
            libraryScreen(
                appState = appState,
                navigateToSettings = navController::navigateToSettings,
                navigateToPlayer = { videoId ->
                    // Use TV player for videos
                    BunnyTVPlayerActivity.start(
                        context = context,
                        videoId = videoId,
                        libraryId = App.di.localPrefs.libraryId
                    )
                },
            )
            settingsScreen(appState = appState)
            resumePositionSettingsScreen(appState = appState)
            resumePositionManagementScreen(
                appState = appState,
                onPlayVideo = { videoId, libraryId ->
                    BunnyTVPlayerActivity.start(
                        context = context,
                        videoId = videoId,
                        libraryId = libraryId ?: App.di.localPrefs.libraryId
                    )
                }
            )
        }
    }
}