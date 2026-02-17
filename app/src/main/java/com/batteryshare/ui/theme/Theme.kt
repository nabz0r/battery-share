package com.batteryshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color.Black,
    secondary = Color(0xFF69F0AE),
    onSecondary = Color.Black,
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFFF5252),
    onError = Color.White
)

@Composable
fun BatteryShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
