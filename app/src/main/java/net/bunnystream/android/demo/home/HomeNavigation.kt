@file:OptIn(ExperimentalMaterial3Api::class)

package net.bunnystream.android.demo.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import net.bunny.android.demo.App
import net.bunny.android.demo.ui.AppState
import androidx.compose.ui.Modifier

const val HOME_ROUTE = "home"

fun NavController.navigateToHome(navOptions: NavOptions? = null) {
    this.navigate(HOME_ROUTE, navOptions)
}

fun NavGraphBuilder.homeScreen(
    appState: AppState,
    navigateToSettings: () -> Unit,
    navigateToVideoList: () -> Unit,
    navigateToUpload: () -> Unit,
    navigateToStreaming: () -> Unit,
     navigateToResumeSettings: () -> Unit,
    navigateToResumeManagement: () -> Unit,
    modifier: Modifier = Modifier,
    navigateToPlayer: (String, Long) -> Unit,
) {
    composable(
        route = HOME_ROUTE,
    ) {
        HomeScreenRoute(
            appState = appState,
            localPrefs = App.di.localPrefs,
            navigateToSettings = navigateToSettings,
            navigateToVideoList = navigateToVideoList,
            navigateToUpload = navigateToUpload,
            navigateToStreaming = navigateToStreaming,
            navigateToPlayer = navigateToPlayer,
            navigateToResumeSettings = navigateToResumeSettings,
            navigateToResumeManagement = navigateToResumeManagement,
            modifier = modifier
        )
    }
}
