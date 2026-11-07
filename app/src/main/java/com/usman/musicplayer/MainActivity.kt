package com.usman.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AppDestination
import com.usman.musicplayer.ui.components.CompactNowPlayingBar
import com.usman.musicplayer.ui.components.UsmanGlassBottomNav
import com.usman.musicplayer.ui.screens.EqualizerScreen
import com.usman.musicplayer.ui.screens.HomeScreen
import com.usman.musicplayer.ui.screens.LibraryScreen
import com.usman.musicplayer.ui.screens.NowPlayingScreen
import com.usman.musicplayer.ui.screens.SearchScreen

import com.usman.musicplayer.ui.screens.SettingsScreen
import com.usman.musicplayer.ui.screens.PluginManagerScreen
import com.usman.musicplayer.ui.screens.ExtensionsMusicScreen
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val musicViewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UsmanMusicPlayerTheme {
                UsmanMusicApp(viewModel = musicViewModel)
            }
        }
    }
}

@Composable
fun UsmanMusicApp(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isDiagnosticExpanded by remember { mutableStateOf(false) }
    var isEqualizerExpanded by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var isPluginManagerExpanded by remember { mutableStateOf(false) }

    // Cinematic App-Launch Motion Sequence
    var isAppLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        isAppLoaded = true
    }

    val appScale by animateFloatAsState(
        targetValue = if (isAppLoaded) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appScale"
    )

    val appAlpha by animateFloatAsState(
        targetValue = if (isAppLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "appAlpha"
    )

    // Request audio permissions based on Android version
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    // Storage Access Framework (SAF) folder picker
    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            viewModel.addSafFolder(treeUri)
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            perms[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        viewModel.onPermissionResult(storageGranted)
    }

    fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        multiplePermissionsLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(Unit) {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val hasStorage = ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        viewModel.onPermissionResult(hasStorage)
        
        if (!hasStorage || !hasAudio || !hasNotification) {
            val permissions = mutableListOf(storagePermission, Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            multiplePermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    // Predictive back gesture & modal back navigation
    BackHandler(enabled = isPluginManagerExpanded) {
        isPluginManagerExpanded = false
    }

    BackHandler(enabled = isSettingsExpanded && !isPluginManagerExpanded) {
        isSettingsExpanded = false
    }

    BackHandler(enabled = isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded) {
        isEqualizerExpanded = false
    }

    BackHandler(enabled = isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded) {
        isNowPlayingExpanded = false
    }

    BackHandler(enabled = !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded && currentDestination != AppDestination.HOME) {
        viewModel.clearFilters()
        currentDestination = AppDestination.HOME
    }

    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Main App Content with Fluid 120Hz Animation System
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = appScale
                    scaleY = appScale
                    alpha = appAlpha
                }
        ) {
            // Screen-to-Screen Fluid Transition Container with Haze Backdrop Source
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 1.02f, animationSpec = tween(220))
                        )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState),
                label = "ScreenTransition"
            ) { destination ->
                when (destination) {
                    AppDestination.HOME -> HomeScreen(
                        uiState = uiState,
                        onNavigateToSearch = { currentDestination = AppDestination.SEARCH },
                        onSearchCategory = { query ->
                            viewModel.setSearchSource(com.usman.musicplayer.viewmodel.SearchSource.ONLINE)
                            viewModel.updateSearchQuery(query)
                            currentDestination = AppDestination.SEARCH
                        },
                        onNavigateToLibrary = { tab ->
                            viewModel.clearFilters()
                            viewModel.setLibraryTab(tab)
                            currentDestination = AppDestination.LIBRARY
                        },
                        onNavigateToNowPlaying = { isNowPlayingExpanded = true },
                        onPlaySong = { song ->
                            viewModel.playSong(song)
                            isNowPlayingExpanded = true
                        },
                        onOpenService = { ctx, song, service ->
                            viewModel.openInOfficialService(ctx, song, service)
                        },
                        onTabGenreSelected = { tab -> viewModel.setActiveHomeGenreTab(tab) },
                        onSelectCategory = { cat -> viewModel.selectOnlineCategory(cat) },
                        onClearCategory = { viewModel.clearSelectedCategory() },
                        onRescanClick = { viewModel.scanMusicLibrary() },
                        onRequestPermissionClick = { requestRequiredPermissions() },
                        onRetryTelugu = { viewModel.retryLoadTeluguMusic() },
                        onOpenSettings = { isSettingsExpanded = true }
                    )

                    AppDestination.NEW -> HomeScreen(
                        uiState = uiState.copy(activeHomeGenreTab = "New Releases"),
                        onNavigateToSearch = { currentDestination = AppDestination.SEARCH },
                        onSearchCategory = { query ->
                            viewModel.setSearchSource(com.usman.musicplayer.viewmodel.SearchSource.ONLINE)
                            viewModel.updateSearchQuery(query)
                            currentDestination = AppDestination.SEARCH
                        },
                        onNavigateToLibrary = { tab ->
                            viewModel.clearFilters()
                            viewModel.setLibraryTab(tab)
                            currentDestination = AppDestination.LIBRARY
                        },
                        onNavigateToNowPlaying = { isNowPlayingExpanded = true },
                        onPlaySong = { song ->
                            viewModel.playSong(song)
                            isNowPlayingExpanded = true
                        },
                        onOpenService = { ctx, song, service ->
                            viewModel.openInOfficialService(ctx, song, service)
                        },
                        onTabGenreSelected = { tab -> viewModel.setActiveHomeGenreTab(tab) },
                        onSelectCategory = { cat -> viewModel.selectOnlineCategory(cat) },
                        onClearCategory = { viewModel.clearSelectedCategory() },
                        onRescanClick = { viewModel.scanMusicLibrary() },
                        onRequestPermissionClick = { requestRequiredPermissions() },
                        onRetryTelugu = { viewModel.retryLoadTeluguMusic() },
                        onOpenSettings = { isSettingsExpanded = true }
                    )



                    AppDestination.SEARCH -> SearchScreen(
                        uiState = uiState,
                        onSearchQueryChange = { q -> viewModel.updateSearchQuery(q) },
                        onTriggerOnlineSearch = { q -> viewModel.searchOnline(q, debounceMs = 0L) },
                        onSearchSourceChange = { src -> viewModel.setSearchSource(src) },
                        onPlaySong = { song ->
                            Log.d("USMAN_PLAYBACK", "[Stage 2: SearchScreen Click] ID: ${song.id}, Title: '${song.title}'")
                            viewModel.playSong(song)
                            isNowPlayingExpanded = true
                        },
                        onOpenService = { ctx, song, service ->
                            viewModel.openInOfficialService(ctx, song, service)
                        },
                        onAlbumClick = { album ->
                            viewModel.filterByAlbum(album.title)
                            viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                            currentDestination = AppDestination.LIBRARY
                        },
                        onArtistClick = { artist ->
                            viewModel.filterByArtist(artist.name)
                            viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                            currentDestination = AppDestination.LIBRARY
                        },
                        onFolderClick = { folder ->
                            viewModel.filterByFolder(folder.name)
                            viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                            currentDestination = AppDestination.LIBRARY
                        },
                        onOpenDiagnostics = { isDiagnosticExpanded = true }
                    )

                    AppDestination.EXTENSIONS -> ExtensionsMusicScreen(
                        onPlaySong = { song ->
                            viewModel.playSong(song)
                            isNowPlayingExpanded = true
                        },
                        onOpenPluginManager = { isPluginManagerExpanded = true }
                    )

                    AppDestination.LIBRARY -> LibraryScreen(
                        uiState = uiState,
                        onTabSelected = { tab -> viewModel.setLibraryTab(tab) },
                        onPlaySong = { song ->
                            viewModel.playSong(song)
                            isNowPlayingExpanded = true
                        },
                        onAlbumSelected = { album ->
                            viewModel.filterByAlbum(album.title)
                            viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                        },
                        onArtistSelected = { artist ->
                            viewModel.filterByArtist(artist.name)
                            viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                        },
                        onFolderSelected = { folder ->
                            viewModel.filterByFolder(folder.name)
                            viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                        },
                        onSelectPlaylist = { playlist -> viewModel.selectPlaylist(playlist) },
                        onCreatePlaylistClick = {
                            viewModel.createPlaylist("New Playlist")
                        },
                        onDeletePlaylist = { playlistId -> viewModel.deletePlaylist(playlistId) },
                        onRenamePlaylist = { playlistId, newName -> viewModel.renamePlaylist(playlistId, newName) },
                        onRemoveSongFromPlaylist = { playlistId, songId -> viewModel.removeSongFromPlaylist(playlistId, songId) },
                        onNavigateToSearch = { currentDestination = AppDestination.SEARCH },
                        onOpenFolderPicker = { safFolderPicker.launch(null) },
                        onRescanLibrary = { viewModel.scanMusicLibrary() },
                        onClearFilter = { viewModel.clearFilters() }
                    )
                }
            }

            // Persistent Floating Liquid Glass Mini Player + Bottom Navigation Bar
            AnimatedVisibility(
                visible = !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ),
                exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(200)
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.currentSong != null) {
                        CompactNowPlayingBar(
                            currentSong = uiState.currentSong,
                            isPlaying = uiState.isPlaying,
                            currentPositionMs = playbackProgress.currentPositionMs,
                            durationMs = if (playbackProgress.durationMs > 0) playbackProgress.durationMs else (uiState.currentSong?.durationMs ?: 0L),
                            hazeState = hazeState,
                            onBarClick = { isNowPlayingExpanded = true },
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onSkipPrevious = { viewModel.skipToPrevious() },
                            onSkipNext = { viewModel.skipToNext() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    UsmanGlassBottomNav(
                        currentDestination = currentDestination,
                        hazeState = hazeState,
                        onDestinationSelected = {
                            viewModel.clearFilters()
                            val tab = when (it) {
                                AppDestination.NEW -> "New Releases"
                                else -> "Trending"
                            }
                            viewModel.setActiveHomeGenreTab(tab)
                            currentDestination = it
                        }
                    )
                }
            }
        }

        // Shared-Element Container Transform into Fullscreen Now Playing Screen
        AnimatedVisibility(
            visible = isNowPlayingExpanded && !isEqualizerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + scaleIn(
                initialScale = 0.90f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
            ) + scaleOut(
                targetScale = 0.92f,
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(250))
        ) {
            NowPlayingScreen(
                uiState = uiState,
                playbackProgress = playbackProgress,
                onBackClick = { isNowPlayingExpanded = false },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipToNext() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSeekTo = { pos -> viewModel.seekTo(pos) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                onVolumeChange = { vol -> viewModel.setVolume(vol) },
                onOpenService = { ctx, song, service -> viewModel.openInOfficialService(ctx, song, service) },
                onOpenEqualizer = { isEqualizerExpanded = true },
                onAddToPlaylist = { playlistId, song, allowDup, callback ->
                    viewModel.addSongToPlaylist(playlistId, song, allowDup, callback)
                },
                onCreatePlaylist = { name, song, callback ->
                    viewModel.createPlaylist(name, song, callback)
                },
                onPlayNext = { song, callback ->
                    viewModel.playNext(song, callback)
                },
                onAddToQueue = { song, callback ->
                    viewModel.addToQueue(song, callback)
                },
                onFetchLyrics = { song -> viewModel.fetchLyrics(song) }
            )
        }

        // Equalizer Screen Slide-In Transition
        AnimatedVisibility(
            visible = isEqualizerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            EqualizerScreen(onBackClick = { isEqualizerExpanded = false })
        }

        // Settings Screen Slide-In Transition
        AnimatedVisibility(
            visible = isSettingsExpanded && !isPluginManagerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
                SettingsScreen(
                    uiState = uiState,
                    onRescanClick = { viewModel.scanMusicLibrary() },
                    onOpenExtensions = { isPluginManagerExpanded = true },
                    onToggleProvider = { id, enabled -> viewModel.toggleProvider(id, enabled) }
                )
        }

        // Plugin Manager Screen Slide-In Transition
        AnimatedVisibility(
            visible = isPluginManagerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            PluginManagerScreen(onBackClick = { isPluginManagerExpanded = false })
        }

        // Cinematic App-Opening Logo Flare Overlay (Fades out seamlessly)
        if (!isAppLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AmoledBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "USMAN Logo",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "USMAN MUSIC",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LIQUID GLASS AUDIO",
                        color = SoftCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

