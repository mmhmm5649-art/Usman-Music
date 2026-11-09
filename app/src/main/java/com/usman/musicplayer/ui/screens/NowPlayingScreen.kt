package com.usman.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.skydoves.cloudy.cloudy
import com.usman.musicplayer.data.AlbumArtworkCache
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import java.text.BreakIterator
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.State
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
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
import com.usman.musicplayer.model.LyricLine
import com.usman.musicplayer.model.LyricWord
import com.usman.musicplayer.model.LyricsParser
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.RepeatMode
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.GlassButtonSurface
import com.usman.musicplayer.ui.components.GlassSlider
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.RainbowAudioVisualizer
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.AlbumPalette
import com.usman.musicplayer.ui.theme.LiquidGlassButtonBorder
import com.usman.musicplayer.ui.theme.NeonPink
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import com.usman.musicplayer.ui.theme.rememberAlbumPalette
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Premium Apple Music-inspired Now Playing screen with dynamic album-color
 * liquid frosted-glass background, live synchronized lyrics, and tactile controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    uiState: MusicPlayerUiState,
    playbackProgress: PlaybackProgress,
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
    onDownloadSong: (Song) -> Unit = {},
    isExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember { com.usman.musicplayer.util.DeviceUtils.isTvDevice(context) }
    val scope = rememberCoroutineScope()
    val song = uiState.currentSong

    // 1. Dynamic album-colour extraction with animated cross-fading
    val albumPalette = rememberAlbumPalette(
        artworkUri = song?.albumArtUri,
        songKey = "${song?.title}_${song?.artist}"
    )

    val durationMs = if (playbackProgress.durationMs > 0) playbackProgress.durationMs else (song?.durationMs ?: 0L)
    val positionMs = playbackProgress.currentPositionMs

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderTempPosition by remember { mutableFloatStateOf(0f) }

    // Navigation and Modal dialog state
    var showLiveLyrics by remember { mutableStateOf(false) }

    // Intercept Back button to close lyrics first before leaving screen
    BackHandler(enabled = showLiveLyrics) {
        showLiveLyrics = false
    }

    // Always reset lyrics view to standard player interface every time Now Playing opens
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            showLiveLyrics = false
        }
    }
    var showThreeDotSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Synchronized lyrics state
    var parsedLyricsState by remember { mutableStateOf<com.usman.musicplayer.model.ParsedLyrics?>(null) }
    var isFetchingLyrics by remember { mutableStateOf(false) }
    var lyricsFetchError by remember { mutableStateOf<String?>(null) }

    // Reset lyrics state when song changes
    LaunchedEffect(song?.id) {
        parsedLyricsState = null
        lyricsFetchError = null
    }

    // Load lyrics when song changes or when lyrics view is toggled
    LaunchedEffect(song?.id, showLiveLyrics) {
        if (song != null && showLiveLyrics) {
            isFetchingLyrics = true
            lyricsFetchError = null
            android.util.Log.d("USMAN_LYRICS", "[NowPlayingScreen] Requesting lyrics for song: '${song.title}' (ID: ${song.filePath ?: song.id})")
            try {
                val raw = onFetchLyrics(song)
                if (raw != null) {
                    val parsed = LyricsParser.parse(raw, durationMs)
                    parsedLyricsState = parsed
                    android.util.Log.d("USMAN_LYRICS", "[NowPlayingScreen] Lyrics parsed successfully: count=${parsed.lines.size}, isSync=${parsed.isSynchronized}")
                } else {
                    parsedLyricsState = com.usman.musicplayer.model.ParsedLyrics(emptyList(), isSynchronized = false)
                    android.util.Log.d("USMAN_LYRICS", "[NowPlayingScreen] No lyrics content returned from provider for: '${song.title}'")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("USMAN_LYRICS", "[NowPlayingScreen] Error fetching lyrics: ${e.message}", e)
                lyricsFetchError = "Unable to fetch lyrics: ${e.localizedMessage ?: "Network error"}"
                parsedLyricsState = com.usman.musicplayer.model.ParsedLyrics(emptyList(), isSynchronized = false)
            }
            isFetchingLyrics = false
        }
    }

    val currentSliderVal = if (isDraggingSlider) {
        sliderTempPosition
    } else {
        if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3000L)
            feedbackMessage = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // --- 1. FULL-SCREEN ATMOSPHERIC ALBUM-ARTWORK BLUR BACKGROUND ---
        ThemeAlbumBackground(song = song, palette = albumPalette)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 680.dp)
                .align(Alignment.Center)
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP BAR: Back, Branding, Live Lyrics Toggle, Options Menu ---
            NowPlayingTopBar(
                palette = albumPalette,
                showLiveLyrics = showLiveLyrics,
                song = song,
                onBackClick = {
                    if (showLiveLyrics) {
                        showLiveLyrics = false
                    } else {
                        onBackClick()
                    }
                },
                onToggleLyrics = { showLiveLyrics = !showLiveLyrics },
                onOpenMenu = { showThreeDotSheet = true }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // --- CENTER VIEW: Animated Switch between Artwork and Live Lyrics ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLiveLyrics,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(380, easing = FastOutSlowInEasing)) +
                         scaleIn(initialScale = 0.93f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) +
                         slideInVertically(initialOffsetY = { it / 16 }))
                        .togetherWith(
                            fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.93f, animationSpec = tween(280)) +
                            slideOutVertically(targetOffsetY = { it / 16 })
                        )
                    },
                    label = "ArtworkOrLyricsTransition"
                ) { isLyricsMode ->
                    if (isLyricsMode) {
                        if (isTv) {
                            com.usman.musicplayer.ui.tv.TvFullLyricsScreen(
                                song = song,
                                parsedLyrics = parsedLyricsState,
                                playbackProgress = playbackProgress,
                                isFetching = isFetchingLyrics,
                                fetchError = lyricsFetchError,
                                palette = albumPalette,
                                onSeekTo = onSeekTo,
                                onRetryFetch = {
                                    scope.launch {
                                        if (song != null) {
                                            isFetchingLyrics = true
                                            lyricsFetchError = null
                                            try {
                                                val raw = onFetchLyrics(song)
                                                if (raw != null) {
                                                    parsedLyricsState = LyricsParser.parse(raw, durationMs)
                                                } else {
                                                    parsedLyricsState = com.usman.musicplayer.model.ParsedLyrics(emptyList(), isSynchronized = false)
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                lyricsFetchError = "Retry failed"
                                            }
                                            isFetchingLyrics = false
                                        }
                                    }
                                },
                                onCloseLyrics = { showLiveLyrics = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // LIVE SYNCHRONIZED LYRICS INTERFACE (PHONE)
                            SynchronizedLyricsView(
                                parsedLyrics = parsedLyricsState,
                                playbackProgress = playbackProgress,
                                isFetching = isFetchingLyrics,
                                fetchError = lyricsFetchError,
                                palette = albumPalette,
                                onSeekTo = onSeekTo,
                                onRetryFetch = {
                                    scope.launch {
                                        if (song != null) {
                                            isFetchingLyrics = true
                                            lyricsFetchError = null
                                            try {
                                                val raw = onFetchLyrics(song)
                                                if (raw != null) {
                                                    parsedLyricsState = LyricsParser.parse(raw, durationMs)
                                                } else {
                                                    parsedLyricsState = com.usman.musicplayer.model.ParsedLyrics(emptyList(), isSynchronized = false)
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                lyricsFetchError = "Retry failed"
                                            }
                                            isFetchingLyrics = false
                                        }
                                    }
                                },
                                onCloseLyrics = { showLiveLyrics = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // ALBUM ARTWORK & TRACK IDENTITY VIEW
                        AlbumArtworkTrackView(
                            song = song,
                            uiState = uiState,
                            palette = albumPalette,
                            onToggleFavorite = { song?.let { onToggleFavorite(it) } },
                            onAddToPlaylist = { showAddToPlaylistSheet = true },
                            onOpenEqualizer = onOpenEqualizer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- LOWER PLAYBACK SECTION (Grouped together and moved UP) ---
            RainbowAudioVisualizer(
                isPlaying = uiState.isPlaying,
                height = 76.dp,
                showGlow = true,
                showReflection = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_rainbow_visualizer")
            )

            Spacer(modifier = Modifier.height(3.dp))

            // --- SLIDER & TIME DISPLAY ---
            val currentDisplayedMs = if (isDraggingSlider) (sliderTempPosition * durationMs).toLong() else positionMs
            val remainingMs = (durationMs - currentDisplayedMs).coerceAtLeast(0L)

            NowPlayingProgressBar(
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
                testTag = "song_progress_slider"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentDisplayedMs),
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "-${formatMs(remainingMs)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- PLAYBACK CONTROLS ROW ---
            PlaybackControlsRow(
                uiState = uiState,
                palette = albumPalette,
                onToggleShuffle = onToggleShuffle,
                onSkipPrevious = onSkipPrevious,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipNext = onSkipNext,
                onToggleRepeat = onToggleRepeat
            )

            Spacer(modifier = Modifier.height(52.dp))


        }

        // --- FLOATING FEEDBACK BANNER ---
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
                    .background(Color(0xE60F172A))
                    .border(1.dp, albumPalette.accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = feedbackMessage.orEmpty(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // --- THREE-DOT MENU MODAL BOTTOM SHEET ---
        if (showThreeDotSheet) {
            ModalBottomSheet(
                onDismissRequest = { showThreeDotSheet = false },
                containerColor = Color(0xF2080D1A),
                scrimColor = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Options",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    MenuOptionRow(
                        icon = Icons.Default.PlaylistAdd,
                        title = "Add to Playlist",
                        tint = albumPalette.accent,
                        onClick = {
                            showThreeDotSheet = false
                            showAddToPlaylistSheet = true
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.QueueMusic,
                        title = "Play Next",
                        tint = Color(0xFF38BDF8),
                        onClick = {
                            showThreeDotSheet = false
                            song?.let { onPlayNext(it) { msg -> feedbackMessage = msg } }
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Download,
                        title = "Download for Offline",
                        tint = Color(0xFF00E676),
                        onClick = {
                            showThreeDotSheet = false
                            song?.let {
                                onDownloadSong(it)
                                feedbackMessage = "Started download for '${it.title}'"
                            }
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.FormatQuote,
                        title = if (showLiveLyrics) "Show Album Artwork" else "Show Synchronized Lyrics",
                        tint = albumPalette.accent,
                        onClick = {
                            showThreeDotSheet = false
                            showLiveLyrics = !showLiveLyrics
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Equalizer,
                        title = "Audio Effects & Equalizer",
                        tint = Color(0xFFA855F7),
                        onClick = {
                            showThreeDotSheet = false
                            onOpenEqualizer()
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Info,
                        title = "Song Information",
                        tint = Color(0xFF60A5FA),
                        onClick = {
                            showThreeDotSheet = false
                            showSongInfoDialog = true
                        }
                    )

                    MenuOptionRow(
                        icon = Icons.Default.Share,
                        title = "Share Song",
                        tint = Color(0xFFF43F5E),
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

        // --- ADD TO PLAYLIST BOTTOM SHEET ---
        if (showAddToPlaylistSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddToPlaylistSheet = false },
                containerColor = Color(0xF2080D1A),
                scrimColor = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Add to Playlist",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

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
                                    .background(albumPalette.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.Black)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Create New Playlist",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.playlists.isEmpty()) {
                        Text(
                            text = "No custom playlists found.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(uiState.playlists) { playlist ->
                                PlaylistItemRow(
                                    playlist = playlist,
                                    palette = albumPalette,
                                    onClick = {
                                        showAddToPlaylistSheet = false
                                        song?.let { s ->
                                            onAddToPlaylist(playlist.id, s, false) { success, msg ->
                                                feedbackMessage = msg
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // --- CREATE PLAYLIST DIALOG ---
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                containerColor = Color(0xF50A1020),
                title = { Text("Create Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = albumPalette.accent,
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName.trim(), song) { _, msg ->
                                    feedbackMessage = msg
                                }
                                newPlaylistName = ""
                                showCreatePlaylistDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = albumPalette.accent, contentColor = Color.Black)
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showCreatePlaylistDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- SONG INFO DIALOG ---
        if (showSongInfoDialog && song != null) {
            AlertDialog(
                onDismissRequest = { showSongInfoDialog = false },
                containerColor = Color(0xF50A1020),
                title = { Text("Song Information", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Title", song.title)
                        InfoRow("Artist", song.artist)
                        InfoRow("Album", song.album)
                        InfoRow("Duration", formatMs(song.durationMs))
                        InfoRow("Quality", if (song.isOnline) "320 KBPS AAC HD" else "Local Storage")
                        InfoRow("Source", song.sourceProvider ?: "JioSaavn")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSongInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = albumPalette.accent, contentColor = Color.Black)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// SUB-COMPONENTS
// -------------------------------------------------------------------------------------------------

/**
 * Authentic Apple Music-style full-screen heavily blurred album artwork background.
 * Uses the current song's album artwork enlarged across the entire screen,
 * heavily blurred into smooth natural color fields with smooth track crossfades.
 */
@Composable
private fun ThemeAlbumBackground(
    song: Song?,
    palette: AlbumPalette
) {
    val context = LocalContext.current
    var backgroundBitmap by remember(song?.id) {
        mutableStateOf<Bitmap?>(song?.let { AlbumArtworkCache.get("art_${it.id}_512") })
    }

    LaunchedEffect(song?.id, song?.albumArtUri, song?.filePath) {
        if (song != null) {
            backgroundBitmap = AlbumArtworkCache.loadArtwork(
                context = context,
                songId = song.id,
                albumArtUri = song.albumArtUri,
                filePath = song.filePath,
                targetWidth = 512,
                targetHeight = 512
            )
        } else {
            backgroundBitmap = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Unified Background: Palette-based gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.primary,
                            palette.secondary,
                            palette.darkBackground,
                            palette.darkBackground // Extra stop for smoother transition
                        )
                    )
                )
        )

        // 2. Full-screen heavily blurred album artwork
        val bmp = backgroundBitmap
        if (bmp != null) {
            Crossfade(
                targetState = bmp,
                animationSpec = tween(500),
                label = "AlbumBackgroundBlurCrossfade"
            ) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .cloudy(radius = 45)
                )
            }
        }

        // 3. Smooth dark vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x33000000),
                            Color(0x00000000),
                            Color(0xAA000000)
                        )
                    )
                )
        )
    }
}

/**
 * Focus-aware Liquid Glass control button supporting both mobile touch
 * and Android TV / Google TV remote D-pad navigation with Apple-style focus indicators.
 */
@Composable
private fun NowPlayingControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    backgroundColor: Color = Color(0x281E2338),
    focusedBorderColor: Color = Color.White,
    testTag: String = "",
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "controlBtnScale"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) focusedBorderColor else Color.Transparent,
                shape = shape
            )
            .clip(shape)
            .background(if (isFocused) backgroundColor.copy(alpha = 0.6f) else backgroundColor)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun NowPlayingTopBar(
    palette: AlbumPalette,
    showLiveLyrics: Boolean,
    song: Song?,
    onBackClick: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button & Small Artwork Thumbnail when in Lyrics view
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            NowPlayingControlButton(
                onClick = onBackClick,
                size = 44.dp,
                cornerRadius = 14.dp,
                backgroundColor = Color(0x331E2338),
                testTag = "now_playing_back_btn"
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showLiveLyrics && song != null) {
                // Small album artwork thumbnail at the top-left with song title and artist
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleLyrics)
                        .padding(end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        SongAlbumArtwork(
                            song = song,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 10.dp,
                            showGlowBorder = false
                        )
                    }
                    Column {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Center Title & Subtitle (shown when in Artwork view)
        if (!showLiveLyrics) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "USMAN MUSIC",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "Now Playing",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Right Actions: Live Lyrics Toggle & Options Menu
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NowPlayingControlButton(
                onClick = onToggleLyrics,
                size = 44.dp,
                cornerRadius = 14.dp,
                backgroundColor = if (showLiveLyrics) palette.accent.copy(alpha = 0.35f) else Color(0x331E2338),
                focusedBorderColor = palette.accent,
                testTag = "now_playing_lyrics_btn"
            ) {
                Icon(
                    imageVector = Icons.Default.FormatQuote,
                    contentDescription = "Lyrics",
                    tint = if (showLiveLyrics) Color.White else Color(0xFFCBD5E1),
                    modifier = Modifier.size(22.dp)
                )
            }

            NowPlayingControlButton(
                onClick = onOpenMenu,
                size = 44.dp,
                cornerRadius = 14.dp,
                backgroundColor = Color(0x331E2338),
                testTag = "now_playing_menu_btn"
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumArtworkTrackView(
    song: Song?,
    uiState: MusicPlayerUiState,
    palette: AlbumPalette,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Large Artwork Frame (Clean, Sharp Artwork)
        Box(
            modifier = Modifier
                .size(260.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color(0x77000000)
                )
                .clip(RoundedCornerShape(24.dp))
                .testTag("now_playing_artwork"),
            contentAlignment = Alignment.Center
        ) {
            SongAlbumArtwork(
                song = song,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 24.dp,
                showGlowBorder = false
            )
        }

        // Track Identity & Action Badges
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = song?.title ?: "Select a song",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${song?.artist ?: "Unknown Artist"} • ${song?.album ?: "Single"}",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio Quality Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x281E2338))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (song?.isOnline == true) "320 KBPS AAC • JIOSAAVN HD" else "LOCAL HIGH-RES AUDIO",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Action Buttons Row (Clean Theme Buttons with D-pad remote focus)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NowPlayingControlButton(
                onClick = onAddToPlaylist,
                size = 44.dp,
                cornerRadius = 14.dp,
                backgroundColor = Color(0x281E2338)
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(20.dp)
                )
            }

            NowPlayingControlButton(
                onClick = onOpenEqualizer,
                size = 44.dp,
                cornerRadius = 14.dp,
                backgroundColor = Color(0x281E2338),
                focusedBorderColor = palette.accent
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Equalizer",
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            NowPlayingControlButton(
                onClick = onToggleFavorite,
                size = 44.dp,
                cornerRadius = 14.dp,
                backgroundColor = if (uiState.isFavorite) Color(0x35F43F5E) else Color(0x281E2338),
                focusedBorderColor = NeonPink
            ) {
                Icon(
                    imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (uiState.isFavorite) NeonPink else Color(0xFFCBD5E1),
                    modifier = Modifier.size(20.dp)
                )
            }
        }


    }
}

/**
 * Live synchronized Apple Music-style lyrics view.
 */
@Composable
private fun SynchronizedLyricsView(
    parsedLyrics: com.usman.musicplayer.model.ParsedLyrics?,
    playbackProgress: PlaybackProgress,
    isFetching: Boolean,
    fetchError: String?,
    palette: AlbumPalette,
    onSeekTo: (Long) -> Unit,
    onRetryFetch: () -> Unit,
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = parsedLyrics?.lines.orEmpty()
    val isSynchronized = parsedLyrics?.isSynchronized == true
    val listState = rememberLazyListState()

    // 1. High-precision sub-millisecond frame-clock for ultra-smooth drawing
    val drawClockState = remember { mutableFloatStateOf(playbackProgress.interpolatedPositionMsFloat()) }
    // 2. Stable millisecond clock for line-switching logic (reduces re-evaluations)
    val logicClockState = remember { mutableLongStateOf(playbackProgress.currentPositionMs) }

    LaunchedEffect(playbackProgress.isPlaying) {
        if (!playbackProgress.isPlaying) {
            drawClockState.floatValue = playbackProgress.currentPositionMs.toFloat()
            logicClockState.longValue = playbackProgress.currentPositionMs
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos {
                val currentFloat = playbackProgress.interpolatedPositionMsFloat()
                drawClockState.floatValue = currentFloat
                // Only update logic clock if a millisecond has actually passed to keep line-switching stable
                val currentLong = currentFloat.toLong()
                if (logicClockState.longValue != currentLong) {
                    logicClockState.longValue = currentLong
                }
            }
        }
    }

    LaunchedEffect(playbackProgress.currentPositionMs) {
        if (kotlin.math.abs(playbackProgress.currentPositionMs - logicClockState.longValue) > 500L) {
            drawClockState.floatValue = playbackProgress.currentPositionMs.toFloat()
            logicClockState.longValue = playbackProgress.currentPositionMs
        }
    }

    // 3. Active index calculation driven by logic clock (stable millisecond accuracy)
    val activeIndex by remember(lines, isSynchronized) {
        derivedStateOf {
            if (isSynchronized) LyricsParser.findActiveIndex(lines, logicClockState.longValue) else -1
        }
    }

    // 3. Manual scroll tracking state (pause auto-scroll for 4.5s on touch drag)
    var isManualScrolled by remember { mutableStateOf(false) }
    var manualScrollPauseUntil by remember { mutableLongStateOf(0L) }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isManualScrolled = true
            manualScrollPauseUntil = System.currentTimeMillis() + 4500L
        }
    }

    // Timer to resume auto-scrolling after 4.5s idle
    LaunchedEffect(isManualScrolled) {
        if (isManualScrolled) {
            while (System.currentTimeMillis() < manualScrollPauseUntil) {
                delay(200L)
            }
            if (!isDragged) {
                isManualScrolled = false
            }
        }
    }

    var lastScrolledIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(parsedLyrics, isSynchronized) {
        lastScrolledIndex = -1
    }

    // Smooth auto-scroll to keep current singing line centered near middle
    LaunchedEffect(activeIndex, isManualScrolled) {
        if (!isManualScrolled && isSynchronized && activeIndex >= 0 && activeIndex < lines.size) {
            if (activeIndex != lastScrolledIndex) {
                lastScrolledIndex = activeIndex
                // Calculate an offset to center the item slightly above middle (approx 2/5 from top)
                val viewportHeight = listState.layoutInfo.viewportSize.height
                if (viewportHeight > 0) {
                    val centerOffset = -(viewportHeight * 2 / 5)
                    listState.animateScrollToItem(activeIndex, centerOffset)
                } else {
                    // Fallback for first scroll if height not yet measured
                    listState.animateScrollToItem(activeIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            isFetching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(38.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading lyrics...",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
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
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = fetchError,
                            color = Color(0xFFCBD5E1),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetryFetch,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.Black)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No lyrics available for this song",
                            color = Color(0xFFCBD5E1),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCloseLyrics,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x331E2338), contentColor = Color.White)
                        ) {
                            Text("Return to Artwork", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Badge for Unsynchronized Plain Lyrics
                    if (!isSynchronized) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33F59E0B))
                                    .border(1.dp, Color(0x88F59E0B), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "PLAIN LYRICS • NOT SYNCHRONIZED",
                                    color = Color(0xFFFDE047),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 140.dp, horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(lines, key = { "${it.id}_${it.timestampMs}" }) { line ->
                                val isActive = isSynchronized && activeIndex >= 0 && line.id == activeIndex
                                val isPast = isSynchronized && activeIndex >= 0 && line.id < activeIndex
                                val isFuture = !isSynchronized || activeIndex < 0 || line.id > activeIndex

                                LyricLineItem(
                                    line = line,
                                    isActive = isActive,
                                    isPast = isPast,
                                    isFuture = isFuture,
                                    isSynchronized = isSynchronized,
                                    logicClockState = logicClockState,
                                    drawClockState = drawClockState,
                                    palette = palette,
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
private fun PlaybackControlsRow(
    uiState: MusicPlayerUiState,
    palette: AlbumPalette,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        NowPlayingControlButton(
            onClick = onToggleShuffle,
            size = 46.dp,
            cornerRadius = 16.dp,
            backgroundColor = if (uiState.isShuffleEnabled) palette.accent.copy(alpha = 0.35f) else Color(0x281E2338),
            focusedBorderColor = palette.accent,
            testTag = "control_shuffle_btn"
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (uiState.isShuffleEnabled) palette.accent else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }

        // Previous Button
        NowPlayingControlButton(
            onClick = onSkipPrevious,
            size = 52.dp,
            cornerRadius = 18.dp,
            backgroundColor = Color(0x281E2338),
            testTag = "control_prev_btn"
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Track",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play/Pause Button
        NowPlayingControlButton(
            onClick = onPlayPauseToggle,
            size = 52.dp,
            cornerRadius = 18.dp,
            backgroundColor = Color(0x281E2338),
            testTag = "control_play_pause_btn"
        ) {
            Icon(
                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Next Button
        NowPlayingControlButton(
            onClick = onSkipNext,
            size = 52.dp,
            cornerRadius = 18.dp,
            backgroundColor = Color(0x281E2338),
            testTag = "control_next_btn"
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Track",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Repeat Button
        val isRepeatActive = uiState.repeatMode != RepeatMode.OFF
        NowPlayingControlButton(
            onClick = onToggleRepeat,
            size = 46.dp,
            cornerRadius = 16.dp,
            backgroundColor = if (isRepeatActive) palette.accent.copy(alpha = 0.35f) else Color(0x281E2338),
            focusedBorderColor = palette.accent,
            testTag = "control_repeat_btn"
        ) {
            Icon(
                imageVector = if (uiState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = if (isRepeatActive) palette.accent else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaylistItemRow(
    playlist: Playlist,
    palette: AlbumPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.QueueMusic,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MenuOptionRow(
    icon: ImageVector,
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
        Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun String.getGraphemes(): List<String> {
    if (this.isEmpty()) return emptyList()
    val graphemes = mutableListOf<String>()
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var start = iterator.first()
    var end = iterator.next()
    while (end != java.text.BreakIterator.DONE) {
        graphemes.add(this.substring(start, end))
        start = end
        end = iterator.next()
    }
    return if (graphemes.isNotEmpty()) graphemes else listOf(this)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
    isSynchronized: Boolean,
    logicClockState: State<Long>,
    drawClockState: State<Float>,
    palette: AlbumPalette,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Apple Music-style smooth scale animation for active line (1.04x) with spring physics
    val lineScale by animateFloatAsState(
        targetValue = if (isActive && isSynchronized) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyricScale_${line.id}"
    )

    // 2. Active line glow radius
    val activeGlowRadius by animateFloatAsState(
        targetValue = if (isActive && isSynchronized) 10f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyricGlow_${line.id}"
    )

    val shadow = if (activeGlowRadius > 0.5f) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.White.copy(alpha = 0.30f),
            blurRadius = activeGlowRadius,
            offset = Offset.Zero
        )
    } else {
        androidx.compose.ui.graphics.Shadow.None
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (line.isTimestamped && line.timestampMs >= 0L) {
                    onSeekTo(line.timestampMs)
                }
            }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        if (isSynchronized && line.words.isNotEmpty()) {
            val textMeasurer = rememberTextMeasurer()
            val textStyle = remember(line.isBackgroundVocal, isActive) {
                TextStyle(
                    fontSize = 24.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontStyle = if (line.isBackgroundVocal) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 36.sp,
                    color = Color.Unspecified
                )
            }
            
            val textLayoutResult = remember(line.text, textStyle) {
                textMeasurer.measure(line.text, style = textStyle)
            }

            // Pre-cache all word paths using line.id, line.words, AND textLayoutResult
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
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { textLayoutResult.size.height.toDp() })
                    .drawWithContent {
                        val currentTime = drawClockState.value
                        
                        // 1. Draw base text depending on line state
                        val baseColor = when {
                            !isActive && isPast -> Color.White.copy(alpha = 0.65f)
                            !isActive && isFuture -> Color.White.copy(alpha = 0.35f)
                            line.isBackgroundVocal -> TextMuted.copy(alpha = 0.25f)
                            else -> TextMuted
                        }
                        drawText(textLayoutResult, color = baseColor)
                        
                        // 2. Only draw active word-by-word highlighting when line is currently active
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
                            
                            // 3. Draw completed words in bright white
                            val highlightColor = if (line.isBackgroundVocal) Color.White.copy(alpha = 0.25f) else Color.White
                            if (!completedPath.isEmpty) {
                                clipPath(completedPath) { drawText(textLayoutResult, color = highlightColor) }
                            }
                            
                            // 4. Draw currently singing word with progressive clipRect animation
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
            // Unsynchronized / plain text fallback line
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
                label = "lyricColor_${line.id}"
            )
            
            Text(
                text = line.text,
                color = textColor,
                fontSize = 24.sp,
                fontWeight = if (isActive && isSynchronized) FontWeight.Bold else FontWeight.SemiBold,
                fontStyle = if (line.isBackgroundVocal) FontStyle.Italic else FontStyle.Normal,
                lineHeight = 36.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Custom Pill-Shaped Progress Bar for Now Playing Screen:
 * - Solid WHITE horizontal pill bar for played portion (thicker with fully rounded edges).
 * - Slightly thinner, semi-transparent GREY horizontal line with rounded edges for unplayed portion.
 * - Seamlessly connected at the center baseline.
 * - Completely removes the thin blue track and vertical white scrubber thumb.
 * - Smooth, continuous seeking and tapping interactions.
 */
@Composable
fun NowPlayingProgressBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    playedHeight: androidx.compose.ui.unit.Dp = 6.dp,
    unplayedHeight: androidx.compose.ui.unit.Dp = 3.5.dp,
    playedColor: Color = Color.White,
    unplayedColor: Color = Color(0x38FFFFFF),
    testTag: String = "song_progress_slider"
) {
    var componentWidth by remember { mutableFloatStateOf(1f) }
    var isFocused by remember { mutableStateOf(false) }

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "progressBarFocusScale"
    )

    Box(
        modifier = modifier
            .testTag(testTag)
            .height(32.dp) // Generous touch target & TV focus container
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .onSizeChanged { componentWidth = it.width.toFloat().coerceAtLeast(1f) }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            val nextVal = (value - 0.02f).coerceIn(0f, 1f)
                            onValueChange(nextVal)
                            onValueChangeFinished()
                            true
                        }
                        Key.DirectionRight -> {
                            val nextVal = (value + 0.02f).coerceIn(0f, 1f)
                            onValueChange(nextVal)
                            onValueChangeFinished()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onValueChangeFinished()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val fraction = (down.position.x / componentWidth).coerceIn(0f, 1f)
                    onValueChange(fraction)

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            change.consume()
                            onValueChangeFinished()
                            break
                        }
                        change.consume()
                        val dragFraction = (change.position.x / componentWidth).coerceIn(0f, 1f)
                        onValueChange(dragFraction)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 18.dp else playedHeight.coerceAtLeast(unplayedHeight))
        ) {
            val totalWidth = size.width
            val totalHeight = size.height
            val centerY = totalHeight / 2f

            val pHeightPx = playedHeight.toPx()
            val uHeightPx = unplayedHeight.toPx()

            // 1. Android TV Remote Focus Ring: subtle Apple-style glowing halo outline
            if (isFocused) {
                val focusHaloHeight = pHeightPx + 8.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(-4f, centerY - focusHaloHeight / 2f),
                    size = Size(totalWidth + 8f, focusHaloHeight),
                    cornerRadius = CornerRadius(focusHaloHeight / 2f, focusHaloHeight / 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Unplayed portion: slightly thinner, semi-transparent grey horizontal line with rounded edges
            drawRoundRect(
                color = unplayedColor,
                topLeft = Offset(0f, centerY - uHeightPx / 2f),
                size = Size(totalWidth, uHeightPx),
                cornerRadius = CornerRadius(uHeightPx / 2f, uHeightPx / 2f)
            )

            // 3. Played portion: solid WHITE horizontal bar, thicker, pill-shaped with fully rounded edges
            val playedWidth = totalWidth * value.coerceIn(0f, 1f)
            if (playedWidth > 0.5f) {
                val radiusX = (pHeightPx / 2f).coerceAtMost(playedWidth / 2f)
                val radiusY = pHeightPx / 2f
                drawRoundRect(
                    color = playedColor,
                    topLeft = Offset(0f, centerY - pHeightPx / 2f),
                    size = Size(playedWidth, pHeightPx),
                    cornerRadius = CornerRadius(radiusX, radiusY)
                )
            }
        }
    }
}


