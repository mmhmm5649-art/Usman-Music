package com.usman.musicplayer.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.LyricLine
import com.usman.musicplayer.model.LyricWord
import com.usman.musicplayer.model.LyricsParser
import com.usman.musicplayer.model.ParsedLyrics
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.AlbumPalette
import com.usman.musicplayer.ui.theme.TextMuted
import kotlinx.coroutines.isActive

/**
 * Dedicated TV Full-Screen Synchronized Lyrics View for Android TV / Google TV.
 * Uses large readable typography, album-art tinted dark background,
 * word-by-word synchronized highlighting driven by actual audio timestamps,
 * smooth scrolling, and remote control navigation.
 */
@Composable
fun TvFullLyricsScreen(
    song: Song?,
    parsedLyrics: ParsedLyrics?,
    playbackProgress: PlaybackProgress,
    isFetching: Boolean,
    fetchError: String?,
    palette: AlbumPalette,
    onSeekTo: (Long) -> Unit,
    onRetryFetch: () -> Unit,
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    playbackProgressFlow: kotlinx.coroutines.flow.StateFlow<PlaybackProgress>? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var scriptMode by remember {
        mutableStateOf(com.usman.musicplayer.ui.components.LyricsScriptPreferences.getScriptMode(context))
    }
    val activeLyrics = remember(parsedLyrics, scriptMode) {
        parsedLyrics?.withScriptMode(scriptMode)
    }
    val lines = activeLyrics?.lines.orEmpty()
    val isSynchronized = activeLyrics?.isSynchronized == true
    val listState = rememberLazyListState()

    val initialProgress = playbackProgressFlow?.value ?: playbackProgress
    // High-precision frame-clock for ultra-smooth word highlighting (never triggers parent recomposition)
    val drawClockState = remember { mutableFloatStateOf(initialProgress.interpolatedPositionMsFloat()) }
    val logicClockState = remember { mutableLongStateOf(initialProgress.currentPositionMs) }

    if (playbackProgressFlow != null) {
        val flowRef = remember { arrayOf(playbackProgressFlow.value) }
        var transportKey by remember {
            val initVal = playbackProgressFlow.value
            mutableStateOf(Triple(initVal.isPlaying, initVal.isBuffering, initVal.playbackSpeed))
        }

        LaunchedEffect(playbackProgressFlow) {
            playbackProgressFlow.collect { latest ->
                flowRef[0] = latest
                val nextKey = Triple(latest.isPlaying, latest.isBuffering, latest.playbackSpeed)
                if (nextKey != transportKey) {
                    transportKey = nextKey
                }
                if (!latest.isPlaying ||
                    latest.isBuffering ||
                    kotlin.math.abs(latest.currentPositionMs - logicClockState.longValue) > 250L
                ) {
                    drawClockState.floatValue = latest.currentPositionMs.toFloat()
                    logicClockState.longValue = latest.currentPositionMs
                }
            }
        }

        LaunchedEffect(transportKey) {
            val (playing, buffering, _) = transportKey
            if (!playing || buffering) {
                val cur = flowRef[0]
                drawClockState.floatValue = cur.currentPositionMs.toFloat()
                logicClockState.longValue = cur.currentPositionMs
                return@LaunchedEffect
            }
            while (isActive) {
                withFrameNanos {
                    val currentFloat = flowRef[0].interpolatedPositionMsFloat()
                    drawClockState.floatValue = currentFloat
                    val currentLong = currentFloat.toLong()
                    if (logicClockState.longValue != currentLong) {
                        logicClockState.longValue = currentLong
                    }
                }
            }
        }
    } else {
        LaunchedEffect(playbackProgress.isPlaying, playbackProgress.isBuffering, playbackProgress.playbackSpeed) {
            if (!playbackProgress.isPlaying || playbackProgress.isBuffering) {
                drawClockState.floatValue = playbackProgress.currentPositionMs.toFloat()
                logicClockState.longValue = playbackProgress.currentPositionMs
                return@LaunchedEffect
            }
            while (isActive) {
                withFrameNanos {
                    val currentFloat = playbackProgress.interpolatedPositionMsFloat()
                    drawClockState.floatValue = currentFloat
                    val currentLong = currentFloat.toLong()
                    if (logicClockState.longValue != currentLong) {
                        logicClockState.longValue = currentLong
                    }
                }
            }
        }

        LaunchedEffect(playbackProgress.currentPositionMs) {
            if (!playbackProgress.isPlaying ||
                playbackProgress.isBuffering ||
                kotlin.math.abs(playbackProgress.currentPositionMs - logicClockState.longValue) > 250L
            ) {
                drawClockState.floatValue = playbackProgress.currentPositionMs.toFloat()
                logicClockState.longValue = playbackProgress.currentPositionMs
            }
        }
    }

    // Active line index
    val activeIndex by remember(lines, isSynchronized) {
        derivedStateOf {
            if (isSynchronized) LyricsParser.findActiveIndex(lines, logicClockState.longValue) else -1
        }
    }

    var lastScrolledIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(parsedLyrics, isSynchronized) {
        lastScrolledIndex = -1
    }

    // Smooth auto-scroll keeping active lyric line centered
    LaunchedEffect(activeIndex) {
        if (isSynchronized && activeIndex >= 0 && activeIndex < lines.size) {
            if (activeIndex != lastScrolledIndex) {
                lastScrolledIndex = activeIndex
                val viewportHeight = listState.layoutInfo.viewportSize.height
                if (viewportHeight > 0) {
                    val centerOffset = -(viewportHeight * 2 / 5)
                    listState.animateScrollToItem(activeIndex, centerOffset)
                } else {
                    listState.animateScrollToItem(activeIndex)
                }
            }
        }
    }

    // TV Dark Album Art Gradient Background
    val bgGradient = remember(palette.darkBackground) {
        Brush.verticalGradient(
            colors = listOf(
                palette.darkBackground.copy(alpha = 0.95f),
                Color(0xFF090D16),
                Color(0xFF030508)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TV Header Bar: Back Button, Song Thumbnail & Info, Script Selector & Live Lyrics Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // TV Back Button
                    var isBackFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .border(
                                width = if (isBackFocused) 2.dp else 0.dp,
                                color = if (isBackFocused) palette.accent else Color.Transparent,
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .background(if (isBackFocused) palette.accent.copy(alpha = 0.3f) else Color(0x331E2338))
                            .clickable(onClick = onCloseLyrics),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to Player",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (song != null) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            SongAlbumArtwork(
                                song = song,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 12.dp,
                                showGlowBorder = false
                            )
                        }

                        Column {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = palette.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Script Selector (Original Script vs Romanized) & Live Lyrics Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    com.usman.musicplayer.ui.components.AmllScriptSelectorPill(
                        selectedMode = scriptMode,
                        onModeSelected = { newMode ->
                            scriptMode = newMode
                            com.usman.musicplayer.ui.components.LyricsScriptPreferences.setScriptMode(context, newMode)
                        },
                        palette = palette,
                        isTv = true
                    )

                    com.usman.musicplayer.ui.components.AmllSyncQualityBadge(
                        parsedLyrics = activeLyrics,
                        palette = palette,
                        isTv = true
                    )
                }
            }

            // Main Lyrics Content
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isFetching -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "Loading lyrics...",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    fetchError != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFF43F5E),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = fetchError,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onRetryFetch,
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.Black)
                                ) {
                                    Text("Retry Fetch", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    lines.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FormatQuote,
                                    contentDescription = null,
                                    tint = Color(0x66FFFFFF),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No lyrics available for this song",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onCloseLyrics,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x331E2338), contentColor = Color.White)
                                ) {
                                    Text("Return to Player", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tv_amll_lyrics_lazy_column"),
                            contentPadding = PaddingValues(vertical = 180.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            items(lines.size, key = { idx -> "${lines[idx].id}_${lines[idx].timestampMs}" }) { index ->
                                val line = lines[index]
                                val prevLineEndMs = if (index > 0) lines[index - 1].endTimestampMs.coerceAtLeast(lines[index - 1].timestampMs) else 0L
                                val isActive = isSynchronized && activeIndex >= 0 && line.id == activeIndex
                                val isPast = isSynchronized && activeIndex >= 0 && line.id < activeIndex
                                val isFuture = !isSynchronized || activeIndex < 0 || line.id > activeIndex

                                com.usman.musicplayer.ui.components.AmllLyricLineItem(
                                    line = line,
                                    prevLineEndMs = prevLineEndMs,
                                    isActive = isActive,
                                    isPast = isPast,
                                    isFuture = isFuture,
                                    isSynchronized = isSynchronized,
                                    drawClockState = drawClockState,
                                    palette = palette,
                                    isTv = true,
                                    onSeekTo = onSeekTo
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
    isSynchronized: Boolean,
    drawClockState: State<Float>,
    palette: AlbumPalette,
    onSeekTo: (Long) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val lineScale by animateFloatAsState(
        targetValue = if (isActive && isSynchronized) 1.05f else if (isFocused) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tvLyricScale_${line.id}"
    )

    val activeGlowRadius by animateFloatAsState(
        targetValue = if (isActive && isSynchronized) 14f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tvLyricGlow_${line.id}"
    )

    val shadow = if (activeGlowRadius > 0.5f) {
        Shadow(
            color = Color.White.copy(alpha = 0.35f),
            blurRadius = activeGlowRadius,
            offset = Offset.Zero
        )
    } else {
        Shadow.None
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) palette.accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color(0x331E2338) else Color.Transparent)
            .clickable {
                if (line.isTimestamped && line.timestampMs >= 0L) {
                    onSeekTo(line.timestampMs)
                }
            }
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        if (isSynchronized && line.words.isNotEmpty()) {
            val textMeasurer = rememberTextMeasurer()
            val textStyle = remember(line.isBackgroundVocal, isActive) {
                TextStyle(
                    fontSize = 30.sp, // Large TV typography
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontStyle = if (line.isBackgroundVocal) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 44.sp,
                    color = Color.Unspecified
                )
            }

            val textLayoutResult = remember(line.text, textStyle) {
                textMeasurer.measure(line.text, style = textStyle)
            }

            val wordPaths = remember(line.id, line.words, textLayoutResult) {
                line.words.associate { word ->
                    val lineTextLength = line.text.length
                    val charStart = word.charStart.coerceIn(0, lineTextLength)
                    val charEnd = word.charEnd.coerceIn(charStart, lineTextLength)
                    word.index to textLayoutResult.getPathForRange(charStart, charEnd)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { textLayoutResult.size.height.toDp() })
                    .drawWithContent {
                        val currentTime = drawClockState.value

                        val baseColor = when {
                            !isActive && isPast -> Color.White.copy(alpha = 0.65f)
                            !isActive && isFuture -> Color.White.copy(alpha = 0.35f)
                            line.isBackgroundVocal -> TextMuted.copy(alpha = 0.25f)
                            else -> TextMuted
                        }
                        drawText(textLayoutResult, color = baseColor)

                        if (isActive) {
                            val completedPath = Path()
                            var activeWord: LyricWord? = null
                            var activeWordPath: Path? = null

                            line.words.forEach { word ->
                                val isWordPast = currentTime >= word.endMs
                                val isWordActive = currentTime >= word.startMs && currentTime < word.endMs
                                val path = wordPaths[word.index]

                                if (path != null && !path.isEmpty) {
                                    if (isWordPast) {
                                        completedPath.addPath(path)
                                    } else if (isWordActive) {
                                        activeWord = word
                                        activeWordPath = path
                                    }
                                }
                            }

                            val highlightColor = if (line.isBackgroundVocal) Color.White.copy(alpha = 0.25f) else Color.White
                            if (!completedPath.isEmpty) {
                                clipPath(completedPath) { drawText(textLayoutResult, color = highlightColor) }
                            }

                            if (activeWord != null && activeWordPath != null) {
                                val bounds = activeWordPath!!.getBounds()
                                val wordDuration = (activeWord!!.endMs - activeWord!!.startMs).toDouble().coerceAtLeast(1.0)
                                val progress = ((currentTime.toDouble() - activeWord!!.startMs.toDouble()) / wordDuration).coerceIn(0.0, 1.0).toFloat()

                                clipPath(activeWordPath!!) {
                                    clipRect(bounds.left, bounds.top, bounds.left + bounds.width * progress, bounds.bottom) {
                                        drawText(textLayoutResult, color = highlightColor)
                                    }
                                }
                            }
                        }
                    }
            )
        } else {
            val targetTextColor = when {
                !isSynchronized -> Color.White.copy(alpha = 0.88f)
                isActive -> Color.White
                isPast -> Color.White.copy(alpha = 0.70f)
                else -> Color.White.copy(alpha = 0.40f)
            }

            val textColor by animateColorAsState(
                targetValue = targetTextColor,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "tvLyricColor_${line.id}"
            )

            Text(
                text = line.text,
                color = textColor,
                fontSize = 30.sp,
                fontWeight = if (isActive && isSynchronized) FontWeight.Bold else FontWeight.SemiBold,
                fontStyle = if (line.isBackgroundVocal) FontStyle.Italic else FontStyle.Normal,
                lineHeight = 44.sp,
                style = TextStyle(shadow = shadow),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
