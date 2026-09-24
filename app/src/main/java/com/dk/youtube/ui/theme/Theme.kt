package com.dk.youtube.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val YouTubeColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = YouTubeTextPrimary,
    primaryContainer = YouTubeDarkRed,
    onPrimaryContainer = YouTubeTextPrimary,
    secondary = YouTubeAccentBlue,
    onSecondary = YouTubeBlack,
    background = YouTubeBlack,
    onBackground = YouTubeTextPrimary,
    surface = YouTubeSurface,
    onSurface = YouTubeTextPrimary,
    surfaceVariant = YouTubeSurfaceVariant,
    onSurfaceVariant = YouTubeTextSecondary,
    outline = YouTubeBorder,
    error = YouTubeRed
)

@Composable
fun YouTubeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = YouTubeBlack.toArgb()
            window.navigationBarColor = YouTubeBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = YouTubeColorScheme,
        typography = Typography,
        content = content
    )
}
