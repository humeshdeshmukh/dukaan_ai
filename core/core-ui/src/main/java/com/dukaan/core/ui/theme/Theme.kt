package com.dukaan.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmerald,
    onPrimary = SurfaceWhite,
    secondary = AccentGold,
    onSecondary = SurfaceWhite,
    background = BackgroundLight,
    surface = SurfaceWhite,
    error = ErrorRed,
    onSurface = TextDark,
    onBackground = TextDark
)

@Composable
fun DukaanTheme(
    content: @Composable () -> Unit
) {
    // For now, only Light theme as per requirements
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
