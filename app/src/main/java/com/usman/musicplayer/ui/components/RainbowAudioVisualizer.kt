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
 * Real-time Full-Width Rainbow Audio Waveform Visualizer with Water Mirror Reflection.
 */
@Composable
fun RainbowAudioVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    height: Dp = 80.dp,
    showGlow: Boolean = true,
    showReflection: Boolean = true
) {
    val spectrumData by AudioSpectrumManager.spectrumFlow.collectAsState()

    val activeRatio = remember { Animatable(if (isPlaying) 1f else 0f) }
    LaunchedEffect(isPlaying) {
        activeRatio.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

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

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    androidx.compose.runtime.DisposableEffect(Unit) {
        AudioSpectrumManager.isProcessingEnabled = true
        onDispose {
            AudioSpectrumManager.isProcessingEnabled = false
        }
    }

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
            Color(0xFF00E5FF)  // Wrap to Cyan
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

            // Horizon / centerline where upper waveform and lower water reflection meet
            val midY = canvasHeight * 0.42f
            val maxBarHeight = canvasHeight * 0.38f

            val computedBars = (canvasWidth / 6.0f).toInt().coerceIn(56, 80)
            val stepX = canvasWidth / (computedBars + 1)
            val barWidth = (stepX * 0.55f).coerceIn(2.0f, 4.5f)

            val isAmbientOnly = !spectrumData.any { it > 0.01f }

            // 1. Draw water reflection first (beneath horizon)
            if (showReflection) {
                val reflectHeightMax = canvasHeight * 0.50f
                for (i in 0 until computedBars) {
                    val normX = i.toFloat() / (computedBars - 1).toFloat().coerceAtLeast(1f)
                    val x = stepX * (i + 1)

                    val colorPos = normX * (rainbowColors.size - 1)
                    val colorIdx = colorPos.toInt().coerceIn(0, rainbowColors.size - 2)
                    val fraction = colorPos - colorIdx
                    val barColor = androidx.compose.ui.graphics.lerp(
                        rainbowColors[colorIdx],
                        rainbowColors[colorIdx + 1],
                        fraction
                    )

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
            }

            // 2. Draw upper waveform bars and glow
            for (i in 0 until computedBars) {
                val normX = i.toFloat() / (computedBars - 1).toFloat().coerceAtLeast(1f)
                val x = stepX * (i + 1)

                val colorPos = normX * (rainbowColors.size - 1)
                val colorIdx = colorPos.toInt().coerceIn(0, rainbowColors.size - 2)
                val fraction = colorPos - colorIdx
                val barColor = androidx.compose.ui.graphics.lerp(
                    rainbowColors[colorIdx],
                    rainbowColors[colorIdx + 1],
                    fraction
                )

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

                if (showGlow) {
                    drawLine(
                        color = barColor.copy(alpha = (0.30f * currentActive * pulseGlow).coerceIn(0.1f, 0.40f)),
                        start = Offset(x, midY - barHalfHeight * 1.15f),
                        end = Offset(x, midY + 1f),
                        strokeWidth = barWidth * 2.5f,
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

            // 3. Horizon baseline spanning the full width
            val horizonBrush = Brush.horizontalGradient(rainbowColors)
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
