package net.bunny.android.demo.player

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.bunny.android.demo.ui.AppState
import java.net.URLEncoder

const val PLAYER_ROUTE = "player"
const val VIDEO_ID      = "videoId"
const val LIBRARY_ID    = "libraryId"

fun NavController.navigateToPlayer(
    videoId: String,
    libraryId: Long?,
    navOptions: NavOptions? = null
) {
    val encodedVideoId = URLEncoder.encode(videoId, "UTF-8")
    // if libraryId==null we pass -1
    val libSegment = libraryId ?: -1L
    navigate("$PLAYER_ROUTE/$encodedVideoId/$libSegment", navOptions)
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
                nullable     = false   // we’re not really “nullable” at Nav‐level
            }
        )
    ) { backStack ->
        val videoId   = backStack.arguments!!.getString(VIDEO_ID)!!
        val rawLibId  = backStack.arguments!!.getLong(LIBRARY_ID)
        val libraryId = rawLibId.takeIf { it != -1L }  // convert sentinel → null
        PlayerRoute(appState, videoId, libraryId)
    }
}