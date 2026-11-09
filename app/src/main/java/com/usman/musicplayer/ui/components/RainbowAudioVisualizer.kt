package com.usman.musicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usman.musicplayer.playback.AudioSpectrumManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time Full-Width Rainbow Audio Waveform Visualizer
 *
 * Requirements:
 * - Uses the entire available horizontal width inside NowPlayingScreen and HomeScreen.
 * - Displays 48–80 frequency bars adapting seamlessly to screen width.
 * - Every part from far-left (bass) to center (mids/vocals) to far-right (treble/air) responds to real music.
 * - Continuous rainbow spectrum: Cyan -> Blue -> Purple -> Magenta -> Pink -> Orange -> Yellow -> Green -> Cyan.
 * - Glowing baseline and mirror reflection with vertical falloff.
 * - Smooth attack/release transitions and natural resting state when paused.
 */
@Composable
fun RainbowAudioVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    height: Dp = 80.dp,
    showGlow: Boolean = true,
    showReflection: Boolean = true
) {
    // Collect real-time FFT spectrum data from AudioSpectrumManager (64 bins)
    val spectrumData by AudioSpectrumManager.spectrumFlow.collectAsState()

    // Smooth resting animation transition when pausing/resuming
    val activeRatio = remember { Animatable(if (isPlaying) 1f else 0f) }
    LaunchedEffect(isPlaying) {
        activeRatio.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    // Dynamic wave phase for natural audio rhythm interpolation across resting & active states
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Pulse rhythm for glowing baseline
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.80f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    // Control FFT processing based on visualizer visibility to save CPU/Battery
    androidx.compose.runtime.DisposableEffect(Unit) {
        AudioSpectrumManager.isProcessingEnabled = true
        onDispose {
            AudioSpectrumManager.isProcessingEnabled = false
        }
    }

    // Complete continuous rainbow color palette across the full visualizer width
    val rainbowColors = remember {
        listOf(
            Color(0xFF00E5FF), // Cyan
            Color(0xFF2563EB), // Blue
            Color(0xFF7C3AED), // Purple
            Color(0xFFA855F7), // Magenta
            Color(0xFFEC4899), // Pink
            Color(0xFFFF5722), // Orange
            Color(0xFFFFD600), // Yellow
            Color(0xFF00E676), // Green
            Color(0xFF00E5FF)  // Smooth wrap back to Cyan
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0 || canvasHeight <= 0) return@Canvas

            val currentActive = activeRatio.value
            if (currentActive <= 0f && !isPlaying) return@Canvas

            val midY = canvasHeight * 0.50f
            val maxBarHeight = canvasHeight * 0.44f

            // Adapt bar count to available screen width (between 48 and 72 bars)
            val barSpacingTarget = 7.0f 
            val computedBars = (canvasWidth / barSpacingTarget).toInt().coerceIn(48, 72)
            val stepX = canvasWidth / (computedBars + 1)
            val barWidth = (stepX * 0.52f).coerceIn(2.5f, 5.0f)

            val isAmbientOnly = !spectrumData.any { it > 0.01f }

            for (i in 0 until computedBars) {
                val normX = i.toFloat() / (computedBars - 1).toFloat().coerceAtLeast(1f)
                val x = stepX * (i + 1)

                // 1. Color calculation (optimized interpolation)
                val colorPos = normX * (rainbowColors.size - 1)
                val colorIdx = colorPos.toInt().coerceIn(0, rainbowColors.size - 2)
                val fraction = colorPos - colorIdx
                val barColor = androidx.compose.ui.graphics.lerp(
                    rainbowColors[colorIdx],
                    rainbowColors[colorIdx + 1],
                    fraction
                )

                // 2. Magnitude calculation (Real FFT + Ambient idle)
                val fftPos = normX * (spectrumData.size - 1)
                val fftIndex = fftPos.toInt().coerceIn(0, spectrumData.size - 2)
                val fftFract = fftPos - fftIndex
                val realFftVal = spectrumData[fftIndex] * (1f - fftFract) + spectrumData[fftIndex + 1] * fftFract

                val bassHarmonic = sin(normX * Math.PI).toFloat()
                val midHarmonic = sin(normX * 8.0 + wavePhase).toFloat() * 0.12f
                val trebleHarmonic = cos(normX * 14.0 - wavePhase * 1.5).toFloat() * 0.10f
                val ambientIdle = (0.12f + bassHarmonic * 0.28f + midHarmonic + trebleHarmonic).coerceIn(0.05f, 0.65f)

                val combinedMagnitude = if (isAmbientOnly) {
                    ambientIdle * currentActive
                } else {
                    (realFftVal * 0.94f + ambientIdle * 0.06f) * currentActive
                }.coerceIn(0.04f, 1.0f)

                val barHalfHeight = maxBarHeight * combinedMagnitude

                // 3a. Soft outer glow behind the bar
                if (showGlow) {
                    drawLine(
                        color = barColor.copy(alpha = (0.28f * currentActive * pulseGlow).coerceIn(0.1f, 0.38f)),
                        start = Offset(x, midY - barHalfHeight * 1.15f),
                        end = Offset(x, midY + 1f),
                        strokeWidth = barWidth * 2.8f,
                        cap = StrokeCap.Round
                    )
                }

                // 3. Draw upper vertical frequency bar
                drawLine(
                    color = barColor,
                    start = Offset(x, midY - barHalfHeight),
                    end = Offset(x, midY + 1f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )

                // 4. Draw reflective glowing mirror (only if showReflection is enabled)
                if (showReflection) {
                    val reflectHeight = barHalfHeight * 0.65f
                    val topAlpha = (0.35f * currentActive * pulseGlow).coerceIn(0f, 0.45f)
                    
                    drawLine(
                        color = barColor.copy(alpha = topAlpha),
                        start = Offset(x, midY + 1f),
                        end = Offset(x, midY + reflectHeight * 0.5f),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = barColor.copy(alpha = topAlpha * 0.4f),
                        start = Offset(x, midY + reflectHeight * 0.5f),
                        end = Offset(x, midY + reflectHeight),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Baseline spanning the entire width (flat and clean when showGlow is false)
            val horizonBrush = Brush.horizontalGradient(rainbowColors)
            val baseAlpha = if (showGlow) {
                (0.40f * currentActive * pulseGlow).coerceIn(0.08f, 0.50f)
            } else {
                (0.25f * currentActive).coerceIn(0.05f, 0.35f)
            }
            drawLine(
                brush = horizonBrush,
                start = Offset(0f, midY),
                end = Offset(canvasWidth, midY),
                strokeWidth = if (showGlow) 1.5f else 1.0f,
                alpha = baseAlpha
            )
        }
    }
}
