package com.rishav.pennywise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = EmeraldDark,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = White,
    secondary = MintSurfaceVariant,
    onSecondary = White,
    secondaryContainer = TextPrimary,
    onSecondaryContainer = White,
    background = TextPrimary,
    onBackground = White,
    surface = ColorTokens.DarkSurface,
    onSurface = White,
    onSurfaceVariant = ColorTokens.DarkSurfaceVariant,
    outlineVariant = EmeraldDark
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = White,
    primaryContainer = EmeraldLight,
    onPrimaryContainer = EmeraldDark,
    secondary = EmeraldDark,
    onSecondary = White,
    secondaryContainer = MintSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    background = MintSurface,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outlineVariant = MintOutline
)

@Composable
fun PennyWiseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val DarkSurface = EmeraldDark
    val DarkSurfaceVariant = EmeraldLight
}
