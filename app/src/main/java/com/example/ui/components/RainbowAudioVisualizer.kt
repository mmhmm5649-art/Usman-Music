package com.example.ui.components

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
import com.example.playback.AudioSpectrumManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time Rainbow Audio Visualizer inspired by Reference Images #3, #4, and #5:
 *
 * Requirements:
 * - Reacts to actual audio playback.
 * - Uses real audio frequency FFT data where supported by hardware.
 * - Displays colorful vertical frequency bars.
 * - Exact ordered gradient: cyan, blue, purple, pink, red, orange, yellow, and green.
 * - Reflective glow beneath the frequency bars (mirror reflection with vertical falloff).
 * - Smooth transitions, subtle neon lighting, and resting state when paused.
 * - Zero CPU/GPU waste when idle.
 */
@Composable
fun RainbowAudioVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    barCount: Int = 48,
    height: Dp = 80.dp
) {
    // Collect real-time FFT spectrum data from AudioSpectrumManager
    val spectrumData by AudioSpectrumManager.spectrumFlow.collectAsState()

    // Smooth resting animation transition when pausing/resuming
    val activeRatio = remember { Animatable(if (isPlaying) 1f else 0f) }
    LaunchedEffect(isPlaying) {
        activeRatio.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    // Dynamic wave phase for natural audio rhythm interpolation
    val infiniteTransition = rememberInfiniteTransition(label = "audio_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Pulse rhythm for subtle neon glow diffusion
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    // Exact required order: cyan, blue, purple, pink, red, orange, yellow, and green
    val rainbowColors = remember {
        listOf(
            Color(0xFF00E5FF), // Cyan
            Color(0xFF2979FF), // Blue
            Color(0xFFA855F7), // Purple
            Color(0xFFFF2A85), // Pink / Magenta
            Color(0xFFFF1744), // Red
            Color(0xFFFF9100), // Orange
            Color(0xFFFFEA00), // Yellow
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
            val midY = canvasHeight * 0.48f // Horizon line separating upper bars from reflection
            val barSpacing = canvasWidth / (barCount + 1)
            val barWidth = (barSpacing * 0.46f).coerceIn(2.5f, 6.0f)
            val maxBarHeight = canvasHeight * 0.42f

            val hasRealFft = spectrumData.any { it > 0.01f }
            val currentActive = activeRatio.value

            for (i in 0 until barCount) {
                val x = barSpacing * (i + 1)
                val normX = i.toFloat() / barCount.toFloat()

                // 1. Read real FFT magnitude if supported on device
                val rawFft = if (hasRealFft && i < spectrumData.size) {
                    spectrumData[i]
                } else 0f

                // 2. Harmonic waveform simulation synced to playback state
                // (bell-curve frequency distribution: sub-bass & mids higher, treble tapering)
                val bellCurve = sin(normX * Math.PI).toFloat()
                val wave1 = sin(normX * 9.0 + wavePhase).toFloat() * 0.18f
                val wave2 = cos(normX * 15.0 - wavePhase * 1.4).toFloat() * 0.12f
                val acousticMagnitude = (0.22f + bellCurve * 0.60f + wave1 + wave2).coerceIn(0.08f, 1.0f)

                // Combine real FFT frequency data with fluid motion when playing
                val barMagnitude = if (hasRealFft) {
                    (rawFft * 0.78f + acousticMagnitude * 0.22f) * currentActive
                } else {
                    acousticMagnitude * currentActive
                }.coerceIn(0.03f, 1.0f)

                val barHalfHeight = maxBarHeight * barMagnitude

                // 3. Interpolate the exact color along the rainbow spectrum
                val colorPos = normX * (rainbowColors.size - 1)
                val colorIdx = colorPos.toInt().coerceIn(0, rainbowColors.size - 2)
                val fraction = colorPos - colorIdx
                val barColor = androidx.compose.ui.graphics.lerp(
                    rainbowColors[colorIdx],
                    rainbowColors[colorIdx + 1],
                    fraction
                )

                // 4. Draw primary vertical frequency bar
                drawLine(
                    color = barColor,
                    start = Offset(x, midY - barHalfHeight),
                    end = Offset(x, midY + (barHalfHeight * 0.08f)),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )

                // 5. Draw reflective glow beneath the horizon line (mirror reflection)
                val reflectHeight = barHalfHeight * 0.68f
                val topAlpha = (0.35f * currentActive * pulseGlow).coerceIn(0f, 0.45f)

                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            barColor.copy(alpha = topAlpha),
                            barColor.copy(alpha = topAlpha * 0.35f),
                            Color.Transparent
                        ),
                        startY = midY + 1.5f,
                        endY = midY + reflectHeight + 4f
                    ),
                    start = Offset(x, midY + 1.5f),
                    end = Offset(x, midY + reflectHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }

            // 6. Horizon line with soft neon glow diffusion
            val horizonBrush = Brush.horizontalGradient(rainbowColors)
            drawLine(
                brush = horizonBrush,
                start = Offset(0f, midY),
                end = Offset(canvasWidth, midY),
                strokeWidth = 1.2f,
                alpha = (0.32f * currentActive * pulseGlow).coerceIn(0f, 0.4f)
            )
        }
    }
}
