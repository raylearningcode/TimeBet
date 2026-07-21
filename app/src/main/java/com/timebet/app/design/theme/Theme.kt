package com.timebet.app.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TimeBetColorScheme = darkColorScheme(
    primary = TimeBetWhite,
    onPrimary = TimeBetBlack,
    primaryContainer = TimeBetSurfaceElevated,
    onPrimaryContainer = TimeBetTextPrimary,
    secondary = TimeBetTextSecondary,
    onSecondary = TimeBetBlack,
    secondaryContainer = TimeBetSurfaceCard,
    onSecondaryContainer = TimeBetTextSecondary,
    tertiary = TimeBetTextTertiary,
    onTertiary = TimeBetBlack,
    background = TimeBetBlack,
    onBackground = TimeBetTextPrimary,
    surface = TimeBetSurface,
    onSurface = TimeBetTextPrimary,
    surfaceVariant = TimeBetSurfaceElevated,
    onSurfaceVariant = TimeBetTextSecondary,
    outline = TimeBetBorder,
    outlineVariant = TimeBetBorderLight,
    error = TimeBetRed,
    onError = TimeBetWhite,
    errorContainer = TimeBetRed.copy(alpha = 0.15f),
    onErrorContainer = TimeBetRed
)

@Composable
fun TimeBetTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TimeBetColorScheme,
        typography = TimeBetTypography,
        content = content
    )
}
