package net.bunny.android.demo.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import net.bunny.android.demo.ui.AppState

const val SETTINGS_ROUTE = "settings"

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    this.navigate(SETTINGS_ROUTE, navOptions)
}

fun NavGraphBuilder.settingsScreen(appState: AppState) {
    composable(
        route = SETTINGS_ROUTE,
    ) {
        SettingsRoute(appState = appState)
    }
}
