package com.chaoqun.depthwhite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF7A3B)
private val OrangeDim = Color(0xFFFF9257)
private val DarkBg = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A1208),
    primaryContainer = Color(0xFF5A2A12),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = OrangeDim,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2C2C2C),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB54A16),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF3A1400),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceVariant = Color(0xFFF4E2DA),
    onBackground = Color(0xFF231A16),
    onSurface = Color(0xFF231A16),
)

@Composable
fun DepthWhiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
