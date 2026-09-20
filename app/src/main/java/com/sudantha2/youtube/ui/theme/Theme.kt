package com.sudantha2.youtube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Static color schemes (no dynamic-color lookup at runtime — one less
 * resource resolution per composition). YouTube-dark is the default look.
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    secondary = Color(0xFF3EA6FF),
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFF272727),
    onBackground = Color(0xFFF1F1F1),
    onSurface = Color(0xFFF1F1F1),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    secondary = Color(0xFF065FD4),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFEFEFEF),
    onBackground = Color(0xFF0F0F0F),
    onSurface = Color(0xFF0F0F0F),
)

@Composable
fun YT4Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
