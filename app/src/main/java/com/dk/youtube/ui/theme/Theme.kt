package com.dk.youtube.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val YouTubeDarkColorScheme = darkColorScheme(
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

private val YouTubeLightColorScheme = lightColorScheme(
    primary = YouTubeRed,
    onPrimary = YouTubeWhite,
    primaryContainer = YouTubeLightRed,
    onPrimaryContainer = YouTubeWhite,
    secondary = YouTubeAccentBlue,
    onSecondary = YouTubeWhite,
    background = YouTubeWhite,
    onBackground = YouTubeLightTextPrimary,
    surface = YouTubeLightSurface,
    onSurface = YouTubeLightTextPrimary,
    surfaceVariant = YouTubeLightSurfaceVariant,
    onSurfaceVariant = YouTubeLightTextSecondary,
    outline = YouTubeLightBorder,
    error = YouTubeRed
)

@Composable
fun YouTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YouTubeDarkColorScheme else YouTubeLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
