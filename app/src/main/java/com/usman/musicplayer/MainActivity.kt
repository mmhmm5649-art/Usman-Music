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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
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
import com.usman.musicplayer.ui.screens.AlbumDetailsScreen
import com.usman.musicplayer.ui.screens.ArtistDetailsScreen
import com.usman.musicplayer.ui.screens.EqualizerScreen
import com.usman.musicplayer.ui.screens.HomeCollectionDetailSheet
import com.usman.musicplayer.ui.screens.HomeScreen
import com.usman.musicplayer.ui.screens.LibraryScreen
import com.usman.musicplayer.ui.screens.NowPlayingScreen
import com.usman.musicplayer.ui.screens.SearchScreen

import com.usman.musicplayer.ui.screens.SettingsScreen
import com.usman.musicplayer.ui.screens.PluginManagerScreen
import com.usman.musicplayer.ui.screens.ExtensionsMusicScreen
import com.usman.musicplayer.ui.screens.PlaybackDiagnosticScreen
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import android.view.KeyEvent
import com.usman.musicplayer.util.DeviceUtils
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val musicViewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureHighRefreshRateDisplayMode()
        setContent {
            UsmanMusicPlayerTheme {
                UsmanMusicApp(viewModel = musicViewModel)
            }
        }
    }

    private fun configureHighRefreshRateDisplayMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display ?: windowManager.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val currentMode = currentDisplay.mode ?: return
            val bestMode = currentDisplay.supportedModes
                .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: return

            if (bestMode.modeId != currentMode.modeId && bestMode.refreshRate > currentMode.refreshRate) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = bestMode.modeId
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                musicViewModel.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                musicViewModel.skipToNext()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                musicViewModel.skipToPrevious()
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                musicViewModel.seekForward(10000L)
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                musicViewModel.seekBackward(10000L)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
fun UsmanMusicApp(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    val phoneHomeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val phoneSearchListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val tvHomeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val tvSearchGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isDiagnosticExpanded by remember { mutableStateOf(false) }
    var isEqualizerExpanded by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var isPluginManagerExpanded by remember { mutableStateOf(false) }

    // Instant App-Launch Motion Sequence (no artificial 600ms cold-start stall)
    var isAppLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAppLoaded = true
    }

    val appScale by animateFloatAsState(
        targetValue = if (isAppLoaded) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appScale"
    )

    val appAlpha by animateFloatAsState(
        targetValue = if (isAppLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
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

    // Preload current song & next queued song artwork so Now Playing and track skips display instantly
    LaunchedEffect(uiState.currentSong?.id, uiState.currentSong?.albumArtUri, uiState.currentSong?.filePath, uiState.activePlaybackQueue.size) {
        val song = uiState.currentSong
        if (song != null) {
            com.usman.musicplayer.data.AlbumArtworkCache.loadArtwork(
                context = context,
                songId = song.id,
                albumArtUri = song.albumArtUri,
                filePath = song.filePath,
                targetWidth = 512,
                targetHeight = 512
            )
            val currentIdx = uiState.activePlaybackQueue.indexOfFirst { it.id == song.id }
            val nextSong = if (currentIdx >= 0) uiState.activePlaybackQueue.getOrNull(currentIdx + 1) else null
            if (nextSong != null) {
                com.usman.musicplayer.data.AppImageLoader.prefetchArtwork(
                    context = context,
                    url = nextSong.albumArtUri,
                    widthPx = com.usman.musicplayer.data.AppImageLoader.SIZE_FULL_ARTWORK_PX
                )
            }
        }
    }

    val isAlbumDetailsOpen = uiState.selectedAlbumSummary != null || uiState.selectedAlbumDetails != null
    val isArtistDetailsOpen = uiState.selectedArtistSummary != null || uiState.selectedArtistDetails != null

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

    BackHandler(enabled = isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded) {
        viewModel.closeAlbumDetails()
    }

    BackHandler(enabled = isArtistDetailsOpen && uiState.activeHomeCollectionSheet != null && !isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded) {
        viewModel.closeHomeCollectionSheet()
    }

    BackHandler(enabled = isArtistDetailsOpen && uiState.activeHomeCollectionSheet == null && !isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded) {
        viewModel.closeArtistDetails()
    }

    BackHandler(enabled = !isArtistDetailsOpen && !isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded && currentDestination != AppDestination.HOME) {
        viewModel.clearFilters()
        currentDestination = AppDestination.HOME
    }

    val isTv = remember { com.usman.musicplayer.util.DeviceUtils.isTvDevice(context) }
    val hazeState = if (isTv) null else remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Main App Content Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = appScale
                    scaleY = appScale
                    alpha = appAlpha
                }
        ) {
            if (isTv) {
                // --- ANDROID TV DEDICATED PRESENTATION LAYER ---
                Row(modifier = Modifier.fillMaxSize()) {
                    com.usman.musicplayer.ui.tv.TvNavigationRail(
                        currentDestination = currentDestination,
                        onNavigate = { dest ->
                            viewModel.clearFilters()
                            currentDestination = dest
                        },
                        onOpenNowPlaying = { isNowPlayingExpanded = true },
                        hasCurrentSong = uiState.currentSong != null
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = currentDestination,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "TvScreenTransition"
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> com.usman.musicplayer.ui.tv.TvHomeScreen(
                                    uiState = uiState,
                                    onNavigateToSearch = { currentDestination = AppDestination.SEARCH },
                                    onSearchCategory = { query ->
                                        viewModel.setSearchSource(com.usman.musicplayer.viewmodel.SearchSource.ONLINE)
                                        viewModel.updateSearchQuery(query)
                                        currentDestination = AppDestination.SEARCH
                                    },
                                    onPlaySong = { song ->
                                        viewModel.playSong(song)
                                        isNowPlayingExpanded = true
                                    },
                                    onTabGenreSelected = { tab -> viewModel.setActiveHomeGenreTab(tab) },
                                    onOpenSettings = { currentDestination = AppDestination.NEW },
                                    onSelectHomeLanguage = { lang -> viewModel.selectHomeLanguage(lang) },
                                    onRequestSectionLoad = { secId -> viewModel.requestHomeSectionLoad(secId) },
                                    onLoadMoreSection = { secId -> viewModel.loadMoreHomeSection(secId) },
                                    onOpenQuickCard = { card -> viewModel.openHomeQuickCard(card) },
                                    onOpenCuratedPlaylist = { playlist -> viewModel.openCuratedHomePlaylist(playlist) },
                                    onCloseCollectionSheet = { viewModel.closeHomeCollectionSheet() },
                                    onOpenSeeAllSection = { secId -> viewModel.openHomeSeeAll(secId) },
                                    onCloseSeeAllSection = { viewModel.closeHomeSeeAll() },
                                    onPlayQueue = { song, queue ->
                                        viewModel.playSong(song, explicitQueue = queue)
                                        isNowPlayingExpanded = true
                                    },
                                    onShuffleQueue = { songs ->
                                        viewModel.playQueueShuffled(songs)
                                        isNowPlayingExpanded = true
                                    },
                                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                                    onOpenAlbum = { album -> viewModel.openAlbum(album) },
                                    onOpenArtist = { artist ->
                                        viewModel.openArtistDetails(artist)
                                    },
                                    onSavePlaylistToLibrary = { title, songs, cb ->
                                        viewModel.savePlaylistToLibrary(title, songs, cb)
                                    },
                                    listState = tvHomeListState
                                )

                                AppDestination.SEARCH -> com.usman.musicplayer.ui.tv.TvSearchScreen(
                                    uiState = uiState,
                                    onSearchQueryChange = { q -> viewModel.updateSearchQuery(q) },
                                    onTriggerOnlineSearch = { q -> viewModel.searchOnline(q, debounceMs = 0L) },
                                    onSearchSourceChange = { src -> viewModel.setSearchSource(src) },
                                    onPlaySong = { song ->
                                        viewModel.playSong(song)
                                        isNowPlayingExpanded = true
                                    },
                                    onSearchFilterCategoryChange = { cat -> viewModel.setSearchFilterCategory(cat) },
                                    onOpenOnlineAlbum = { album -> viewModel.openAlbum(album) },
                                    onOpenOnlineArtist = { artist -> viewModel.openOnlineArtist(artist) },
                                    onOpenLocalAlbum = { album -> viewModel.openLocalAlbum(album) },
                                    gridState = tvSearchGridState
                                )

                                AppDestination.LIBRARY -> com.usman.musicplayer.ui.tv.TvLibraryScreen(
                                    uiState = uiState,
                                    onTabSelected = { tab -> viewModel.setLibraryTab(tab) },
                                    onPlaySong = { song ->
                                        viewModel.playSong(song)
                                        isNowPlayingExpanded = true
                                    },
                                    onOpenSavedAlbum = { details -> viewModel.openSavedAlbum(details) },
                                    onOpenLocalAlbum = { album -> viewModel.openLocalAlbum(album) }
                                )

                                AppDestination.NEW -> com.usman.musicplayer.ui.tv.TvSettingsScreen(
                                    onOpenEqualizer = { isEqualizerExpanded = true },
                                    onOpenPlugins = { isPluginManagerExpanded = true },
                                    onRescanLibrary = { viewModel.scanMusicLibrary() },
                                    userProfile = uiState.userProfile
                                )
                            }
                        }
                    }
                }
            } else {
                // --- ANDROID PHONE PRESENTATION LAYER (UNCHANGED) ---
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
                        .let { if (hazeState != null) it.haze(hazeState) else it },
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
                            onOpenSettings = { isSettingsExpanded = true },
                            onSelectHomeLanguage = { lang -> viewModel.selectHomeLanguage(lang) },
                            onRequestSectionLoad = { secId -> viewModel.requestHomeSectionLoad(secId) },
                            onLoadMoreSection = { secId -> viewModel.loadMoreHomeSection(secId) },
                            onOpenQuickCard = { card -> viewModel.openHomeQuickCard(card) },
                            onOpenCuratedPlaylist = { playlist -> viewModel.openCuratedHomePlaylist(playlist) },
                            onCloseCollectionSheet = { viewModel.closeHomeCollectionSheet() },
                            onOpenSeeAllSection = { secId -> viewModel.openHomeSeeAll(secId) },
                            onCloseSeeAllSection = { viewModel.closeHomeSeeAll() },
                            onPlayQueue = { song, queue ->
                                viewModel.playSong(song, explicitQueue = queue)
                                isNowPlayingExpanded = true
                            },
                            onShuffleQueue = { songs ->
                                viewModel.playQueueShuffled(songs)
                                isNowPlayingExpanded = true
                            },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                            onDownloadSong = { song -> viewModel.downloadSong(song) },
                            onOpenAlbum = { album -> viewModel.openAlbum(album) },
                            onOpenArtist = { artist ->
                                viewModel.openArtistDetails(artist)
                            },
                            onSavePlaylistToLibrary = { title, songs, cb ->
                                viewModel.savePlaylistToLibrary(title, songs, cb)
                            },
                            listState = phoneHomeListState
                        )

                        AppDestination.NEW -> HomeScreen(
                            uiState = remember(uiState) { uiState.copy(activeHomeGenreTab = "New Releases") },
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
                            onOpenSettings = { isSettingsExpanded = true },
                            onSelectHomeLanguage = { lang -> viewModel.selectHomeLanguage(lang) },
                            onRequestSectionLoad = { secId -> viewModel.requestHomeSectionLoad(secId) },
                            onLoadMoreSection = { secId -> viewModel.loadMoreHomeSection(secId) },
                            onOpenQuickCard = { card -> viewModel.openHomeQuickCard(card) },
                            onOpenCuratedPlaylist = { playlist -> viewModel.openCuratedHomePlaylist(playlist) },
                            onCloseCollectionSheet = { viewModel.closeHomeCollectionSheet() },
                            onOpenSeeAllSection = { secId -> viewModel.openHomeSeeAll(secId) },
                            onCloseSeeAllSection = { viewModel.closeHomeSeeAll() },
                            onPlayQueue = { song, queue ->
                                viewModel.playSong(song, explicitQueue = queue)
                                isNowPlayingExpanded = true
                            },
                            onShuffleQueue = { songs ->
                                viewModel.playQueueShuffled(songs)
                                isNowPlayingExpanded = true
                            },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                            onDownloadSong = { song -> viewModel.downloadSong(song) },
                            onOpenAlbum = { album -> viewModel.openAlbum(album) },
                            onOpenArtist = { artist ->
                                viewModel.openArtistDetails(artist)
                            },
                            onSavePlaylistToLibrary = { title, songs, cb ->
                                viewModel.savePlaylistToLibrary(title, songs, cb)
                            }
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
                            onSearchFilterCategoryChange = { cat -> viewModel.setSearchFilterCategory(cat) },
                            onOpenOnlineAlbum = { album -> viewModel.openAlbum(album) },
                            onOpenOnlineArtist = { artist -> viewModel.openOnlineArtist(artist) },
                            onAlbumClick = { album ->
                                viewModel.openLocalAlbum(album)
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
                            onOpenDiagnostics = { isDiagnosticExpanded = true },
                            listState = phoneSearchListState
                        )

                        AppDestination.LIBRARY -> LibraryScreen(
                            uiState = uiState,
                            onTabSelected = { tab -> viewModel.setLibraryTab(tab) },
                            onPlaySong = { song ->
                                viewModel.playSong(song)
                                isNowPlayingExpanded = true
                            },
                            onAlbumSelected = { album ->
                                viewModel.openLocalAlbum(album)
                            },
                            onArtistSelected = { artist ->
                                viewModel.filterByArtist(artist.name)
                                viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                            },
                            onFolderSelected = { folder ->
                                viewModel.filterByFolder(folder.name)
                                viewModel.setLibraryTab(LibraryTab.ALL_SONGS)
                            },
                            onSavedAlbumSelected = { details -> viewModel.openSavedAlbum(details) },
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
                            onClearFilter = { viewModel.clearFilters() },
                            onRemoveDownload = { songId -> viewModel.removeDownload(songId) },
                            onPauseDownload = { songId -> viewModel.pauseDownload(songId) },
                            onResumeDownload = { songId -> viewModel.resumeDownload(songId) },
                            onCancelDownload = { songId -> viewModel.cancelDownload(songId) },
                            onRetryDownload = { songId -> viewModel.retryDownload(songId) }
                        )
                    }
                }

                // Persistent Floating Liquid Glass Mini Player + Bottom Navigation Bar (Phone Only)
                AnimatedVisibility(
                    visible = !isArtistDetailsOpen && !isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded && !isSettingsExpanded && !isPluginManagerExpanded,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.currentSong != null) {
                            CompactNowPlayingBar(
                                currentSong = uiState.currentSong,
                                isPlaying = uiState.isPlaying,
                                playbackProgressFlow = viewModel.playbackProgress,
                                durationMs = uiState.currentSong?.durationMs ?: 0L,
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
        }

        // Artist Details Overlay (Phone & TV) — layered beneath Album Details so tapping an album stacks cleanly
        AnimatedVisibility(
            visible = isArtistDetailsOpen && !isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded,
            enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(tween(180))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ArtistDetailsScreen(
                    artistSummary = uiState.selectedArtistSummary,
                    artistDetails = uiState.selectedArtistDetails,
                    isLoading = uiState.isArtistDetailsLoading,
                    error = uiState.artistDetailsError,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    isTv = isTv,
                    onBack = { viewModel.closeArtistDetails() },
                    onRetry = { summary -> viewModel.openArtistDetails(summary) },
                    onPlayAll = { tracks ->
                        viewModel.playAlbum(tracks, startIndex = 0, shuffle = false)
                        isNowPlayingExpanded = true
                    },
                    onShuffleAll = { tracks ->
                        viewModel.playQueueShuffled(tracks)
                        isNowPlayingExpanded = true
                    },
                    onPlaySong = { song, queue ->
                        viewModel.playSong(song, explicitQueue = queue)
                        isNowPlayingExpanded = true
                    },
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                    onDownloadSong = { song -> viewModel.downloadSong(song) },
                    onOpenAlbum = { album -> viewModel.openAlbum(album) },
                    onOpenPlaylist = { playlist -> viewModel.openCuratedHomePlaylist(playlist) },
                    onSaveMixToLibrary = { title, songs, cb ->
                        viewModel.savePlaylistToLibrary(title, songs, cb)
                    }
                )

                val artistPlaylistSheet = uiState.activeHomeCollectionSheet
                if (artistPlaylistSheet != null) {
                    if (isTv) {
                        com.usman.musicplayer.ui.tv.TvHomeCollectionOverlay(
                            playlist = artistPlaylistSheet,
                            isLoading = uiState.isHomeCollectionLoading,
                            currentSong = uiState.currentSong,
                            onBack = { viewModel.closeHomeCollectionSheet() },
                            onPlaySong = { song, queue ->
                                viewModel.playSong(song, explicitQueue = queue)
                                isNowPlayingExpanded = true
                            },
                            onShuffleAll = { songs ->
                                viewModel.playQueueShuffled(songs)
                                isNowPlayingExpanded = true
                            },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                            onSaveToLibrary = { title, songs, cb ->
                                viewModel.savePlaylistToLibrary(title, songs, cb)
                            }
                        )
                    } else {
                        HomeCollectionDetailSheet(
                            playlist = artistPlaylistSheet,
                            isLoading = uiState.isHomeCollectionLoading,
                            currentSong = uiState.currentSong,
                            isPlaying = uiState.isPlaying,
                            onDismiss = { viewModel.closeHomeCollectionSheet() },
                            onPlaySong = { song, queue ->
                                viewModel.playSong(song, explicitQueue = queue)
                                isNowPlayingExpanded = true
                            },
                            onShuffleAll = { songs ->
                                viewModel.playQueueShuffled(songs)
                                isNowPlayingExpanded = true
                            },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                            onDownloadSong = { song -> viewModel.downloadSong(song) },
                            onSaveToLibrary = { title, songs, cb ->
                                viewModel.savePlaylistToLibrary(title, songs, cb)
                            }
                        )
                    }
                }
            }
        }

        // Album Details Overlay (Phone & TV)
        AnimatedVisibility(
            visible = isAlbumDetailsOpen && !isNowPlayingExpanded && !isEqualizerExpanded,
            enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(tween(180))
        ) {
            val currentAlbumId = uiState.selectedAlbumDetails?.id ?: uiState.selectedAlbumSummary?.id
            val currentProvider = uiState.selectedAlbumDetails?.sourceProvider ?: uiState.selectedAlbumSummary?.sourceProvider
            val isSaved = currentAlbumId != null && uiState.savedAlbums.any {
                it.id == currentAlbumId && it.sourceProvider.equals(currentProvider, ignoreCase = true)
            }
            AlbumDetailsScreen(
                albumSummary = uiState.selectedAlbumSummary,
                albumDetails = uiState.selectedAlbumDetails,
                isLoading = uiState.isAlbumDetailsLoading,
                error = uiState.albumDetailsError,
                playlists = uiState.playlists,
                isSavedToLibrary = isSaved,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                isTv = isTv,
                onBack = { viewModel.closeAlbumDetails() },
                onRetry = { summary -> viewModel.openAlbum(summary) },
                onPlayAll = { tracks ->
                    viewModel.playAlbum(tracks, startIndex = 0, shuffle = false)
                    isNowPlayingExpanded = true
                },
                onShuffleAll = { tracks ->
                    viewModel.playAlbum(tracks, startIndex = 0, shuffle = true)
                    isNowPlayingExpanded = true
                },
                onPlayTrack = { tracks, index ->
                    viewModel.playAlbum(tracks, startIndex = index, shuffle = false)
                    isNowPlayingExpanded = true
                },
                onToggleSaveLibrary = { details, callback ->
                    viewModel.toggleSaveAlbumToLibrary(details, callback)
                },
                onAddAlbumToPlaylist = { playlistId, tracks, callback ->
                    viewModel.addAlbumToPlaylist(playlistId, tracks, callback)
                },
                onCreatePlaylistFromAlbum = { name, tracks, callback ->
                    viewModel.createPlaylistFromAlbum(name, tracks, callback)
                },
                onDownloadAlbum = { tracks, callback ->
                    viewModel.downloadAlbum(tracks, callback)
                },
                onDownloadSong = { song -> viewModel.downloadSong(song) }
            )
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
                playbackProgressFlow = viewModel.playbackProgress,
                isExpanded = isNowPlayingExpanded,
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
                onFetchLyrics = { song -> viewModel.fetchLyrics(song) },
                onDownloadSong = { song -> viewModel.downloadSong(song) }
            )
        }

        // Equalizer / Studio Feel Audio Engine Slide-In Transition
        AnimatedVisibility(
            visible = isEqualizerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            EqualizerScreen(onBackClick = { isEqualizerExpanded = false })
        }

        // Diagnostic Screen Slide-In Transition
        AnimatedVisibility(
            visible = isDiagnosticExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            PlaybackDiagnosticScreen(onDismiss = { isDiagnosticExpanded = false })
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

