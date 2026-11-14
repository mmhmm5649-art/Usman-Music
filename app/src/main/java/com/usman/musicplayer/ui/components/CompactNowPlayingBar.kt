package com.usman.musicplayer.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usman.musicplayer.R
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.theme.LiquidGlassBorderBrush
import com.usman.musicplayer.ui.theme.PlayPauseButtonBrush
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

private val MINI_PLAYER_PROGRESS_BRUSH = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF00E5FF),
        Color(0xFFA855F7),
        Color(0xFFEC4899)
    )
)

private val MINI_PLAYER_FOCUSED_BORDER_BRUSH = Brush.linearGradient(
    listOf(Color.White, Color(0xAAFFFFFF))
)

@Composable
fun CompactNowPlayingBar(
    currentSong: Song?,
    isPlaying: Boolean,
    onBarClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    playbackProgressFlow: StateFlow<PlaybackProgress>? = null,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    var isBarFocused by remember { mutableStateOf(false) }
    val barScale by animateFloatAsState(
        targetValue = if (isBarFocused) 1.025f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "miniPlayerFocusScale"
    )

    var isPrevFocused by remember { mutableStateOf(false) }
    var isPlayFocused by remember { mutableStateOf(false) }
    var isNextFocused by remember { mutableStateOf(false) }

    LiquidGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
            }
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x73000000))
            .onFocusChanged { isBarFocused = it.isFocused }
            .clickable(onClick = onBarClick)
            .focusable()
            .testTag("compact_now_playing_bar"),
        shape = RoundedCornerShape(22.dp),
        borderBrush = if (isBarFocused) MINI_PLAYER_FOCUSED_BORDER_BRUSH else LiquidGlassBorderBrush,
        backgroundColor = Color(0x38121626),
        borderWidth = if (isBarFocused) 2.dp else 1.dp,
        hazeState = hazeState,
        hazeBlurRadius = 24.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiniPlayerProgressLine(
                playbackProgressFlow = playbackProgressFlow,
                fallbackPositionMs = currentPositionMs,
                fallbackDurationMs = durationMs,
                songDurationMs = currentSong?.durationMs ?: 0L
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song Artwork / App Icon Thumbnail in Glass Frame
                if (currentSong != null) {
                    SongAlbumArtwork(
                        song = currentSong,
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 14.dp,
                        showGlowBorder = false
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "USMAN Logo",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Artist / Status with clean clipping and high readability
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentSong?.title ?: "USMAN MUSIC PLAYER",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSong?.artist ?: "No song playing",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Previous Button with TV D-pad focus support
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .onFocusChanged { isPrevFocused = it.isFocused }
                        .border(
                            width = if (isPrevFocused) 1.5.dp else 0.dp,
                            color = if (isPrevFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("compact_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = if (isPrevFocused) Color.White else TextPrimary.copy(alpha = 0.92f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Floating Play/Pause Glass Orb Button with TV focus indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .onFocusChanged { isPlayFocused = it.isFocused }
                        .border(
                            width = if (isPlayFocused) 2.dp else 0.dp,
                            color = if (isPlayFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(PlayPauseButtonBrush)
                        .clickable(onClick = onPlayPauseToggle)
                        .focusable()
                        .testTag("compact_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Next Button with TV D-pad focus support
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .onFocusChanged { isNextFocused = it.isFocused }
                        .border(
                            width = if (isNextFocused) 1.5.dp else 0.dp,
                            color = if (isNextFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("compact_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = if (isNextFocused) Color.White else TextPrimary.copy(alpha = 0.92f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressLine(
    playbackProgressFlow: StateFlow<PlaybackProgress>?,
    fallbackPositionMs: Long,
    fallbackDurationMs: Long,
    songDurationMs: Long
) {
    val progressState = playbackProgressFlow?.collectAsStateWithLifecycle()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
    ) {
        drawRect(color = Color(0x22FFFFFF))
        val live = progressState?.value
        val posMs = live?.currentPositionMs ?: fallbackPositionMs
        val durMs = (live?.durationMs ?: fallbackDurationMs).takeIf { it > 0L } ?: songDurationMs
        val fraction = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
        if (fraction > 0f) {
            drawRect(
                brush = MINI_PLAYER_PROGRESS_BRUSH,
                size = Size(width = size.width * fraction, height = size.height)
            )
        }
    }
}

