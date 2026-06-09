package com.nimbleflux.medtrumwatch.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF4A90D9)
private val BlueLight = Color(0xFF7BB3E8)
private val BlueDark = Color(0xFF2E6DB5)
private val SurfaceDark = Color(0xFF1A1A2E)
private val SurfaceContainerDark = Color(0xFF16213E)
private val BackgroundDark = Color(0xFF0F0F1A)
private val ErrorRed = Color(0xFFCF6679)
private val SuccessGreen = Color(0xFF66BB6A)
private val WarmAmber = Color(0xFFFFA726)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = BlueDark,
    secondary = WarmAmber,
    onSecondary = Color.White,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF1A1C1E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEAF2),
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = BlueDark,
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = WarmAmber,
    onSecondary = Color(0xFF452B00),
    error = ErrorRed,
    onError = Color(0xFF690005),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E2EC),
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = Color(0xFF1E1E36),
    surfaceContainer = SurfaceContainerDark,
)

@Composable
fun GlucoseSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
