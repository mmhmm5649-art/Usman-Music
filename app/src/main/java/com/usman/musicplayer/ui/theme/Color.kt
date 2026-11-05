package com.usman.musicplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Pure AMOLED Black Background
val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF08090E)
val DarkSurfaceElevated = Color(0xFF0F111A)

// Apple-style Refined Accents (sophisticated purple, electric blue, cyan, magenta)
val NeonCyan = Color(0xFF00E5FF)
val ElectricBlue = Color(0xFF2979FF)
val NeonPurple = Color(0xFFA855F7)
val NeonMagenta = Color(0xFFEC4899)
val NeonPink = Color(0xFFFF2A85)
val SoftCyan = Color(0xFF38BDF8)
val SoftPurple = Color(0xFFC084FC)

// Authentic Apple-inspired Liquid Glass Material Tokens
val GlassWhiteHighlight = Color(0x73FFFFFF)       // Crisp top specular gleam (~45%)
val GlassWhiteSubtle = Color(0x38FFFFFF)          // Secondary glass bevel rim (~22%)
val GlassWhiteFaint = Color(0x18FFFFFF)           // Bottom refraction edge (~9%)
val GlassSurface = Color(0x1A202A44)             // Ultra-translucent glass slab (10% tint)
val GlassSurfaceHeavy = Color(0x2818223C)        // Translucent frosted glass (16% tint)
val GlassSurfaceLight = Color(0x242E3D66)        // Floating elevated glass chip (14% tint)
val GlassSurfaceDock = Color(0x331C2440)         // Translucent floating glass dock (20% tint)
val GlassInnerShadow = Color(0x26000000)          // Soft ambient occlusion behind glass

// Legacy color token aliases for compatibility across all screens
val GlassBackground = GlassSurfaceHeavy
val GlassCardBackground = GlassSurface
val GlassBorder = Color(0x5900E5FF)
val GlassBorderMagenta = Color(0x59EC4899)
val GlassBorderSubtle = Color(0x40FFFFFF)

// Text Tokens
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B6C8)
val TextMuted = Color(0xFF757D96)

// Real Liquid Glass Multi-stop Specular Rim Brushes
val LiquidGlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0x99FFFFFF), // Top-left high specular glint
        Color(0x3500E5FF), // Subtle cyan refraction
        Color(0x25C084FC), // Subtle purple dispersion
        Color(0x1AFFFFFF)  // Faint bottom refraction edge
    )
)

val LiquidGlassButtonBorder = Brush.linearGradient(
    colors = listOf(
        Color(0x80FFFFFF), // Specular rim
        Color(0x3038BDF8), // Cyan refraction
        Color(0x25EC4899), // Magenta refraction
        Color(0x18FFFFFF)  // Translucent bottom
    )
)

val LiquidGlassDockBorder = Brush.verticalGradient(
    colors = listOf(
        Color(0xA6FFFFFF), // Top luminous edge catch (65% white)
        Color(0x3338BDF8), // Translucent cyan bevel
        Color(0x15A855F7), // Soft purple dispersion
        Color(0x20FFFFFF)  // Bottom refraction rim
    )
)

val GlassBorderBrush = LiquidGlassBorderBrush

// Active Pill & Play/Pause Orbs
val ActivePillBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF0284C7), Color(0xFF7C3AED))
)

val PlayPauseButtonBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFF06B6D4))
)

val PlayPauseGlowBrush = Brush.radialGradient(
    colors = listOf(Color(0x66A855F7), Color(0x3300E5FF), Color.Transparent),
    radius = 160f
)
