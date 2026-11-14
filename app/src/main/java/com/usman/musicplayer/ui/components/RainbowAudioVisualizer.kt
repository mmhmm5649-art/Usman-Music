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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usman.musicplayer.playback.AudioSpectrumManager
import kotlin.math.cos
import kotlin.math.sin

private val RAINBOW_VISUALIZER_COLORS = listOf(
    Color(0xFF00E5FF), // Cyan
    Color(0xFF2563EB), // Blue
    Color(0xFF7C3AED), // Purple
    Color(0xFFA855F7), // Magenta
    Color(0xFFEC4899), // Pink
    Color(0xFFFF5722), // Orange
    Color(0xFFFFD600), // Yellow
    Color(0xFF00E676), // Green
    Color(0xFF00E5FF)  // Wrap to Cyan
)

/**
 * Real-time Full-Width Rainbow Audio Waveform Visualizer with Water Mirror Reflection.
 * Uses single-pass per-bar math, cached gradient brushes, lifecycle-aware collection,
 * and zero 120Hz wakeups when paused.
 */
@Composable
fun RainbowAudioVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    height: Dp = 80.dp,
    showGlow: Boolean = true,
    showReflection: Boolean = true
) {
    val spectrumData by AudioSpectrumManager.spectrumFlow.collectAsStateWithLifecycle()

    val activeRatio = remember { Animatable(if (isPlaying) 1f else 0f) }
    LaunchedEffect(isPlaying) {
        activeRatio.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    val isAnimating = isPlaying || activeRatio.value > 0.005f
    val wavePhase: Float
    val pulseGlow: Float
    if (isAnimating) {
        val infiniteTransition = rememberInfiniteTransition(label = "audio_wave_anim")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        val animatedGlow by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseGlow"
        )
        wavePhase = animatedPhase
        pulseGlow = animatedGlow
    } else {
        wavePhase = 0f
        pulseGlow = 0.85f
    }

    DisposableEffect(isPlaying) {
        AudioSpectrumManager.isProcessingEnabled = isPlaying
        onDispose {
            AudioSpectrumManager.isProcessingEnabled = false
        }
    }

    val horizonBrush = remember { Brush.horizontalGradient(RAINBOW_VISUALIZER_COLORS) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

            val currentActive = activeRatio.value
            if (currentActive <= 0.001f && !isPlaying) return@Canvas

            val midY = canvasHeight * 0.42f
            val maxBarHeight = canvasHeight * 0.38f
            val reflectHeightMax = canvasHeight * 0.50f

            val computedBars = (canvasWidth / 6.0f).toInt().coerceIn(56, 80)
            val stepX = canvasWidth / (computedBars + 1)
            val barWidth = (stepX * 0.55f).coerceIn(2.0f, 4.5f)
            val glowStrokeWidth = barWidth * 2.5f
            val barDenom = (computedBars - 1).toFloat().coerceAtLeast(1f)
            val colorCount = RAINBOW_VISUALIZER_COLORS.size - 1
            val specLastIdx = (spectrumData.size - 1).coerceAtLeast(1)

            var isAmbientOnly = true
            for (v in spectrumData) {
                if (v > 0.01f) {
                    isAmbientOnly = false
                    break
                }
            }

            // Single pass per bar: computes color, FFT interpolation & harmonics once
            // and draws reflection, glow, and bar together.
            for (i in 0 until computedBars) {
                val normX = i.toFloat() / barDenom
                val x = stepX * (i + 1)

                val colorPos = normX * colorCount
                val colorIdx = colorPos.toInt().coerceIn(0, colorCount - 1)
                val barColor = lerp(
                    RAINBOW_VISUALIZER_COLORS[colorIdx],
                    RAINBOW_VISUALIZER_COLORS[colorIdx + 1],
                    colorPos - colorIdx
                )

                val fftPos = normX * specLastIdx
                val fftIndex = fftPos.toInt().coerceIn(0, (spectrumData.size - 2).coerceAtLeast(0))
                val fftFract = fftPos - fftIndex
                val realFftVal = if (spectrumData.size >= 2) {
                    spectrumData[fftIndex] * (1f - fftFract) + spectrumData[fftIndex + 1] * fftFract
                } else {
                    0f
                }

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

                if (showReflection) {
                    val reflectHeight = (barHalfHeight * 0.70f).coerceAtMost(reflectHeightMax)
                    val steps = 6
                    for (step in 0 until steps) {
                        val stepRatio1 = step.toFloat() / steps
                        val stepRatio2 = (step + 1).toFloat() / steps
                        val yStart = midY + reflectHeight * stepRatio1
                        val yEnd = midY + reflectHeight * stepRatio2
                        val ripple1 = sin(yStart * 0.12f + wavePhase * 2f) * 1.8f
                        val ripple2 = sin(yEnd * 0.12f + wavePhase * 2f) * 1.8f
                        val alpha = (0.28f * (1f - stepRatio1) * currentActive * pulseGlow).coerceIn(0f, 0.35f)

                        drawLine(
                            color = barColor.copy(alpha = alpha),
                            start = Offset(x + ripple1, yStart),
                            end = Offset(x + ripple2, yEnd),
                            strokeWidth = barWidth * (1f + stepRatio1 * 0.8f),
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (showGlow) {
                    drawLine(
                        color = barColor.copy(alpha = (0.30f * currentActive * pulseGlow).coerceIn(0.1f, 0.40f)),
                        start = Offset(x, midY - barHalfHeight * 1.15f),
                        end = Offset(x, midY + 1f),
                        strokeWidth = glowStrokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                drawLine(
                    color = barColor,
                    start = Offset(x, midY - barHalfHeight),
                    end = Offset(x, midY + 1f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }

            // Horizon baseline spanning the full width
            val baseAlpha = if (showGlow) {
                (0.45f * currentActive * pulseGlow).coerceIn(0.1f, 0.55f)
            } else {
                (0.30f * currentActive).coerceIn(0.08f, 0.40f)
            }
            drawLine(
                brush = horizonBrush,
                start = Offset(0f, midY),
                end = Offset(canvasWidth, midY),
                strokeWidth = if (showGlow) 1.8f else 1.2f,
                alpha = baseAlpha
            )
        }
    }
}
