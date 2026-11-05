package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.ui.components.GlassButtonSurface
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.RainbowAudioVisualizer
import com.example.ui.components.SongAlbumArtwork
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassBorderBrush
import com.example.ui.theme.LiquidGlassButtonBorder
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PlayPauseButtonBrush
import com.example.ui.theme.SoftCyan
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MusicPlayerUiState

@Composable
fun NowPlayingScreen(
    uiState: MusicPlayerUiState,
    onBackClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val song = uiState.currentSong
    val durationMs = if (uiState.durationMs > 0) uiState.durationMs else (song?.durationMs ?: 0L)
    val positionMs = uiState.currentPositionMs

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderTempPosition by remember { mutableFloatStateOf(0f) }

    val currentSliderVal = if (isDraggingSlider) {
        sliderTempPosition
    } else {
        if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Soft Apple Music-style ambient lighting behind album artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.25f),
                            ElectricBlue.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        radius = 700f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Floating Glass Back, USMAN MUSIC branding, and More Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Floating Glass Back Button
                GlassButtonSurface(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_back_btn"),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Title & Subtitle matching Reference #3
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "USMAN MUSIC",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "— Now Playing —",
                        color = SoftCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp
                    )
                }

                // Floating Glass More Button
                GlassButtonSurface(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_menu_btn"),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large Album Artwork in a realistic Liquid Glass Frame with reflection
            Box(
                modifier = Modifier
                    .size(268.dp)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(36.dp), spotColor = Color(0x66000000))
                    .clip(RoundedCornerShape(36.dp))
                    .testTag("now_playing_artwork"),
                contentAlignment = Alignment.Center
            ) {
                SongAlbumArtwork(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 36.dp,
                    showGlowBorder = true
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Track title, artist, playlist queue & favorite glass buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating Glass Playlist Queue Button
                GlassButtonSurface(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_queue_btn"),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x201A1E32)
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Title and Artist in Center
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song?.title ?: "Select a song",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song?.artist ?: "Unknown Artist",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Floating Glass Favorite Heart Button
                GlassButtonSurface(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_favorite_btn"),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x201A1E32)
                ) {
                    IconButton(
                        onClick = { song?.let { onToggleFavorite(it) } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (uiState.isFavorite) NeonPink else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-time colorful rainbow audio visualization with mirror reflection (Reference 3 & 5)
            RainbowAudioVisualizer(
                isPlaying = uiState.isPlaying,
                barCount = 48,
                height = 70.dp,
                modifier = Modifier.testTag("audio_rainbow_visualizer")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Music Progress Bar with Apple-style Liquid Glass slider
            com.example.ui.components.GlassSlider(
                value = currentSliderVal,
                onValueChange = {
                    isDraggingSlider = true
                    sliderTempPosition = it
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    val targetMs = (sliderTempPosition * durationMs).toLong()
                    onSeekTo(targetMs)
                },
                modifier = Modifier.fillMaxWidth(),
                activeTrackColor = SoftPurple,
                inactiveTrackColor = Color(0x283A3F58),
                thumbColor = Color.White,
                testTag = "song_progress_slider"
            )

            // Current Position on Left vs Remaining Duration on Right
            val currentDisplayedMs = if (isDraggingSlider) (sliderTempPosition * durationMs).toLong() else positionMs
            val remainingMs = (durationMs - currentDisplayedMs).coerceAtLeast(0L)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentDisplayedMs),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "-${formatMs(remainingMs)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Controls Row:
            // [Shuffle] [Prev] [Play/Pause Glass Orb] [Next] [Repeat]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating Glass Shuffle Button
                GlassButtonSurface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = if (uiState.isShuffleEnabled) Color(0x3500E5FF) else Color(0x1F1E2338),
                    borderBrush = if (uiState.isShuffleEnabled) Brush.linearGradient(listOf(NeonCyan, SoftCyan)) else LiquidGlassButtonBorder
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("control_shuffle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState.isShuffleEnabled) NeonCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Floating Glass Previous Button
                GlassButtonSurface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("control_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Central Play/Pause Liquid Glass Orb Button with Ambient Glow
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(elevation = 16.dp, shape = CircleShape, spotColor = Color(0x99A855F7))
                        .clip(CircleShape)
                        .background(PlayPauseButtonBrush)
                        .clickable(onClick = onPlayPauseToggle)
                        .drawBehind {
                            // Top specular catch on glass sphere
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x55FFFFFF), Color.Transparent),
                                    center = Offset(size.width * 0.35f, size.height * 0.25f),
                                    radius = size.width * 0.45f
                                )
                            )
                        }
                        .testTag("control_play_pause_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Floating Glass Next Button
                GlassButtonSurface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("control_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Floating Glass Repeat Button
                val isRepeatActive = uiState.repeatMode != RepeatMode.OFF
                GlassButtonSurface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = if (isRepeatActive) Color(0x35A855F7) else Color(0x1F1E2338),
                    borderBrush = if (isRepeatActive) Brush.linearGradient(listOf(NeonPurple, SoftPurple)) else LiquidGlassButtonBorder
                ) {
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("control_repeat_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeatActive) SoftPurple else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Volume Control with Floating Glass Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "Volume Down",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.example.ui.components.GlassSlider(
                    value = uiState.volume,
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = {},
                    modifier = Modifier.weight(1f),
                    activeTrackColor = SoftCyan,
                    inactiveTrackColor = Color(0x28282C44),
                    thumbColor = Color.White,
                    testTag = "volume_slider"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume Up",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
