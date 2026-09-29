package org.olo.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app's clay accent over ivory in the light theme, warm dark in the dark
// one -- the same palette the player screen paints itself with, so the settings
// sheet (which takes the theme) matches the player around it.
private val Clay = Color(0xFFE8A183)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4552F),
    onPrimary = Color.White,
    surface = Color(0xFFFDF7F2),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFEEE2D8),
    onSurfaceVariant = Color(0xFF574539),
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    onPrimary = Color(0xFF12100E),
    surface = Color(0xFF1B1815),
    onSurface = Color(0xFFF2E9E2),
    surfaceVariant = Color(0xFF33291F),
    onSurfaceVariant = Color(0xFFCDBBAC),
)

@Composable
fun OloPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
