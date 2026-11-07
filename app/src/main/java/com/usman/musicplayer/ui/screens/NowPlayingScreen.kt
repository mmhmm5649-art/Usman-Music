package com.usman.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.R
import com.usman.musicplayer.data.repository.ExternalServiceType
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.RepeatMode
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.GlassButtonSurface
import com.usman.musicplayer.ui.components.GlassSlider
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.RainbowAudioVisualizer
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.*
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    uiState: MusicPlayerUiState,
    playbackProgress: com.usman.musicplayer.model.PlaybackProgress,
    onBackClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenService: (Context, Song, ExternalServiceType) -> Unit = { _, _, _ -> },
    onOpenEqualizer: () -> Unit = {},
    onAddToPlaylist: (Long, Song, Boolean, (Boolean, String) -> Unit) -> Unit = { _, _, _, _ -> },
    onCreatePlaylist: (String, Song?, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onPlayNext: (Song, (String) -> Unit) -> Unit = { _, _ -> },
    onAddToQueue: (Song, (String) -> Unit) -> Unit = { _, _ -> },
    onFetchLyrics: suspend (Song) -> String? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val song = uiState.currentSong
    LaunchedEffect(song) {
        android.util.Log.d("USMAN_PLAYBACK", "[Stage 8: Now Playing UI] CurrentSong ID: ${song?.id}, Title: '${song?.title}', Artist: '${song?.artist}', Album: '${song?.album}', Artwork: '${song?.albumArtUri}'")
    }
    val durationMs = if (playbackProgress.durationMs > 0) playbackProgress.durationMs else (song?.durationMs ?: 0L)
    val positionMs = playbackProgress.currentPositionMs

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderTempPosition by remember { mutableFloatStateOf(0f) }

    var showThreeDotSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var lyricsText by remember { mutableStateOf<String?>(null) }
    var isFetchingLyrics by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    val currentSliderVal = if (isDraggingSlider) {
        sliderTempPosition
    } else {
        if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    }

    // Auto-dismiss feedback snackbar
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3000L)
            feedbackMessage = null
        }
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

                // Floating Glass More Button (Three-Dot Menu)
                GlassButtonSurface(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_menu_btn"),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(onClick = { showThreeDotSheet = true }, modifier = Modifier.size(44.dp)) {
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

            // Large Album Artwork in a realistic Liquid Glass Frame
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
                GlassButtonSurface(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_queue_btn"),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x201A1E32)
                ) {
                    IconButton(onClick = { showAddToPlaylistSheet = true }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add to Playlist",
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

            // Real-time colorful full-width rainbow audio visualization
            RainbowAudioVisualizer(
                isPlaying = uiState.isPlaying,
                height = 76.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_rainbow_visualizer")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Music Progress Bar
            GlassSlider(
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

                // Central Play/Pause Liquid Glass Orb Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(elevation = 16.dp, shape = CircleShape, spotColor = Color(0x99A855F7))
                        .clip(CircleShape)
                        .background(PlayPauseButtonBrush)
                        .clickable(onClick = onPlayPauseToggle)
                        .drawBehind {
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

            // Volume Control
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
                GlassSlider(
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

        // Floating Liquid Glass Feedback Toast Banner
        AnimatedVisibility(
            visible = feedbackMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 24.dp, top = 12.dp, end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE61E2544))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = feedbackMessage ?: "",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // --- Three-Dot Menu Bottom Sheet ---
        if (showThreeDotSheet) {
            ModalBottomSheet(
                onDismissRequest = { showThreeDotSheet = false },
                containerColor = Color(0xF10F1322),
                scrimColor = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    // Header with Song Info Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongAlbumArtwork(
                            song = song,
                            modifier = Modifier.size(48.dp),
                            cornerRadius = 12.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song?.title ?: "Unknown Track",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song?.artist ?: "Unknown Artist",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Menu Options
                    MenuOptionRow(
                        icon = Icons.Default.PlaylistAdd,
                        title = "Add to Playlist",
                        tint = NeonCyan,
                        onClick = {
                            showThreeDotSheet = false
                            showAddToPlaylistSheet = true
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.QueuePlayNext,
                        title = "Play Next",
                        tint = ElectricBlue,
                        onClick = {
                            showThreeDotSheet = false
                            song?.let {
                                onPlayNext(it) { msg -> feedbackMessage = msg }
                            }
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.QueueMusic,
                        title = "Add to Queue",
                        tint = SoftCyan,
                        onClick = {
                            showThreeDotSheet = false
                            song?.let {
                                onAddToQueue(it) { msg -> feedbackMessage = msg }
                            }
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Equalizer,
                        title = "Equalizer",
                        tint = NeonPurple,
                        onClick = {
                            showThreeDotSheet = false
                            onOpenEqualizer()
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Info,
                        title = "Song Information",
                        tint = SoftPurple,
                        onClick = {
                            showThreeDotSheet = false
                            showSongInfoDialog = true
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.TextSnippet,
                        title = "Lyrics",
                        tint = NeonCyan,
                        onClick = {
                            showThreeDotSheet = false
                            showLyricsDialog = true
                            if (song != null) {
                                isFetchingLyrics = true
                                scope.launch {
                                    lyricsText = onFetchLyrics(song)
                                    isFetchingLyrics = false
                                }
                            }
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Share,
                        title = "Share Song",
                        tint = NeonMagenta,
                        onClick = {
                            showThreeDotSheet = false
                            song?.let { s ->
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Listening to \"${s.title}\" by ${s.artist} on USMAN MUSIC PLAYER!")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // --- Add to Playlist Bottom Sheet ---
        if (showAddToPlaylistSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddToPlaylistSheet = false },
                containerColor = Color(0xF10F1322),
                scrimColor = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Add to Playlist",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Create New Playlist Card
                    LiquidGlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showAddToPlaylistSheet = false
                                showCreatePlaylistDialog = true
                            },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x331E2544)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(NeonCyan, ElectricBlue))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Create New Playlist",
                                color = NeonCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.playlists.isEmpty()) {
                        Text(
                            text = "No playlists created yet. Create your first playlist above!",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.playlists) { playlist ->
                                LiquidGlassPanel(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            song?.let { s ->
                                                onAddToPlaylist(playlist.id, s, false) { success, msg ->
                                                    feedbackMessage = msg
                                                }
                                            }
                                            showAddToPlaylistSheet = false
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = Color(0x1F14182E)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x337C3AED)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.QueueMusic, null, tint = SoftCyan)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${playlist.songCount} songs",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // --- Create Playlist Dialog ---
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                containerColor = Color(0xF10F1322),
                title = {
                    Text("Create New Playlist", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("Enter a name for your playlist:", color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("My Favorite Tracks", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedContainerColor = Color(0x1AFFFFFF),
                                unfocusedContainerColor = Color(0x0DFFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName, song) { success, msg ->
                                    feedbackMessage = msg
                                }
                                newPlaylistName = ""
                                showCreatePlaylistDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White)
                    ) {
                        Text("Create & Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }

        // --- Song Info Dialog ---
        if (showSongInfoDialog && song != null) {
            AlertDialog(
                onDismissRequest = { showSongInfoDialog = false },
                containerColor = Color(0xF10F1322),
                title = {
                    Text("Song Details", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Title", song.title)
                        InfoRow("Artist", song.artist)
                        InfoRow("Album", song.album)
                        InfoRow("Duration", song.formattedDuration)
                        InfoRow("Source", if (song.isOnline) "Online Stream" else "Local Storage")
                        InfoRow("Path/URL", song.filePath)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSongInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // --- Lyrics Dialog ---
        if (showLyricsDialog && song != null) {
            AlertDialog(
                onDismissRequest = { showLyricsDialog = false },
                containerColor = Color(0xF10F1322),
                title = {
                    Column {
                        Text("Lyrics", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${song.title} - ${song.artist}", color = SoftCyan, fontSize = 11.sp)
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        if (isFetchingLyrics) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.align(Alignment.Center))
                        } else {
                            val text = lyricsText
                            if (text != null) {
                                LazyColumn {
                                    item {
                                        Text(
                                            text = text,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "No lyrics found for this track.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showLyricsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun MenuOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label.uppercase(), color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
