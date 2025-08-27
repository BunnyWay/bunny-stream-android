package net.bunny.android.demo.player

import android.content.Context
import android.content.pm.PackageManager
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.bunny.android.demo.ui.AppState
import net.bunny.tv.ui.BunnyTVPlayerActivity
import java.net.URLEncoder

const val PLAYER_ROUTE = "player"
const val VIDEO_ID      = "videoId"
const val LIBRARY_ID    = "libraryId"

// Helper function to check if running on TV
private fun Context.isRunningOnTV(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

// Extension function for NavController
fun NavController.navigateToPlayer(
    videoId: String,
    libraryId: Long?,
    videoTitle: String? = null
) {
    val context = this.context

    if (context.isRunningOnTV()) {
        // Use TV player - try to launch TV player activity if available
        try {
            BunnyTVPlayerActivity.start(
                context = context,
                videoId = videoId,
                libraryId = libraryId ?: -1L,
                videoTitle = videoTitle
            )
        } catch (e: Exception) {
            // TV player not available, fall back to mobile navigation
            val encodedVideoId = URLEncoder.encode(videoId, "UTF-8")
            val libSegment = libraryId ?: -1L
            navigate("$PLAYER_ROUTE/$encodedVideoId/$libSegment")
        }
    } else {
        // Use mobile player (existing navigation)
        val encodedVideoId = URLEncoder.encode(videoId, "UTF-8")
        val libSegment = libraryId ?: -1L
        navigate("$PLAYER_ROUTE/$encodedVideoId/$libSegment")
    }
}

fun NavGraphBuilder.playerScreen(appState: AppState) {
    composable(
        route = "$PLAYER_ROUTE/{$VIDEO_ID}/{$LIBRARY_ID}",
        arguments = listOf(
            navArgument(VIDEO_ID) {
                type = NavType.StringType
            },
            navArgument(LIBRARY_ID) {
                type         = NavType.LongType
                defaultValue = -1L     // must be non-null
                nullable     = false   // we're not really "nullable" at Nav‐level
            }
        )
    ) { backStack ->
        val videoId   = backStack.arguments!!.getString(VIDEO_ID)!!
        val rawLibId  = backStack.arguments!!.getLong(LIBRARY_ID)
        val libraryId = rawLibId.takeIf { it != -1L }  // convert sentinel → null
        PlayerRoute(appState, videoId, libraryId)
    }
}