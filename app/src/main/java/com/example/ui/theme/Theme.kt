package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VelvetDarkColorScheme = darkColorScheme(
    primary = EmeraldCta,
    onPrimary = TextLight,
    secondary = ChampagneGold,
    onSecondary = SuitInk,
    tertiary = SkyHighlight,
    background = ObsidianBg,
    onBackground = TextLight,
    surface = VelvetSurface,
    onSurface = TextLight,
    surfaceVariant = VelvetSurfaceElevated,
    onSurfaceVariant = TextMuted,
    error = DangerRed,
    onError = TextLight
)

@Composable
fun VelvetRummyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VelvetDarkColorScheme,
        typography = VelvetTypography,
        content = content
    )
}
