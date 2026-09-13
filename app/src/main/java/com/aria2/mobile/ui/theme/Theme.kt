package com.aria2.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentDark,
    onPrimaryContainer = OnAccent,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHi,
    onSurfaceVariant = DarkOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    secondary = DarkMuted,
    onSecondary = DarkOnBg,
    error = Danger,
    onError = OnAccent,
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = LightSurface,
    primaryContainer = Accent.copy(alpha = 0.14f),
    onPrimaryContainer = AccentDark,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHi,
    onSurfaceVariant = LightOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutline,
    secondary = LightMuted,
    onSecondary = LightOnBg,
    error = Danger,
    onError = LightSurface,
)

@Composable
fun Aria2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}