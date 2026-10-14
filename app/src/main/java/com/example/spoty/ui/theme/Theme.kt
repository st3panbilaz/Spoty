package com.example.spoty.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = AccentWhite,
    onPrimary = Color.Black,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider
)

data class SpotyColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val divider: Color,
    val accent: Color
)

val DarkSpotyColors = SpotyColors(
    background = DarkBackground,
    surface = DarkSurface,
    surface2 = DarkSurface2,
    surface3 = DarkSurface3,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textHint = DarkTextHint,
    divider = DarkDivider,
    accent = AccentWhite
)

val LightSpotyColors = SpotyColors(
    background = LightBackground,
    surface = LightSurface,
    surface2 = LightSurface2,
    surface3 = LightSurface3,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textHint = LightTextHint,
    divider = LightDivider,
    accent = Color.Black
)

val LocalSpotyColors = staticCompositionLocalOf { DarkSpotyColors }

@Composable
fun SpotyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightNavigationBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = {
            val spotyColors = if (darkTheme) DarkSpotyColors else LightSpotyColors
            CompositionLocalProvider(
                LocalSpotyColors provides spotyColors,
                content = content
            )
        }
    )
}