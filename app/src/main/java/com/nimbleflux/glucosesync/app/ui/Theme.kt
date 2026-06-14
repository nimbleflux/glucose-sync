package com.nimbleflux.glucosesync.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

private val Blue = Color(0xFF4A90D9)
private val BlueLight = Color(0xFF7BB3E8)
private val BlueDark = Color(0xFF2E6DB5)
private val SurfaceDark = Color(0xFF1A1A2E)
private val SurfaceContainerDark = Color(0xFF16213E)
private val BackgroundDark = Color(0xFF0F0F1A)
private val ErrorRed = Color(0xFFCF6679)
private val SuccessGreen = Color(0xFF66BB6A)
private val SuccessGreenDark = Color(0xFF8FD694)
private val SuccessContainer = Color(0xFFD6F0D7)
private val SuccessContainerDark = Color(0xFF2D4A2F)
private val WarmAmber = Color(0xFFFFA726)
private val Outline = Color(0xFF79747E)
private val OutlineDark = Color(0xFF938F99)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = BlueDark,
    secondary = WarmAmber,
    onSecondary = Color.White,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = SuccessContainer,
    onTertiaryContainer = Color(0xFF0F3D14),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEAF2),
    surfaceContainerHigh = Color(0xFFE6E7ED),
    surfaceContainerHighest = Color(0xFFE0E1E9),
    outline = Outline,
    outlineVariant = Color(0xFFC4C6D0)
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = BlueDark,
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = WarmAmber,
    onSecondary = Color(0xFF452B00),
    tertiary = SuccessGreenDark,
    onTertiary = Color(0xFF00391A),
    tertiaryContainer = SuccessContainerDark,
    onTertiaryContainer = Color(0xFFB0F0B7),
    error = ErrorRed,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = Color(0xFFE2E2EC),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E2EC),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = Color(0xFF1E1E36),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF1F2540),
    surfaceContainerHighest = Color(0xFF2A3050),
    outline = OutlineDark,
    outlineVariant = Color(0xFF44474F)
)

@Composable
fun GlucoseSyncTheme(
    themeMode: String = "system",
    activity: ComponentActivity,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    SideEffect {
        val window = activity.window
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
