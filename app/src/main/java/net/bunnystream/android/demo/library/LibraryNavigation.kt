package net.bunnystream.android.demo.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.bunny.android.demo.App
import net.bunny.android.demo.ui.AppState

const val LIBRARY_ROUTE = "library"
const val SHOW_UPLOAD = "library"

fun NavController.navigateToLibrary(navOptions: NavOptions? = null, showUpload: Boolean = false) {
    this.navigate("$LIBRARY_ROUTE/$showUpload", navOptions)
}

fun NavGraphBuilder.libraryScreen(
    appState: AppState,
    navigateToSettings: () -> Unit,
    navigateToPlayer: (String) -> Unit
) {
    composable(
        route = "$LIBRARY_ROUTE/{$SHOW_UPLOAD}",
        arguments = listOf(
            navArgument(SHOW_UPLOAD) { type = NavType.BoolType },
        ),
    ) {
        val showUpload = it.arguments?.getBoolean(SHOW_UPLOAD) ?: false
        LibraryRoute(
            appState = appState,
            showUpload = showUpload,
            navigateToSettings = navigateToSettings,
            navigateToPlayer = { videoId -> navigateToPlayer(videoId) },
            localPrefs = App.di.localPrefs
        )
    }
}
