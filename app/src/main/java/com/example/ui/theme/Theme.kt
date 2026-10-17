package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    secondary = EarthOchre,
    tertiary = StatusBlue,
    background = DarkSurface,
    surface = DarkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE2E2E2),
    onSurface = Color(0xFFE2E2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    secondary = EarthOchre,
    tertiary = StatusBlue,
    background = EarthSand,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1C19),
    onSurface = Color(0xFF1A1C19)
)

@Composable
fun PataPairTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
