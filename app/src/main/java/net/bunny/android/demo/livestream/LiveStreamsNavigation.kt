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
private const val LIVE_PLAYER_ARG_FALLBACK_URL = "fallbackUrl"
private const val LIVE_PLAYER_ARG_TITLE = "title"

private const val LIVE_EDITOR_ROUTE_PREFIX = "live_stream_editor"
private const val LIVE_EDITOR_ARG_STREAM_ID = "streamId"

fun NavController.navigateToLiveStreams(navOptions: NavOptions? = null) {
    this.navigate(LIVE_STREAMS_ROUTE, navOptions)
}

fun NavController.navigateToLiveStreamPlayer(
    streamId: String,
    fallbackHlsUrl: String?,
    title: String?,
) {
    Log.d(
        "BunnyLive/Nav",
        "navigateToLiveStreamPlayer — streamId=$streamId title='$title' " +
            "fallbackHlsUrl=$fallbackHlsUrl",
    )
    val encodedStreamId = URLEncoder.encode(streamId, Charsets.UTF_8.name())
    val encodedFallback = URLEncoder.encode(fallbackHlsUrl.orEmpty(), Charsets.UTF_8.name())
    val encodedTitle = URLEncoder.encode(title.orEmpty(), Charsets.UTF_8.name())
    navigate("$LIVE_PLAYER_ROUTE_PREFIX/$encodedStreamId/$encodedFallback/$encodedTitle")
}

/**
 * Opens the full-screen live stream editor. Pass `null` [streamId] to create a new stream.
 */
fun NavController.navigateToLiveStreamEditor(streamId: String? = null) {
    Log.d("BunnyLive/Nav", "navigateToLiveStreamEditor — streamId=$streamId")
    val encoded = URLEncoder.encode(streamId.orEmpty(), Charsets.UTF_8.name())
    navigate("$LIVE_EDITOR_ROUTE_PREFIX?$LIVE_EDITOR_ARG_STREAM_ID=$encoded")
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
        LiveStreamEditorRoute(appState = appState, streamId = streamId)
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
            "{$LIVE_PLAYER_ARG_FALLBACK_URL}/" +
            "{$LIVE_PLAYER_ARG_TITLE}",
        arguments = listOf(
            navArgument(LIVE_PLAYER_ARG_STREAM_ID) { type = NavType.StringType },
            navArgument(LIVE_PLAYER_ARG_FALLBACK_URL) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(LIVE_PLAYER_ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStack ->
        val streamId = backStack.arguments?.getString(LIVE_PLAYER_ARG_STREAM_ID).orEmpty()
        val fallback = backStack.arguments?.getString(LIVE_PLAYER_ARG_FALLBACK_URL)
            ?.takeIf { it.isNotBlank() }
        val title = backStack.arguments?.getString(LIVE_PLAYER_ARG_TITLE)?.takeIf { it.isNotBlank() }
        LiveStreamPlayerRoute(
            appState = appState,
            streamId = streamId,
            fallbackHlsUrl = fallback,
            title = title,
        )
    }
}
