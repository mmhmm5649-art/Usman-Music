package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UsmanDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = AmoledBlack,
    primaryContainer = Color(0xFF003847),
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = TextPrimary,
    secondaryContainer = Color(0xFF38155A),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = NeonMagenta,
    onTertiary = TextPrimary,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = GlassBorderSubtle
)

@Composable
fun UsmanMusicPlayerTheme(
    content: @Composable () -> Unit,
) {
    // Pure black AMOLED aesthetic with neon accents
    MaterialTheme(
        colorScheme = UsmanDarkColorScheme,
        typography = Typography,
        content = content
    )
}
