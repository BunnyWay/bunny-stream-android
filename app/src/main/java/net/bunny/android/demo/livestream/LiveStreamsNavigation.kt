package net.bunny.android.demo.livestream

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.bunny.android.demo.ui.AppState
import java.net.URLEncoder

const val LIVE_STREAMS_ROUTE = "live_streams"

private const val LIVE_PLAYER_ROUTE_PREFIX = "live_player"
private const val LIVE_PLAYER_ARG_STREAM_ID = "streamId"
private const val LIVE_PLAYER_ARG_TITLE = "title"

private const val LIVE_EDITOR_ROUTE_PREFIX = "live_stream_editor"
private const val LIVE_EDITOR_ARG_STREAM_ID = "streamId"

private const val TRAILER_PICKER_ROUTE = "live_trailer_picker"

/** savedStateHandle key the trailer picker uses to hand the chosen video id back to the editor. */
const val TRAILER_PICK_RESULT_KEY = "picked_trailer_video_id"

private const val THUMBNAIL_PICKER_ROUTE = "live_thumbnail_picker"

/** savedStateHandle key the thumbnail picker uses to hand the chosen image URL back to the editor. */
const val THUMBNAIL_PICK_RESULT_KEY = "picked_thumbnail_url"

fun NavController.navigateToLiveStreams(navOptions: NavOptions? = null) {
    this.navigate(LIVE_STREAMS_ROUTE, navOptions)
}

fun NavController.navigateToLiveStreamPlayer(streamId: String, title: String?) {
    Log.d("BunnyLive/Nav", "navigateToLiveStreamPlayer — streamId=$streamId title='$title'")
    val encodedStreamId = URLEncoder.encode(streamId, Charsets.UTF_8.name())
    val encodedTitle = URLEncoder.encode(title.orEmpty(), Charsets.UTF_8.name())
    navigate("$LIVE_PLAYER_ROUTE_PREFIX/$encodedStreamId/$encodedTitle")
}

/**
 * Opens the full-screen live stream editor. Pass `null` [streamId] to create a new stream.
 */
fun NavController.navigateToLiveStreamEditor(streamId: String? = null) {
    Log.d("BunnyLive/Nav", "navigateToLiveStreamEditor — streamId=$streamId")
    val encoded = URLEncoder.encode(streamId.orEmpty(), Charsets.UTF_8.name())
    navigate("$LIVE_EDITOR_ROUTE_PREFIX?$LIVE_EDITOR_ARG_STREAM_ID=$encoded")
}

/** Opens the trailer picker (a library video list) to choose a stream's pre-stream trailer. */
fun NavController.navigateToTrailerPicker() {
    Log.d("BunnyLive/Nav", "navigateToTrailerPicker")
    navigate(TRAILER_PICKER_ROUTE)
}

fun NavGraphBuilder.liveTrailerPickerScreen(appState: AppState) {
    composable(route = TRAILER_PICKER_ROUTE) {
        TrailerPickerRoute(
            appState = appState,
            onPicked = { videoId ->
                // Hand the result to the destination we'll return to (the editor) and pop.
                appState.navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(TRAILER_PICK_RESULT_KEY, videoId)
                appState.navController.popBackStack()
            },
        )
    }
}

/** Opens the thumbnail picker (a library video-thumbnail list) to choose a stream's thumbnail. */
fun NavController.navigateToThumbnailPicker() {
    Log.d("BunnyLive/Nav", "navigateToThumbnailPicker")
    navigate(THUMBNAIL_PICKER_ROUTE)
}

fun NavGraphBuilder.liveThumbnailPickerScreen(appState: AppState) {
    composable(route = THUMBNAIL_PICKER_ROUTE) {
        ThumbnailPickerRoute(
            appState = appState,
            onPicked = { thumbnailUrl ->
                appState.navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(THUMBNAIL_PICK_RESULT_KEY, thumbnailUrl)
                appState.navController.popBackStack()
            },
        )
    }
}

fun NavGraphBuilder.liveStreamEditorScreen(appState: AppState) {
    composable(
        route = "$LIVE_EDITOR_ROUTE_PREFIX?$LIVE_EDITOR_ARG_STREAM_ID={$LIVE_EDITOR_ARG_STREAM_ID}",
        arguments = listOf(
            navArgument(LIVE_EDITOR_ARG_STREAM_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStack ->
        val streamId = backStack.arguments?.getString(LIVE_EDITOR_ARG_STREAM_ID)
            ?.takeIf { it.isNotBlank() }
        LiveStreamEditorRoute(
            appState = appState,
            streamId = streamId,
            // This entry's handle is where the trailer picker leaves its result; pass the stable
            // reference rather than reading currentBackStackEntry during a transition.
            savedStateHandle = backStack.savedStateHandle,
        )
    }
}

fun NavGraphBuilder.liveStreamsScreen(appState: AppState) {
    composable(route = LIVE_STREAMS_ROUTE) {
        LiveStreamsRoute(appState = appState)
    }
}

fun NavGraphBuilder.liveStreamPlayerScreen(appState: AppState) {
    composable(
        route = "$LIVE_PLAYER_ROUTE_PREFIX/" +
            "{$LIVE_PLAYER_ARG_STREAM_ID}/" +
            "{$LIVE_PLAYER_ARG_TITLE}",
        arguments = listOf(
            navArgument(LIVE_PLAYER_ARG_STREAM_ID) { type = NavType.StringType },
            navArgument(LIVE_PLAYER_ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStack ->
        val streamId = backStack.arguments?.getString(LIVE_PLAYER_ARG_STREAM_ID).orEmpty()
        val title = backStack.arguments?.getString(LIVE_PLAYER_ARG_TITLE)?.takeIf { it.isNotBlank() }
        LiveStreamPlayerRoute(
            appState = appState,
            streamId = streamId,
            title = title,
        )
    }
}
