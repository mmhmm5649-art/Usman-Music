package com.usman.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.LyricLine
import com.usman.musicplayer.model.LyricWord
import com.usman.musicplayer.model.ParsedLyrics
import android.content.Context
import androidx.compose.foundation.layout.defaultMinSize
import com.usman.musicplayer.model.LyricsScriptMode
import com.usman.musicplayer.model.SyncQuality
import com.usman.musicplayer.ui.theme.AlbumPalette
import com.usman.musicplayer.ui.theme.TextMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and broadcasts the user's selected [LyricsScriptMode] (Original Script vs Romanized)
 * across phone and Android TV lyrics screens without interrupting playback.
 */
object LyricsScriptPreferences {
    private const val PREFS_NAME = "usman_lyrics_script_prefs"
    private const val KEY_SCRIPT_MODE = "lyrics_script_mode"

    private val _scriptModeFlow = MutableStateFlow(LyricsScriptMode.ORIGINAL)
    val scriptModeFlow: StateFlow<LyricsScriptMode> = _scriptModeFlow.asStateFlow()

    @Volatile
    private var initialized = false

    fun getScriptMode(context: Context?): LyricsScriptMode {
        if (!initialized && context != null) {
            synchronized(this) {
                if (!initialized) {
                    try {
                        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val saved = prefs.getString(KEY_SCRIPT_MODE, null)
                        val parsed = LyricsScriptMode.entries.find { it.name == saved } ?: LyricsScriptMode.ORIGINAL
                        _scriptModeFlow.value = parsed
                        initialized = true
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        return _scriptModeFlow.value
    }

    fun setScriptMode(context: Context?, mode: LyricsScriptMode) {
        _scriptModeFlow.value = mode
        initialized = true
        if (context != null) {
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_SCRIPT_MODE, mode.name).apply()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Interactive script selector pill allowing users to switch instantaneously between:
 * - Original Script
 * - Romanized (English Letters)
 * Works seamlessly on both Android phones (touch) and Android TV (D-pad focus).
 */
@Composable
fun AmllScriptSelectorPill(
    selectedMode: LyricsScriptMode,
    onModeSelected: (LyricsScriptMode) -> Unit,
    palette: AlbumPalette,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(if (isTv) 20.dp else 14.dp))
            .background(Color(0x40111625))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(if (isTv) 20.dp else 14.dp))
            .padding(2.dp)
            .testTag("lyrics_script_selector"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LyricsScriptMode.entries.forEach { mode ->
            val isSelected = (mode == selectedMode)
            var isFocused by remember { mutableStateOf(false) }
            val tag = if (mode == LyricsScriptMode.ORIGINAL) {
                "lyrics_script_original_btn"
            } else {
                "lyrics_script_romanized_btn"
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = if (isTv) 36.dp else 28.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clip(RoundedCornerShape(if (isTv) 18.dp else 12.dp))
                    .background(
                        when {
                            isSelected -> palette.accent.copy(alpha = if (isTv) 0.34f else 0.28f)
                            isFocused -> Color.White.copy(alpha = 0.14f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (isFocused || isSelected) 1.dp else 0.dp,
                        color = when {
                            isFocused -> Color.White
                            isSelected -> palette.accent.copy(alpha = 0.80f)
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(if (isTv) 18.dp else 12.dp)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = if (isTv) 14.dp else 10.dp, vertical = if (isTv) 6.dp else 4.dp)
                    .testTag(tag),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTv) mode.displayName else mode.shortLabel,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = if (isTv) 12.sp else 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

/**
 * Honest sync-quality badge for AMLL / Apple Music-style lyrics.
 * Never claims word-level or syllable-level accuracy when only line-level LRC timestamps exist.
 */
@Composable
fun AmllSyncQualityBadge(
    parsedLyrics: ParsedLyrics?,
    palette: AlbumPalette,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val label = when {
        parsedLyrics == null -> ""
        parsedLyrics.hasSyllableSync -> if (isTv) "SYLLABLE-SYNCED LIVE LYRICS" else "SYLLABLE SYNC"
        parsedLyrics.syncQuality == SyncQuality.WORD ||
            parsedLyrics.syncQuality == SyncQuality.CHARACTER -> if (isTv) "WORD-SYNCED LIVE LYRICS" else "WORD SYNC"
        parsedLyrics.syncQuality == SyncQuality.LINE -> if (isTv) "LINE-SYNCED LIVE LYRICS" else "LINE SYNC"
        else -> "PLAIN LYRICS"
    }
    if (label.isEmpty()) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isTv) 20.dp else 14.dp))
            .background(palette.accent.copy(alpha = if (isTv) 0.20f else 0.16f))
            .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(if (isTv) 20.dp else 14.dp))
            .padding(horizontal = if (isTv) 16.dp else 10.dp, vertical = if (isTv) 8.dp else 4.dp)
            .testTag("amll_sync_quality_badge")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(if (isTv) 18.dp else 14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = if (isTv) 13.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
        }
    }
}

/**
 * AMLL / Apple Music 3-dot pulsing instrumental interlude indicator (• • •).
 * Appears during instrumental intros or gaps >= 5 seconds between vocal lines.
 */
@Composable
fun AmllInterludeDotsIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    drawClockState: State<Float>,
    palette: AlbumPalette,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val dotSize = if (isTv) 14.dp else 10.dp
    val spacing = if (isTv) 12.dp else 8.dp

    Row(
        modifier = modifier
            .padding(vertical = if (isTv) 10.dp else 6.dp, horizontal = 8.dp)
            .testTag("amll_interlude_dots"),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val currentMs = drawClockState.value
        val totalGap = (gapEndMs - gapStartMs).coerceAtLeast(1L).toFloat()
        val overallProgress = ((currentMs - gapStartMs.toFloat()) / totalGap).coerceIn(0f, 1f)

        for (dotIdx in 0..2) {
            val dotStart = dotIdx / 3f
            val dotEnd = (dotIdx + 1) / 3f
            val dotProgress = ((overallProgress - dotStart) / (dotEnd - dotStart)).coerceIn(0f, 1f)
            val alpha = 0.28f + (0.72f * dotProgress)
            val scale = 0.85f + (0.28f * kotlin.math.sin(dotProgress * Math.PI.toFloat()))

            Canvas(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = size.minDimension / 2f
                )
            }
        }
    }
}

/**
 * Native Jetpack Compose AMLL / Apple Music-style lyric line item supporting:
 * - Word-by-word progressive highlighting at actual singing timestamps
 * - Syllable-by-syllable progressive highlighting within words when syllable timestamps exist
 * - Soft-edge gradient reveal across the active word/syllable
 * - Completed highlight retention on already-sung words/syllables
 * - Dimmed upcoming words & lines
 * - Background vocal sub-lines & translation/romanization sub-lines
 * - Honest line-level fallback when only LRC line timestamps exist
 */
@Composable
fun AmllLyricLineItem(
    line: LyricLine,
    prevLineEndMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
    isSynchronized: Boolean,
    drawClockState: State<Float>,
    palette: AlbumPalette,
    isTv: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val targetScale = when {
        isActive && isSynchronized -> if (isTv) 1.05f else 1.035f
        isFocused && isTv -> 1.02f
        else -> 1.0f
    }

    val lineScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "amllLineScale_${line.id}"
    )

    val activeGlowRadius by animateFloatAsState(
        targetValue = if (isActive && isSynchronized) (if (isTv) 14f else 10f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "amllLineGlow_${line.id}"
    )

    val glowBucket = (activeGlowRadius * 2f).toInt()
    val shadow = remember(glowBucket, isTv) {
        if (glowBucket > 1) {
            Shadow(
                color = Color.White.copy(alpha = if (isTv) 0.35f else 0.28f),
                blurRadius = glowBucket / 2f,
                offset = Offset.Zero
            )
        } else {
            Shadow.None
        }
    }

    val hasInterludeGap = isSynchronized && line.isTimestamped && (line.timestampMs - prevLineEndMs) >= 5000L
    val hasInterludeBeforeLine by remember(hasInterludeGap, prevLineEndMs, line.timestampMs, drawClockState) {
        derivedStateOf {
            if (!hasInterludeGap) {
                false
            } else {
                val t = drawClockState.value
                t >= prevLineEndMs.toFloat() && t < line.timestampMs.toFloat()
            }
        }
    }

    val horizontalAlignment = if (line.isOppositeAligned) Alignment.End else Alignment.Start
    val transformOriginX = if (line.isOppositeAligned) 1f else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(transformOriginX, 0.5f)
            }
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isTv) {
                    Modifier.border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) palette.accent else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(if (isTv) 14.dp else 12.dp))
            .background(if (isFocused && isTv) Color(0x331E2338) else Color.Transparent)
            .clickable {
                if (line.isTimestamped && line.timestampMs >= 0L) {
                    onSeekTo(line.timestampMs)
                }
            }
            .padding(
                vertical = if (isTv) 8.dp else 6.dp,
                horizontal = if (isTv) 12.dp else 4.dp
            )
            .testTag("amll_lyric_line_${line.id}"),
        horizontalAlignment = horizontalAlignment
    ) {
        if (hasInterludeBeforeLine) {
            AmllInterludeDotsIndicator(
                gapStartMs = prevLineEndMs,
                gapEndMs = line.timestampMs,
                drawClockState = drawClockState,
                palette = palette,
                isTv = isTv
            )
        }

        val fontSize = if (isTv) 30.sp else 24.sp
        val lineHeight = if (isTv) 44.sp else 36.sp

        if (isSynchronized && line.words.isNotEmpty()) {
            AmllWordSyllableCanvasText(
                lineId = line.id,
                text = line.text,
                words = line.words,
                isActive = isActive,
                isPast = isPast,
                isFuture = isFuture,
                isBackgroundVocal = line.isBackgroundVocal,
                isOppositeAligned = line.isOppositeAligned,
                fontSize = fontSize,
                lineHeight = lineHeight,
                shadow = shadow,
                drawClockState = drawClockState
            )
        } else {
            // Line-level LRC or Plain Text line
            val targetTextColor = when {
                !isSynchronized -> Color.White.copy(alpha = 0.88f)
                isActive -> Color.White
                isPast -> Color.White.copy(alpha = 0.56f)
                else -> Color.White.copy(alpha = 0.34f)
            }

            val textColor by animateColorAsState(
                targetValue = targetTextColor,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "amllLineColor_${line.id}"
            )

            Text(
                text = line.text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = if (isActive && isSynchronized) FontWeight.Bold else FontWeight.SemiBold,
                fontStyle = if (line.isBackgroundVocal) FontStyle.Italic else FontStyle.Normal,
                lineHeight = lineHeight,
                textAlign = if (line.isOppositeAligned) TextAlign.End else TextAlign.Start,
                style = TextStyle(shadow = shadow),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional TTML background vocal sub-line (ttm:role="x-bg")
        if (!line.backgroundVocalText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            val bgFontSize = if (isTv) 21.sp else 17.sp
            val bgLineHeight = if (isTv) 30.sp else 24.sp
            if (isSynchronized && line.backgroundVocalWords.isNotEmpty()) {
                AmllWordSyllableCanvasText(
                    lineId = line.id * 10000 + 1,
                    text = line.backgroundVocalText,
                    words = line.backgroundVocalWords,
                    isActive = isActive,
                    isPast = isPast,
                    isFuture = isFuture,
                    isBackgroundVocal = true,
                    isOppositeAligned = line.isOppositeAligned,
                    fontSize = bgFontSize,
                    lineHeight = bgLineHeight,
                    shadow = Shadow.None,
                    drawClockState = drawClockState
                )
            } else {
                Text(
                    text = line.backgroundVocalText,
                    color = Color.White.copy(alpha = if (isActive) 0.68f else 0.32f),
                    fontSize = bgFontSize,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    lineHeight = bgLineHeight,
                    textAlign = if (line.isOppositeAligned) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Optional Romanization sub-line (ttm:role="x-roman")
        if (!line.romanizedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = line.romanizedText,
                color = Color.White.copy(alpha = if (isActive) 0.65f else 0.30f),
                fontSize = if (isTv) 18.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = if (isTv) 26.sp else 20.sp,
                textAlign = if (line.isOppositeAligned) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional Translation sub-line (ttm:role="x-translation")
        if (!line.translatedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = line.translatedText,
                color = Color.White.copy(alpha = if (isActive) 0.72f else 0.34f),
                fontSize = if (isTv) 19.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = if (isTv) 28.sp else 22.sp,
                textAlign = if (line.isOppositeAligned) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class CachedWordGeometry(
    val wordPath: Path,
    val wordBounds: Rect,
    val syllablePaths: Map<Int, Path>,
    val syllableBounds: Map<Int, Rect>
)

@Composable
private fun AmllWordSyllableCanvasText(
    lineId: Int,
    text: String,
    words: List<LyricWord>,
    isActive: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
    isBackgroundVocal: Boolean,
    isOppositeAligned: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    shadow: Shadow,
    drawClockState: State<Float>
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(isBackgroundVocal, isActive, isOppositeAligned, fontSize, lineHeight, shadow) {
        TextStyle(
            fontSize = fontSize,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            fontStyle = if (isBackgroundVocal) FontStyle.Italic else FontStyle.Normal,
            lineHeight = lineHeight,
            textAlign = if (isOppositeAligned) TextAlign.End else TextAlign.Start,
            shadow = shadow,
            color = Color.Unspecified
        )
    }

    val textLayoutResult: TextLayoutResult = remember(text, textStyle) {
        textMeasurer.measure(text, style = textStyle)
    }

    // Pre-cache word and syllable paths & bounds so drawing on every frame allocates zero new layout paths or rects
    val wordGeometries = remember(lineId, words, textLayoutResult) {
        val textLen = text.length
        words.associate { word ->
            val wStart = word.charStart.coerceIn(0, textLen)
            val wEnd = word.charEnd.coerceIn(wStart, textLen)
            val wPath = textLayoutResult.getPathForRange(wStart, wEnd)
            val wBounds = if (!wPath.isEmpty) wPath.getBounds() else Rect.Zero
            val sylPaths = if (word.syllables.size > 1) {
                word.syllables.associate { syl ->
                    val sStart = syl.charStartInLine.takeIf { it >= 0 }?.coerceIn(0, textLen)
                        ?: (wStart + syl.charStartInWord).coerceIn(0, textLen)
                    val sEnd = syl.charEndInLine.takeIf { it >= 0 }?.coerceIn(sStart, textLen)
                        ?: (wStart + syl.charEndInWord).coerceIn(sStart, textLen)
                    syl.index to textLayoutResult.getPathForRange(sStart, sEnd)
                }
            } else {
                emptyMap()
            }
            val sylBounds = if (sylPaths.isNotEmpty()) {
                sylPaths.mapValues { (_, p) -> if (!p.isEmpty) p.getBounds() else Rect.Zero }
            } else {
                emptyMap()
            }
            word.index to CachedWordGeometry(
                wordPath = wPath,
                wordBounds = wBounds,
                syllablePaths = sylPaths,
                syllableBounds = sylBounds
            )
        }
    }

    val completedPath = remember { Path() }
    val dimmedColor = remember(isActive, isPast, isFuture, isBackgroundVocal) {
        when {
            !isActive && isPast -> Color.White.copy(alpha = if (isBackgroundVocal) 0.42f else 0.56f)
            !isActive && isFuture -> Color.White.copy(alpha = if (isBackgroundVocal) 0.24f else 0.32f)
            isBackgroundVocal -> TextMuted.copy(alpha = 0.28f)
            else -> Color.White.copy(alpha = 0.34f)
        }
    }
    val highlightColor = remember(isBackgroundVocal) {
        if (isBackgroundVocal) Color.White.copy(alpha = 0.78f) else Color.White
    }
    val featherColors = remember(highlightColor) {
        listOf(highlightColor.copy(alpha = 0.85f), Color.Transparent)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { textLayoutResult.size.height.toDp() })
            .drawWithContent {
                val currentTime = drawClockState.value

                // 1. Base text color
                drawText(textLayoutResult, color = dimmedColor)

                // 2. Active line word-by-word and syllable-by-syllable illumination
                if (isActive) {
                    completedPath.reset()
                    var activeClipPath: Path? = null
                    var activeClipBounds: Rect = Rect.Zero
                    var activeProgress = 0f

                    for (word in words) {
                        val geom = wordGeometries[word.index] ?: continue
                        if (geom.wordPath.isEmpty) continue

                        if (currentTime >= word.endMs.toFloat()) {
                            completedPath.addPath(geom.wordPath)
                        } else if (currentTime >= word.startMs.toFloat()) {
                            if (word.syllables.size > 1 && geom.syllablePaths.isNotEmpty()) {
                                for (syl in word.syllables) {
                                    val sPath = geom.syllablePaths[syl.index] ?: continue
                                    if (sPath.isEmpty) continue
                                    if (currentTime >= syl.endMs.toFloat()) {
                                        completedPath.addPath(sPath)
                                    } else if (currentTime >= syl.startMs.toFloat()) {
                                        activeClipPath = sPath
                                        activeClipBounds = geom.syllableBounds[syl.index] ?: Rect.Zero
                                        activeProgress = syl.progressAt(currentTime)
                                        break
                                    } else {
                                        break
                                    }
                                }
                            } else {
                                activeClipPath = geom.wordPath
                                activeClipBounds = geom.wordBounds
                                activeProgress = word.progressAt(currentTime)
                            }
                        }
                    }

                    // 3. Render all completed words and completed syllables in bright white
                    if (!completedPath.isEmpty) {
                        clipPath(completedPath) {
                            drawText(textLayoutResult, color = highlightColor)
                        }
                    }

                    // 4. Render the currently singing word or syllable with AMLL soft-edge horizontal gradient
                    if (activeClipPath != null && activeProgress > 0f && activeClipBounds.width > 0f) {
                        val width = activeClipBounds.width.coerceAtLeast(1f)
                        val fillRight = activeClipBounds.left + width * activeProgress.coerceIn(0f, 1f)
                        val featherWidth = (width * 0.14f).coerceIn(4f, 18f)

                        clipPath(activeClipPath) {
                            clipRect(
                                left = activeClipBounds.left,
                                top = activeClipBounds.top,
                                right = fillRight,
                                bottom = activeClipBounds.bottom
                            ) {
                                drawText(textLayoutResult, color = highlightColor)
                            }
                            if (activeProgress < 0.98f && fillRight < activeClipBounds.right) {
                                val featherRight = (fillRight + featherWidth).coerceAtMost(activeClipBounds.right)
                                if (featherRight - fillRight >= 1f) {
                                    clipRect(
                                        left = fillRight,
                                        top = activeClipBounds.top,
                                        right = featherRight,
                                        bottom = activeClipBounds.bottom
                                    ) {
                                        drawText(
                                            textLayoutResult = textLayoutResult,
                                            brush = Brush.horizontalGradient(
                                                colors = featherColors,
                                                startX = fillRight,
                                                endX = featherRight
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    )
}
