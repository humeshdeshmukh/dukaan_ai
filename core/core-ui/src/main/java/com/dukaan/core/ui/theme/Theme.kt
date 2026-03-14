package com.dukaan.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmerald,
    onPrimary = SurfaceWhite,
    primaryContainer = SuccessGreenLight,
    onPrimaryContainer = PrimaryEmeraldDark,
    secondary = AccentGold,
    onSecondary = SurfaceWhite,
    secondaryContainer = GoldMuted,
    onSecondaryContainer = AccentGold,
    tertiary = InfoBlue,
    onTertiary = SurfaceWhite,
    tertiaryContainer = InfoBlueLight,
    onTertiaryContainer = InfoBlue,
    background = BackgroundLight,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextMuted,
    outline = DividerLight,
    error = ErrorRed,
    onError = SurfaceWhite,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEmeraldLight,
    onPrimary = PrimaryEmeraldDark,
    primaryContainer = PrimaryEmerald,
    onPrimaryContainer = SuccessGreenLight,
    secondary = GoldSoft,
    onSecondary = PrimaryEmeraldDark,
    secondaryContainer = AccentGold,
    onSecondaryContainer = GoldMuted,
    tertiary = InfoBlue,
    onTertiary = SurfaceWhite,
    tertiaryContainer = InfoBlue,
    onTertiaryContainer = InfoBlueLight,
    background = BackgroundDark,
    onBackground = TextOnDark,
    surface = SurfaceDark,
    onSurface = TextOnDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextMutedDark,
    outline = DividerDark,
    error = ErrorRed,
    onError = SurfaceWhite,
    errorContainer = ErrorRed,
    onErrorContainer = ErrorRedLight
)

@Composable
fun DukaanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
