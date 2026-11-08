package com.usman.musicplayer.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Dynamic palette extracted from album artwork using Android Palette for Apple Music-style theme.
 */
data class AlbumPalette(
    val darkBackground: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val surfaceGlass: Color
)

object AlbumColorCache {
    private val cache = LruCache<String, AlbumPalette>(60)

    fun get(key: String): AlbumPalette? = cache.get(key)
    fun put(key: String, palette: AlbumPalette) {
        cache.put(key, palette)
    }
}

/**
 * Extracts dominant vibrant and dark muted colors from album artwork using Android Palette.
 * Caches results in-memory and returns animated colors for smooth track transitions.
 */
@Composable
fun rememberAlbumPalette(
    artworkUri: String?,
    songKey: String?
): AlbumPalette {
    val context = LocalContext.current
    val initialPalette = remember(artworkUri, songKey) {
        if (!artworkUri.isNullOrBlank()) {
            AlbumColorCache.get(artworkUri) ?: generateFallbackPalette(songKey ?: artworkUri)
        } else {
            generateFallbackPalette(songKey)
        }
    }
    var targetPalette by remember(artworkUri, songKey) { mutableStateOf(initialPalette) }

    LaunchedEffect(artworkUri, songKey) {
        if (!artworkUri.isNullOrBlank()) {
            val cached = AlbumColorCache.get(artworkUri)
            if (cached != null) {
                targetPalette = cached
                return@LaunchedEffect
            }

            val extracted = extractColorsFromUri(context, artworkUri)
            if (extracted != null) {
                AlbumColorCache.put(artworkUri, extracted)
                targetPalette = extracted
            } else {
                targetPalette = initialPalette
            }
        } else {
            targetPalette = initialPalette
        }
    }

    // Smooth 600ms transitions between track palettes
    val animDarkBg by animateColorAsState(
        targetValue = targetPalette.darkBackground,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "animDarkBg"
    )
    val animPrimary by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "animPrimary"
    )
    val animSecondary by animateColorAsState(
        targetValue = targetPalette.secondary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "animSecondary"
    )
    val animTertiary by animateColorAsState(
        targetValue = targetPalette.tertiary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "animTertiary"
    )
    val animAccent by animateColorAsState(
        targetValue = targetPalette.accent,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "animAccent"
    )
    val animGlass by animateColorAsState(
        targetValue = targetPalette.surfaceGlass,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "animGlass"
    )

    return AlbumPalette(
        darkBackground = animDarkBg,
        primary = animPrimary,
        secondary = animSecondary,
        tertiary = animTertiary,
        accent = animAccent,
        surfaceGlass = animGlass
    )
}

private suspend fun extractColorsFromUri(context: Context, uri: String): AlbumPalette? = withContext(Dispatchers.IO) {
    try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // Required for pixel access
            .size(96, 96) // Fast downsample for Android Palette extraction
            .build()

        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = result.drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
            return@withContext analyzeBitmapWithPalette(bitmap)
        }
    } catch (_: Exception) {
        // Safe graceful fallback on network timeout or invalid image format
    }
    null
}

/**
 * Uses Android Palette (androidx.palette.graphics.Palette) to extract swatches,
 * deriving dark warm background tones and vibrant accent highlights.
 */
private fun analyzeBitmapWithPalette(bitmap: Bitmap): AlbumPalette {
    val palette = Palette.from(bitmap).generate()

    val vibrant = palette.vibrantSwatch?.rgb
    val darkMuted = palette.darkMutedSwatch?.rgb
    val muted = palette.mutedSwatch?.rgb
    val darkVibrant = palette.darkVibrantSwatch?.rgb
    val dominant = palette.dominantSwatch?.rgb

    val seedColor = vibrant ?: darkVibrant ?: dominant ?: muted ?: 0xFF6366F1.toInt()
    val bgSeedColor = darkMuted ?: muted ?: darkVibrant ?: dominant ?: seedColor

    val hsl = FloatArray(3)

    // 1. Compute solid-looking, warm, desaturated, dark background color derived from artwork
    ColorUtils.colorToHSL(bgSeedColor, hsl)
    hsl[1] = hsl[1].coerceIn(0.15f, 0.40f) // Desaturate for warm solid background feel
    hsl[2] = hsl[2].coerceIn(0.08f, 0.16f) // Darken for high readability under glass
    val darkBackground = Color(ColorUtils.HSLToColor(hsl))

    // 2. Compute primary vibrant color
    ColorUtils.colorToHSL(seedColor, hsl)
    hsl[1] = hsl[1].coerceIn(0.60f, 0.95f)
    hsl[2] = hsl[2].coerceIn(0.40f, 0.65f)
    val primary = Color(ColorUtils.HSLToColor(hsl))

    // 3. Compute secondary color (from darkVibrant or swatch shift)
    val secSeed = darkVibrant ?: dominant ?: seedColor
    ColorUtils.colorToHSL(secSeed, hsl)
    if (secSeed == seedColor) {
        hsl[0] = (hsl[0] + 35f) % 360f
    }
    hsl[1] = hsl[1].coerceIn(0.45f, 0.90f)
    hsl[2] = hsl[2].coerceIn(0.30f, 0.55f)
    val secondary = Color(ColorUtils.HSLToColor(hsl))

    // 4. Compute tertiary glow color
    ColorUtils.colorToHSL(seedColor, hsl)
    hsl[0] = (hsl[0] + 180f) % 360f // Complementary ambient glow
    hsl[1] = 0.55f
    hsl[2] = 0.22f
    val tertiary = Color(ColorUtils.HSLToColor(hsl))

    val accent = primary.copy(alpha = 1f)
    val surfaceGlass = primary.copy(alpha = 0.15f)

    return AlbumPalette(
        darkBackground = darkBackground,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        accent = accent,
        surfaceGlass = surfaceGlass
    )
}

/**
 * Generates an aesthetic, deterministic ambient color scheme from song metadata
 * when no album art is loaded.
 */
private fun generateFallbackPalette(seed: String?): AlbumPalette {
    val hash = abs((seed ?: "USMAN_DEFAULT_ALBUM").hashCode())
    val presets = listOf(
        // Neon Indigo & Cyan
        AlbumPalette(
            darkBackground = Color(0xFF0D0F1E),
            primary = Color(0xFF6366F1),
            secondary = Color(0xFF00E5FF),
            tertiary = Color(0xFF1E1B4B),
            accent = Color(0xFF38BDF8),
            surfaceGlass = Color(0x226366F1)
        ),
        // Electric Violet & Rose
        AlbumPalette(
            darkBackground = Color(0xFF150A21),
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFFEC4899),
            tertiary = Color(0xFF2E1065),
            accent = Color(0xFFA855F7),
            surfaceGlass = Color(0x228B5CF6)
        ),
        // Emerald & Cyan Horizon
        AlbumPalette(
            darkBackground = Color(0xFF091322),
            primary = Color(0xFF06B6D4),
            secondary = Color(0xFF10B981),
            tertiary = Color(0xFF042F2E),
            accent = Color(0xFF34D399),
            surfaceGlass = Color(0x2206B6D4)
        ),
        // Sunset Amber & Terracotta
        AlbumPalette(
            darkBackground = Color(0xFF1F100B),
            primary = Color(0xFFF43F5E),
            secondary = Color(0xFFF59E0B),
            tertiary = Color(0xFF4C0519),
            accent = Color(0xFFFB7185),
            surfaceGlass = Color(0x22F43F5E)
        )
    )

    return presets[hash % presets.size]
}
