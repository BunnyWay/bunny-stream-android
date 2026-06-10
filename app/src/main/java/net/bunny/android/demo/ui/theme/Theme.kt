package net.bunny.android.demo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Blue60,
    onPrimary = White,
    onBackground = Blue40,
    onSurface = Blue60,
    onSurfaceVariant = Blue40,
)

private val LightColorScheme = lightColorScheme(
    primary = Orange60,
    onPrimary = White,
    onBackground = White,
    onSurface = Blue60,
    onSurfaceVariant = Blue40,
    background = White
)

@Composable
fun BunnyStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // No statusBarColor here: it is deprecated and ignored on Android 15+ (forced
            // edge-to-edge). The status bar is transparent and each screen's TopAppBar draws
            // its own background behind it. White icons fit the orange/primary app bars.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}