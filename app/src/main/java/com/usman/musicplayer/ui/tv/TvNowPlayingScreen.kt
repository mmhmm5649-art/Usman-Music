package com.usman.musicplayer.ui.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.usman.musicplayer.model.RepeatMode as AppRepeatMode
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel

@Composable
fun TvNowPlayingScreen(
    uiState: MusicPlayerUiState,
    viewModel: MusicPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val currentSong = uiState.currentSong

    if (currentSong == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No track currently playing. Select a song from Home or Search.",
                color = Color(0xFF94A3B8),
                fontSize = 18.sp
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "discRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left: Big Artwork Disc View
        Box(
            modifier = Modifier
                .size(270.dp)
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl glow backdrop
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0x3300E5FF), CircleShape)
            )

            // Album Artwork
            val artUri = currentSong.albumArtUri
            if (!artUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = currentSong.title,
                    modifier = Modifier
                        .size(240.dp)
                        .rotate(if (uiState.isPlaying) rotation else 0f)
                        .clip(CircleShape)
                        .border(3.dp, Color(0x8000E5FF), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TvFocusCyan,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            // Center Spindle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF050B14))
                    .border(2.dp, TvFocusCyan, CircleShape)
            )
        }

        // Center: Track Details & Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Bitrate & Source Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x3300E5FF))
                        .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (currentSong.isOnline) "320 KBPS AAC • JIOSAAVN HD" else "LOCAL HIGH FIDELITY",
                        color = TvFocusCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Track Title
            Text(
                text = currentSong.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Artist & Album
            Text(
                text = "${currentSong.artist} • ${currentSong.album}",
                color = Color(0xFFCBD5E1),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Slider
            val progress = if (uiState.durationMs > 0) {
                (uiState.currentPositionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TvFocusCyan,
                trackColor = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(uiState.currentPositionMs),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatMs(uiState.durationMs),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transport Control Buttons (D-pad focusable)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                TvControlButton(
                    icon = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    isActive = uiState.isShuffleEnabled,
                    onClick = { viewModel.toggleShuffle() }
                )

                // Previous
                TvControlButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    onClick = { viewModel.skipToPrevious() }
                )

                // Play / Pause (Large Primary)
                TvPlayPauseButton(
                    isPlaying = uiState.isPlaying,
                    onClick = { viewModel.togglePlayPause() }
                )

                // Next
                TvControlButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    onClick = { viewModel.skipToNext() }
                )

                // Repeat
                TvControlButton(
                    icon = if (uiState.repeatMode == AppRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    isActive = uiState.repeatMode != AppRepeatMode.OFF,
                    onClick = { viewModel.toggleRepeat() }
                )

                // Favorite
                TvControlButton(
                    icon = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    isActive = uiState.isFavorite,
                    activeColor = Color(0xFFF43F5E),
                    onClick = { viewModel.toggleFavorite(currentSong) }
                )
            }
        }

        // Right: Queue Panel
        val queueSongs = uiState.recentlyPlayed.ifEmpty { uiState.onlineTrendingSongs.ifEmpty { uiState.songs } }
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A101D))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "Upcoming Queue (${queueSongs.size})",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (queueSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(queueSongs, key = { "tv_q_${it.id}_${it.filePath}" }) { song ->
                        TvQueueItem(
                            song = song,
                            isCurrent = song.id == currentSong.id,
                            onClick = { viewModel.playSong(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeColor: Color = TvFocusCyan
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = CircleShape

    Box(
        modifier = Modifier
            .size(46.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) TvFocusCyan else if (isActive) activeColor else Color(0x33FFFFFF),
                shape = shape
            )
            .clip(shape)
            .background(if (isFocused) Color(0xFF0C2442) else Color(0xFF131D31))
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) TvFocusCyan else if (isActive) activeColor else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TvPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = CircleShape

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tvPlayPauseScale"
    )

    Box(
        modifier = Modifier
            .size(62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 1.5.dp,
                color = if (isFocused) Color.White else TvFocusCyan,
                shape = shape
            )
            .clip(shape)
            .background(Color(0xFF00E5FF))
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.Black,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun TvQueueItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) TvFocusCyan else Color.Transparent,
                shape = shape
            )
            .clip(shape)
            .background(
                when {
                    isFocused -> Color(0xFF0C2442)
                    isCurrent -> Color(0x3300E5FF)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = song.title,
            color = if (isCurrent || isFocused) TvFocusCyan else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
