package com.usman.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.usman.musicplayer.data.LocalMusicScanner
import com.usman.musicplayer.data.SongDownloadManager
import com.usman.musicplayer.data.local.DownloadEntity
import com.usman.musicplayer.data.repository.AlbumLibraryRepository
import com.usman.musicplayer.data.repository.ExternalServiceType
import com.usman.musicplayer.data.repository.MusicGenreCategory
import com.usman.musicplayer.data.repository.OnlineMusicRepository
import com.usman.musicplayer.data.repository.PlaylistRepository
import com.usman.musicplayer.data.repository.UnifiedMusicRepository

import com.usman.musicplayer.data.repository.HomeDiscoveryCatalog
import com.usman.musicplayer.data.repository.HomeDiscoveryRepository
import com.usman.musicplayer.data.repository.HomeUserSignalsStore
import com.usman.musicplayer.data.repository.PersistedPlayStat
import com.usman.musicplayer.data.repository.PersistedAuthMode
import com.usman.musicplayer.data.repository.UserProfilePreferencesStore
import com.usman.musicplayer.data.repository.UserProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.AlbumDetails
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.ArtistDetailsData
import com.usman.musicplayer.model.AuthGateDestination
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeHeroFeature
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.HomeNotificationItem
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.RepeatMode
import com.usman.musicplayer.model.SearchFilterCategory
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.UserProfileState
import com.usman.musicplayer.playback.AudioEffectsManager
import com.usman.musicplayer.playback.AudioSpectrumManager
import com.usman.musicplayer.playback.UsmanPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import com.usman.musicplayer.data.repository.LocalSearchIndexer
import com.usman.musicplayer.data.repository.LocalMusicProvider
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

enum class SearchSource {
    ONLINE,
    DEVICE
}

@Stable
data class MusicPlayerUiState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<MusicFolder> = emptyList(),
    val safFolders: List<MusicFolder> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistSongs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 0.8f,
    val isFavorite: Boolean = false,
    val searchQuery: String = "",
    val activeLibraryTab: LibraryTab = LibraryTab.ALL_SONGS,
    val isScanning: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val selectedAlbumFilter: String? = null,
    val selectedArtistFilter: String? = null,
    val selectedFolderFilter: String? = null,

    // Device Local Music Search Results (Off-UI-Thread Indexed)
    val deviceMatchingSongs: List<Song> = emptyList(),
    val deviceMatchingAlbums: List<Album> = emptyList(),
    val deviceMatchingArtists: List<Artist> = emptyList(),
    val deviceMatchingFolders: List<MusicFolder> = emptyList(),
    val isLocalSearching: Boolean = false,

    // Online Global Music Discovery & Search State
    val searchSource: SearchSource = SearchSource.ONLINE,
    val searchFilterCategory: SearchFilterCategory = SearchFilterCategory.ALL,
    val searchHistory: List<String> = emptyList(),
    val onlineSearchResults: List<Song> = emptyList(),
    val onlineSearchAlbums: List<AlbumSearchResult> = emptyList(),
    val onlineSearchArtists: List<OnlineArtistResult> = emptyList(),
    val isOnlineSearching: Boolean = false,
    val onlineSearchError: String? = null,
    val selectedAlbumSummary: AlbumSearchResult? = null,
    val selectedAlbumDetails: AlbumDetails? = null,
    val isAlbumDetailsLoading: Boolean = false,
    val albumDetailsError: String? = null,
    val savedAlbums: List<AlbumDetails> = emptyList(),
    val activePlaybackQueue: List<Song> = emptyList(),
    val onlineTrendingSongs: List<Song> = emptyList(),
    val onlineTeluguSongs: List<Song> = emptyList(),
    val isTeluguLoading: Boolean = false,
    val onlineTeluguError: String? = null,
    val onlineBollywoodSongs: List<Song> = emptyList(),
    val onlineIndianSongs: List<Song> = emptyList(),
    val onlinePhonkSongs: List<Song> = emptyList(),
    val onlineEnglishSongs: List<Song> = emptyList(),
    val onlineGlobalSongs: List<Song> = emptyList(),
    val curatedPlaylists: List<CuratedPlaylist> = emptyList(),
    val selectedCuratedPlaylist: CuratedPlaylist? = null,
    val isOnlineLoading: Boolean = false,
    val activeHomeGenreTab: String = "Trending",
    val selectedCategory: MusicGenreCategory? = null,
    val categorySongs: List<Song> = emptyList(),
    val isCategoryLoading: Boolean = false,

    // Spotify-Style Home Discovery State (4 Languages Only: Telugu, Tamil, English, Hindi)
    val favoriteSongs: List<Song> = emptyList(),
    val selectedHomeLanguage: HomeLanguageFilter = HomeLanguageFilter.ALL,
    val homeSections: List<HomeDiscoverySection> = emptyList(),
    val homeQuickCards: List<HomeQuickAccessCard> = emptyList(),
    val homeHeroFeature: HomeHeroFeature? = null,
    val homeNotifications: List<HomeNotificationItem> = emptyList(),
    val isHomeInitialLoading: Boolean = true,
    val activeHomeCollectionSheet: CuratedPlaylist? = null,
    val isHomeCollectionLoading: Boolean = false,
    val selectedHomeSeeAllSectionId: String? = null,

    // Dedicated Artist Details State
    val selectedArtistSummary: OnlineArtistResult? = null,
    val selectedArtistDetails: ArtistDetailsData? = null,
    val isArtistDetailsLoading: Boolean = false,
    val artistDetailsError: String? = null,

    // Music Sources & Providers
    val musicProviders: List<com.usman.musicplayer.sdk.MusicProvider> = emptyList(),
    val enabledProviders: Map<String, Boolean> = emptyMap(),

    // Downloads
    val downloadedSongs: List<Song> = emptyList(),
    val activeDownloads: List<DownloadEntity> = emptyList(),
    val completedDownloads: List<DownloadEntity> = emptyList(),

    // User Account & Personalized Greeting Profile
    val userProfile: UserProfileState = UserProfileState()
)

@UnstableApi
class MusicPlayerViewModel @androidx.annotation.VisibleForTesting internal constructor(
    application: Application,
    private val musicRepository: UnifiedMusicRepository,
    private val homeDiscoveryRepository: HomeDiscoveryRepository,
    autoStart: Boolean = true
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        musicRepository = UnifiedMusicRepository(application),
        homeDiscoveryRepository = HomeDiscoveryRepository(),
        autoStart = true
    )

    private val scanner = LocalMusicScanner(application)
    private val playlistRepository = PlaylistRepository(application)
    private val albumLibraryRepository = AlbumLibraryRepository(application)
    private val downloadManager = SongDownloadManager(application)
    private val searchPrefs = application.getSharedPreferences(SEARCH_PREFS_NAME, Context.MODE_PRIVATE)
    private val signalsStore = HomeUserSignalsStore(application)
    private val homeSectionsById = java.util.concurrent.ConcurrentHashMap<String, HomeDiscoverySection>()
    private val inFlightSectionJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var persistedPlayStats: List<PersistedPlayStat> = emptyList()
    val localSearchIndexer = LocalSearchIndexer()
    private val searchQueryStateFlow = MutableStateFlow("")
    private val activeSearchRequestId = AtomicLong(0L)
    private var lastStartedSearchQuery: String = ""
    @Volatile
    private var homeHasStartedInitialLoad: Boolean = false
    private var progressTrackingJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var searchExecutionJob: Job? = null
    private var homeLoadJob: Job? = null
    private var albumFetchJob: Job? = null
    private var artistFetchJob: Job? = null
    private var latestSearchQuery: String = ""
    private var categoryFetchJob: Job? = null
    private var playlistSongsJob: Job? = null
    private var homeCollectionJob: Job? = null
    private var homeSnapshotJob: Job? = null
    private var playJob: Job? = null

    // Media3 MediaController connected to UsmanPlaybackService
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    // High-frequency playback progress flow to avoid root recomposition of entire app
    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    // User Profile & Auth Sync
    private val profilePrefsStore = UserProfilePreferencesStore(application)
    private var userProfileRepository: UserProfileRepository? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var profileObserverJob: Job? = null
    private var profileSyncJob: Job? = null
    private var activeObservedProfileUid: String? = null

    init {
        setupSearchPipeline()
        if (autoStart) {
            initializeUserProfileAndAuthObserver(application)
            initializeMedia3Controller()
            observePlaylists()
            observeSavedAlbums()
            observeDownloads()
            viewModelScope.launch {
                // Hydrate Home snapshot cache + lightweight signals off the Main Thread immediately
                val (cachedHomeSections, _) = withContext(Dispatchers.IO) {
                    AudioEffectsManager.init(application)
                    musicRepository.warmUp()
                    loadSearchHistory()
                    val snapshot = signalsStore.loadHomeSectionsSnapshot()
                    snapshot to true
                }
                updateProviderStates()

                if (cachedHomeSections.isNotEmpty()) {
                    hydrateHomeFromCachedSnapshot(cachedHomeSections)
                }

                // Load favorites/recent/play stats in parallel & start progressive network refresh
                launch(Dispatchers.IO) { loadUserPlaybackSignals() }
                loadInitialOnlineMusic(isBackgroundRefresh = cachedHomeSections.isNotEmpty())
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun startStartupSequenceForTest() {
        viewModelScope.launch {
            val cachedHomeSections = withContext(Dispatchers.IO) {
                loadSearchHistory()
                signalsStore.loadHomeSectionsSnapshot()
            }
            if (cachedHomeSections.isNotEmpty()) {
                hydrateHomeFromCachedSnapshot(cachedHomeSections)
            }
            loadUserPlaybackSignals()
            loadInitialOnlineMusic(isBackgroundRefresh = cachedHomeSections.isNotEmpty())
        }
    }

    private fun hydrateHomeFromCachedSnapshot(cachedSections: List<HomeDiscoverySection>) {
        homeDiscoveryRepository.seedFromPersistedSnapshot(cachedSections)
        cachedSections.forEach { sec ->
            val cleaned = sec.copy(
                songs = sec.songs.filter(::isAllowedUserFacingSong).map(::applyFavoriteFlag),
                albums = sec.albums.filter(::isAllowedUserFacingAlbum),
                artists = sec.artists.filter(::isAllowedUserFacingArtist),
                playlists = sec.playlists.filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) },
                isLoading = false,
                hasAttemptedLoad = true
            )
            if (cleaned.hasContent) {
                homeSectionsById[sec.id] = cleaned
                syncLegacyHomeSectionState(sec.id, cleaned.songs)
            }
        }
        _uiState.update {
            it.copy(
                isHomeInitialLoading = false,
                isOnlineLoading = false,
                isTeluguLoading = false
            )
        }
        rebuildHomeSectionsSnapshot()
        buildCuratedPlaylists()
    }

    private fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateUserProfileRepository(): UserProfileRepository? {
        userProfileRepository?.let { return it }
        return try {
            val app = getApplication<Application>()
            if (!isFirebaseAvailable(app)) return null
            UserProfileRepository(app).also { userProfileRepository = it }
        } catch (e: Exception) {
            Log.w("UserProfileVM", "Firestore profile repository unavailable: ${e.message}")
            null
        }
    }

    private fun initializeUserProfileAndAuthObserver(application: Application) {
        if (!isFirebaseAvailable(application)) {
            _uiState.update {
                it.copy(
                    userProfile = UserProfileState(
                        gateDestination = AuthGateDestination.HOME_GUEST,
                        isGuestMode = true
                    )
                )
            }
            return
        }

        val auth = try {
            Firebase.auth
        } catch (e: Exception) {
            Log.w("UserProfileVM", "FirebaseAuth unavailable: ${e.message}")
            _uiState.update {
                it.copy(
                    userProfile = UserProfileState(
                        gateDestination = AuthGateDestination.HOME_GUEST,
                        isGuestMode = true
                    )
                )
            }
            return
        }
        firebaseAuth = auth

        val listener = FirebaseAuth.AuthStateListener { fa ->
            handleFirebaseAuthChange(fa.currentUser)
        }
        authStateListener = listener
        auth.addAuthStateListener(listener)

        val initialUser = auth.currentUser
        if (initialUser != null) {
            handleFirebaseAuthChange(initialUser)
        } else {
            profilePrefsStore.setPersistedAuthMode(PersistedAuthMode.GUEST)
            _uiState.update {
                it.copy(
                    userProfile = UserProfileState(
                        gateDestination = AuthGateDestination.HOME_GUEST,
                        isSignedIn = false,
                        isGuestMode = true
                    )
                )
            }
        }
    }

    private fun handleFirebaseAuthChange(user: com.google.firebase.auth.FirebaseUser?) {
        if (user == null) {
            profileObserverJob?.cancel()
            profileObserverJob = null
            profileSyncJob?.cancel()
            profileSyncJob = null
            activeObservedProfileUid = null

            _uiState.update { state ->
                state.copy(
                    userProfile = UserProfileState(
                        gateDestination = AuthGateDestination.HOME_GUEST,
                        isSignedIn = false,
                        isGuestMode = true,
                        uid = null,
                        googleDisplayName = null,
                        email = null,
                        photoUrl = null,
                        isAuthInProgress = false,
                        authError = state.userProfile.authError,
                        sessionNotice = state.userProfile.sessionNotice
                    )
                )
            }
            return
        }

        val uid = user.uid
        val googleName = user.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val email = user.email?.trim()?.takeIf { it.isNotEmpty() }
        val photoUrl = user.photoUrl?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        profilePrefsStore.setPersistedAuthMode(PersistedAuthMode.GOOGLE)

        _uiState.update {
            it.copy(
                userProfile = UserProfileState(
                    gateDestination = AuthGateDestination.HOME_AUTHENTICATED,
                    isSignedIn = true,
                    isGuestMode = false,
                    uid = uid,
                    googleDisplayName = googleName,
                    email = email,
                    photoUrl = photoUrl,
                    isAuthInProgress = false,
                    authError = null,
                    sessionNotice = null
                )
            )
        }

        if (activeObservedProfileUid == uid && profileObserverJob?.isActive == true) {
            return
        }

        profileObserverJob?.cancel()
        profileSyncJob?.cancel()
        activeObservedProfileUid = uid

        val repo = getOrCreateUserProfileRepository() ?: return

        profileObserverJob = viewModelScope.launch {
            try {
                repo.observeUserProfile(uid).collect { doc ->
                    val currentUid = firebaseAuth?.currentUser?.uid
                    if (currentUid != uid) return@collect
                    _uiState.update { state ->
                        if (state.userProfile.uid != uid) state
                        else state.copy(
                            userProfile = state.userProfile.copy(
                                googleDisplayName = doc?.googleDisplayName?.takeIf { it.isNotBlank() }
                                    ?: state.userProfile.googleDisplayName,
                                email = doc?.email?.takeIf { it.isNotBlank() } ?: state.userProfile.email,
                                photoUrl = doc?.photoUrl?.takeIf { it.isNotBlank() } ?: state.userProfile.photoUrl
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("UserProfileVM", "Profile observer ended: ${e.message}")
            }
        }

        profileSyncJob = viewModelScope.launch {
            repo.syncSignedInUserProfile(
                googleDisplayName = googleName,
                email = email,
                photoUrl = photoUrl
            )
        }
    }

    private fun loadSearchHistory() {
        val raw = searchPrefs.getString(KEY_SEARCH_HISTORY, null).orEmpty()
        val items = raw.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(MAX_SEARCH_HISTORY)
        if (items.isNotEmpty()) {
            _uiState.update { it.copy(searchHistory = items) }
        }
    }

    fun recordSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val updated = (listOf(trimmed) + _uiState.value.searchHistory)
            .distinctBy { it.lowercase() }
            .take(MAX_SEARCH_HISTORY)
        _uiState.update { it.copy(searchHistory = updated) }
        searchPrefs.edit().putString(KEY_SEARCH_HISTORY, updated.joinToString("\n")).apply()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadManager.allDownloads.collect { entities ->
                val completed = entities.filter { it.status == "COMPLETED" }
                val active = entities.filter { it.status != "COMPLETED" }
                val downloadedSongsList = completed.map { downloadManager.convertToOfflineSong(it) }
                _uiState.update {
                    it.copy(
                        downloadedSongs = downloadedSongsList,
                        activeDownloads = active,
                        completedDownloads = completed
                    )
                }
                refreshDerivedHomeUi()
            }
        }
    }

    fun downloadSong(song: Song) {
        if (!isAllowedUserFacingSong(song)) return
        viewModelScope.launch {
            val url = if (song.filePath.startsWith("http://") || song.filePath.startsWith("https://")) {
                song.filePath
            } else {
                musicRepository.getStreamUrl(song).getOrNull() ?: song.externalUrl ?: return@launch
            }
            downloadManager.startDownload(song, url)
        }
    }

    fun pauseDownload(songId: Long) { downloadManager.pauseDownload(songId) }
    fun resumeDownload(songId: Long) { downloadManager.resumeDownload(songId) }
    fun cancelDownload(songId: Long) { downloadManager.cancelDownload(songId) }
    fun retryDownload(songId: Long) { downloadManager.retryDownload(songId) }
    fun removeDownload(songId: Long) { downloadManager.removeDownload(songId) }

    private fun updateProviderStates() {
        val providers = com.usman.musicplayer.sdk.ProviderRegistry.providers.filter {
            !it.metadata.id.equals("itunes_catalog_provider", ignoreCase = true) &&
                !it.metadata.name.equals("iTunes", ignoreCase = true)
        }
        val enabledMap = providers.associate { it.metadata.id to com.usman.musicplayer.sdk.ProviderRegistry.isProviderEnabled(it.metadata.id) }
        _uiState.update { it.copy(musicProviders = providers, enabledProviders = enabledMap) }
    }

    fun toggleProvider(providerId: String, enabled: Boolean) {
        com.usman.musicplayer.sdk.ProviderRegistry.setProviderEnabled(providerId, enabled)
        updateProviderStates()
        // If we are on Search screen, we might want to refresh results
        if (_uiState.value.searchQuery.isNotBlank() && _uiState.value.searchSource == SearchSource.ONLINE) {
            searchOnline(_uiState.value.searchQuery, debounceMs = 0L)
        }
    }

    suspend fun fetchLyrics(song: Song): String? {
        Log.d("USMAN_LYRICS", "[MusicPlayerViewModel] Fetching lyrics for '${song.title}' by '${song.artist}' (ID: ${song.filePath ?: song.id})")
        val providers = com.usman.musicplayer.sdk.ProviderRegistry.activeProviders

        // 1. Try LRCLIB primary lyrics provider first
        val lrclibProvider = providers.find { it.metadata.id == "lrclib_lyrics_provider" }
        if (lrclibProvider != null) {
            val result = lrclibProvider.getLyrics(song)
            if (result.isSuccess) {
                val l = result.getOrNull()
                if (!l.isNullOrBlank()) {
                    Log.d("USMAN_LYRICS", "[MusicPlayerViewModel] LRCLIB lyrics fetched successfully (length: ${l.length})")
                    return l
                }
            }
        }

        // 2. Try the provider that served this song
        val matchingProvider = providers.find {
            it.metadata.name.equals(song.sourceProvider, ignoreCase = true) ||
            it.metadata.id.equals(song.sourceProvider, ignoreCase = true)
        }

        if (matchingProvider != null && matchingProvider.metadata.id != "lrclib_lyrics_provider") {
            val result = matchingProvider.getLyrics(song)
            if (result.isSuccess) {
                val l = result.getOrNull()
                if (!l.isNullOrBlank()) {
                    Log.d("USMAN_LYRICS", "[MusicPlayerViewModel] Lyrics fetch via ${matchingProvider.metadata.name} success")
                    return l
                }
            }
        }

        // 3. Fallback: try all active providers
        for (provider in providers) {
            if (provider.metadata.id == "lrclib_lyrics_provider" || provider.metadata.id == matchingProvider?.metadata?.id) continue
            val result = provider.getLyrics(song)
            if (result.isSuccess) {
                val l = result.getOrNull()
                if (!l.isNullOrBlank()) {
                    Log.d("USMAN_LYRICS", "[MusicPlayerViewModel] Lyrics fetch via fallback ${provider.metadata.name} success")
                    return l
                }
            }
        }

        Log.w("USMAN_LYRICS", "[MusicPlayerViewModel] Lyrics fetch failed or null for '${song.title}' across all providers")
        return null
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistRepository.allPlaylists.collect { list ->
                _uiState.update { it.copy(playlists = list) }
            }
        }
    }

    private fun observeSavedAlbums() {
        viewModelScope.launch {
            albumLibraryRepository.savedAlbums.collect { list ->
                val filtered = list
                    .filter { !it.sourceProvider.equals("iTunes", ignoreCase = true) }
                    .mapNotNull { details ->
                        val cleanTracks = details.tracks.filter(::isAllowedUserFacingSong)
                        if (cleanTracks.isEmpty()) null else details.copy(tracks = cleanTracks)
                    }
                _uiState.update { it.copy(savedAlbums = filtered) }
            }
        }
    }


    private fun initializeMedia3Controller() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, UsmanPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    val controller = future.get() ?: return@addListener
                    mediaController = controller

                    // Synchronize initial state from controller
                    val initialIsPlaying = controller.isPlaying
                    val initialDuration = controller.duration.takeIf { it > 0 } ?: 0L
                    val initialPos = controller.currentPosition.coerceAtLeast(0L)
                    val initialBuffered = controller.bufferedPosition.coerceAtLeast(0L)
                    val initialShuffle = controller.shuffleModeEnabled
                    val initialRepeat = when (controller.repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    }

                    _uiState.update {
                        it.copy(
                            isPlaying = initialIsPlaying,
                            durationMs = if (initialDuration > 0) initialDuration else it.durationMs,
                            currentPositionMs = initialPos,
                            bufferedPositionMs = initialBuffered,
                            isShuffleEnabled = initialShuffle,
                            repeatMode = initialRepeat
                        )
                    }

                    if (initialIsPlaying) {
                        startProgressTracker()
                    }

                    // Setup listener to synchronize playback state with Media3 player events
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                            _playbackProgress.update {
                                it.copy(
                                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                                    isPlaying = isPlaying,
                                    isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                                    playbackSpeed = controller.playbackParameters.speed,
                                    updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                    updateTimeRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                )
                            }
                            if (isPlaying) {
                                startProgressTracker()
                                val sessionId = UsmanPlaybackService.currentAudioSessionId
                                if (sessionId > 0) {
                                    AudioEffectsManager.attachAudioSession(context, sessionId)
                                    AudioSpectrumManager.attachAudioSession(context, sessionId)
                                }
                            } else {
                                AudioSpectrumManager.clearSpectrum()
                            }
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            val newPosMs = newPosition.positionMs.coerceAtLeast(0L)
                            _uiState.update { it.copy(currentPositionMs = newPosMs) }
                            _playbackProgress.update {
                                it.copy(
                                    currentPositionMs = newPosMs,
                                    isPlaying = controller.isPlaying,
                                    isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                                    playbackSpeed = controller.playbackParameters.speed,
                                    updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                    updateTimeRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                )
                            }
                        }

                        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                            _playbackProgress.update {
                                it.copy(
                                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                                    playbackSpeed = playbackParameters.speed,
                                    updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                    updateTimeRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                )
                            }
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            val isPlaying = controller.isPlaying
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (mediaItem != null) {
                                val songId = mediaItem.mediaId.toLongOrNull() ?: return
                                val state = _uiState.value

                                // Search active playback queue and opened album tracks first
                                val song = state.activePlaybackQueue.find { it.id == songId }
                                    ?: state.selectedAlbumDetails?.tracks?.find { it.id == songId }
                                    ?: state.selectedPlaylistSongs.find { it.id == songId }
                                    ?: state.activeHomeCollectionSheet?.songs?.find { it.id == songId }
                                    ?: state.homeSections.asSequence().flatMap { it.songs.asSequence() }.firstOrNull { it.id == songId }
                                    ?: state.songs.find { it.id == songId }
                                    ?: state.onlineSearchResults.find { it.id == songId }
                                    ?: state.categorySongs.find { it.id == songId }
                                    ?: state.recentlyPlayed.find { it.id == songId }
                                    ?: state.favoriteSongs.find { it.id == songId }
                                    ?: state.onlineTrendingSongs.find { it.id == songId }
                                    ?: state.onlineTeluguSongs.find { it.id == songId }
                                    ?: state.onlineIndianSongs.find { it.id == songId }
                                    ?: state.onlineEnglishSongs.find { it.id == songId }
                                    ?: state.onlineGlobalSongs.find { it.id == songId }
                                    ?: state.currentSong

                                if (song != null) {
                                    onSongTransitioned(song)
                                }
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val duration = controller.duration.takeIf { it > 0 } ?: 0L
                            val buffered = controller.bufferedPosition.coerceAtLeast(0L)
                            val isPlaying = controller.isPlaying

                            val isBuffering = playbackState == Player.STATE_BUFFERING
                            _playbackProgress.update {
                                it.copy(
                                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                                    durationMs = if (duration > 0) duration else it.durationMs,
                                    bufferedPositionMs = buffered,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    playbackSpeed = controller.playbackParameters.speed,
                                    updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                    updateTimeRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                )
                            }
                            if (playbackState == Player.STATE_READY) {
                                _uiState.update {
                                    it.copy(
                                        durationMs = if (duration > 0) duration else it.durationMs,
                                        bufferedPositionMs = buffered,
                                        isPlaying = isPlaying
                                    )
                                }
                            } else if (playbackState == Player.STATE_ENDED) {
                                onTrackEnded()
                            }
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            _uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            val mode = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                                else -> RepeatMode.OFF
                            }
                            _uiState.update { it.copy(repeatMode = mode) }
                        }

                        private var lastFailedSongId: Long? = null
                        private var retryCountForSong = 0

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("MusicPlayerViewModel", "Playback error: ${error.message} (Code: ${error.errorCode})", error)
                            
                            val state = _uiState.value
                            val currentSong = state.currentSong
                            
                            val isHttpError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                    error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException

                            if (currentSong != null && currentSong.isOnline && isHttpError) {
                                if (lastFailedSongId != currentSong.id) {
                                    lastFailedSongId = currentSong.id
                                    retryCountForSong = 1
                                } else {
                                    retryCountForSong++
                                }

                                if (retryCountForSong <= 2) {
                                    Log.d("MusicPlayerViewModel", "Detected potential expired/403 stream for ${currentSong.title}. Attempting fresh re-resolution (attempt $retryCountForSong)...")
                                    
                                    viewModelScope.launch {
                                        // Pass copy with contentUriString = null so it actually contacts the provider for a fresh URL
                                        // rather than returning the existing failed cached URL!
                                        val songToResolve = currentSong.copy(contentUriString = null)
                                        val streamResult = musicRepository.getStreamUrl(songToResolve)
                                        if (streamResult.isSuccess) {
                                            var newUrl = streamResult.getOrNull()
                                            if (!newUrl.isNullOrBlank()) {
                                                // If same 320kbps URL is returned and already failed with 403, try 160kbps
                                                if (newUrl == currentSong.contentUriString && newUrl.contains("_320.mp4")) {
                                                    newUrl = newUrl.replace("_320.mp4", "_160.mp4")
                                                }
                                                if (newUrl != currentSong.contentUriString) {
                                                    Log.d("MusicPlayerViewModel", "Re-resolved new stream URL: $newUrl. Retrying playback...")
                                                    val updatedSong = currentSong.copy(contentUriString = newUrl)
                                                    _uiState.update { it.copy(currentSong = updatedSong) }
                                                    
                                                    controller.setMediaItem(createMediaItem(updatedSong))
                                                    controller.prepare()
                                                    controller.play()
                                                    return@launch
                                                }
                                            }
                                        }

                                        // Re-resolution failed or returned same failed URL -> Skip to next track smoothly!
                                        Log.w("MusicPlayerViewModel", "Stream re-resolution failed for '${currentSong.title}'. Skipping to next track in queue...")
                                        _uiState.update { 
                                            it.copy(
                                                onlineSearchError = "Stream unavailable for \"${currentSong.title}\". Playing next track..."
                                            ) 
                                        }
                                        if (controller.hasNextMediaItem()) {
                                            controller.seekToNextMediaItem()
                                            controller.prepare()
                                            controller.play()
                                        } else {
                                            skipToNext()
                                        }
                                    }
                                    return
                                } else {
                                    // Max retries reached for this song -> Skip to next track
                                    Log.w("MusicPlayerViewModel", "Max retries reached for '${currentSong.title}'. Skipping...")
                                    _uiState.update { 
                                        it.copy(
                                            onlineSearchError = "Stream unavailable for \"${currentSong.title}\""
                                        ) 
                                    }
                                    if (controller.hasNextMediaItem()) {
                                        controller.seekToNextMediaItem()
                                        controller.prepare()
                                        controller.play()
                                    } else {
                                        skipToNext()
                                    }
                                    return
                                }
                            } else {
                                _uiState.update { it.copy(isPlaying = false) }
                            }
                        }
                    })

                    // Attach audio session if already playing
                    val sessionId = UsmanPlaybackService.currentAudioSessionId
                    if (sessionId > 0) {
                        AudioEffectsManager.attachAudioSession(context, sessionId)
                        AudioSpectrumManager.attachAudioSession(context, sessionId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    // --- Online Music Loading & Spotify-Style Home Discovery ---

    private suspend fun loadUserPlaybackSignals() {
        val (favs, recent, stats) = withContext(Dispatchers.IO) {
            val loadedFavs = signalsStore.loadFavorites().filter(::isAllowedUserFacingSong)
            val loadedRecent = signalsStore.loadRecentlyPlayed()
                .filter(::isAllowedUserFacingSong)
                .map { s ->
                    val isFav = loadedFavs.any { HomeUserSignalsStore.songIdentityKey(it) == HomeUserSignalsStore.songIdentityKey(s) }
                    if (isFav) s.copy(isFavorite = true) else s
                }
            val loadedStats = signalsStore.loadPlayStats()
            Triple(loadedFavs, loadedRecent, loadedStats)
        }
        persistedPlayStats = stats
        _uiState.update {
            it.copy(
                favoriteSongs = favs,
                recentlyPlayed = recent
            )
        }
        rebuildHomeSectionsSnapshot()
    }

    fun loadInitialOnlineMusic(
        isBackgroundRefresh: Boolean = false,
        forceReload: Boolean = false
    ) {
        if (!forceReload && homeHasStartedInitialLoad && homeSectionsById.values.any { it.hasContent }) {
            return
        }
        homeHasStartedInitialLoad = true
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            val hasPopulatedSections = homeSectionsById.values.any { it.hasContent }
            if (!hasPopulatedSections && !isBackgroundRefresh) {
                _uiState.update {
                    it.copy(
                        isOnlineLoading = true,
                        isTeluguLoading = true,
                        isHomeInitialLoading = true,
                        onlineTeluguError = null
                    )
                }
                rebuildHomeSectionsSnapshot()
            }

            // Stage 0: Above-the-fold spotlight + first visible sections load immediately
            val stage0Ids = listOf(
                "discovery_trending_now",
                "telugu_top_hits",
                "discovery_made_for_you"
            )
            stage0Ids.map { secId ->
                async {
                    loadSectionInternal(
                        sectionId = secId,
                        forceRefresh = isBackgroundRefresh || forceReload
                    )
                }
            }.awaitAll()

            // Clear top-level initial loader as soon as first visible sections land
            _uiState.update {
                it.copy(
                    isOnlineLoading = false,
                    isTeluguLoading = false,
                    isHomeInitialLoading = false
                )
            }
            buildCuratedPlaylists()

            // Stage 1: Primary 4-language & album carousels concurrently (repository Semaphore caps concurrency cleanly)
            val stage1Ids = listOf(
                "discovery_popular_artists",
                "tamil_top_hits",
                "english_global_top_hits",
                "hindi_top_hits",
                "discovery_new_releases",
                "discovery_featured_albums"
            )
            stage1Ids.map { secId ->
                async {
                    loadSectionInternal(
                        sectionId = secId,
                        forceRefresh = isBackgroundRefresh || forceReload
                    )
                }
            }.awaitAll()
            buildCuratedPlaylists()

            // Stage 2: Lower below-the-fold discovery carousels asynchronously
            launch {
                val secondaryIds = listOf(
                    "discovery_recommended_for_you",
                    "discovery_heavy_rotation",
                    "discovery_movie_soundtracks"
                )
                secondaryIds.map { id ->
                    async {
                        loadSectionInternal(
                            sectionId = id,
                            forceRefresh = isBackgroundRefresh || forceReload
                        )
                    }
                }.awaitAll()
                withContext(Dispatchers.IO) {
                    signalsStore.saveHomeSectionsSnapshot(homeSectionsById.values.toList())
                }
            }

            withContext(Dispatchers.IO) {
                signalsStore.saveHomeSectionsSnapshot(homeSectionsById.values.toList())
            }
        }
    }

    fun selectHomeLanguage(language: HomeLanguageFilter) {
        if (_uiState.value.selectedHomeLanguage == language) return
        _uiState.update { it.copy(selectedHomeLanguage = language) }
        rebuildHomeSectionsSnapshot()

        val defs = HomeDiscoveryCatalog.definitionsForFilter(language)
        viewModelScope.launch {
            defs.take(6).map { def ->
                async {
                    val existing = homeSectionsById[def.id]
                    if (existing == null || !existing.hasAttemptedLoad) {
                        loadSectionInternal(def.id, forceRefresh = false)
                    }
                }
            }.awaitAll()
        }
    }

    fun requestHomeSectionLoad(sectionId: String) {
        val existing = homeSectionsById[sectionId]
        if (existing != null && (existing.hasAttemptedLoad || existing.isLoading)) return
        if (inFlightSectionJobs.containsKey(sectionId)) return

        val job = viewModelScope.launch {
            try {
                loadSectionInternal(sectionId, forceRefresh = false)
            } finally {
                inFlightSectionJobs.remove(sectionId)
            }
        }
        inFlightSectionJobs[sectionId] = job
    }

    fun loadMoreHomeSection(sectionId: String) {
        val def = definitionForDynamicOrCatalog(sectionId) ?: return
        val existing = homeSectionsById[sectionId] ?: return
        if (existing.isLoading || !existing.canLoadMore) return

        viewModelScope.launch {
            homeSectionsById[sectionId] = existing.copy(isLoading = true)
            rebuildHomeSectionsSnapshot()

            val nextPage = existing.currentPage + 1
            val nextSection = homeDiscoveryRepository.loadSection(
                definition = def,
                page = nextPage,
                recentlyPlayed = _uiState.value.recentlyPlayed,
                favoriteSongs = _uiState.value.favoriteSongs,
                playStats = persistedPlayStats,
                forceRefresh = true
            )

            val mergedSongs = (existing.songs + nextSection.songs)
                .filter(::isAllowedUserFacingSong)
                .distinctBy { HomeUserSignalsStore.songIdentityKey(it) }
                .map(::applyFavoriteFlag)
            val mergedAlbums = (existing.albums + nextSection.albums)
                .filter(::isAllowedUserFacingAlbum)
                .distinctBy { it.id.ifBlank { "${it.title.lowercase()}_${it.artist.lowercase()}" } }

            val merged = existing.copy(
                songs = mergedSongs,
                albums = mergedAlbums,
                isLoading = false,
                hasAttemptedLoad = true,
                currentPage = nextPage,
                canLoadMore = nextSection.songs.isNotEmpty() || nextSection.albums.isNotEmpty()
            )
            homeSectionsById[sectionId] = merged
            rebuildHomeSectionsSnapshot()
        }
    }

    fun refreshHomeDiscovery() {
        viewModelScope.launch {
            inFlightSectionJobs.values.forEach { it.cancel() }
            inFlightSectionJobs.clear()
            homeHasStartedInitialLoad = false
            // Keep existing populated content visible while rotating fresh dynamic recommendations
            loadInitialOnlineMusic(isBackgroundRefresh = true, forceReload = true)
            val activeDefs = HomeDiscoveryCatalog.definitionsForFilter(_uiState.value.selectedHomeLanguage)
            activeDefs.take(6).map { async { loadSectionInternal(it.id, forceRefresh = true) } }.awaitAll()
            withContext(Dispatchers.IO) {
                signalsStore.saveHomeSectionsSnapshot(homeSectionsById.values.toList())
            }
        }
    }

    private suspend fun loadSectionInternal(sectionId: String, forceRefresh: Boolean) {
        val def = definitionForDynamicOrCatalog(sectionId) ?: return
        val prev = homeSectionsById[sectionId]
        val hadContent = prev?.hasContent == true
        if (prev == null) {
            homeSectionsById[sectionId] = HomeDiscoverySection(
                id = def.id,
                title = def.title,
                subtitle = def.subtitle,
                language = def.language,
                contentType = def.contentType,
                isLoading = true,
                hasAttemptedLoad = false,
                isPersonalized = def.isPersonalized
            )
            scheduleHomeSnapshotRebuild()
        } else if (!hadContent && !prev.isLoading) {
            // Only mark loading shimmer when section does not already have cached/existing content
            homeSectionsById[sectionId] = prev.copy(isLoading = true)
            scheduleHomeSnapshotRebuild()
        }

        val loaded = homeDiscoveryRepository.loadSection(
            definition = def,
            page = 1,
            recentlyPlayed = _uiState.value.recentlyPlayed,
            favoriteSongs = _uiState.value.favoriteSongs,
            playStats = persistedPlayStats,
            forceRefresh = forceRefresh
        )

        val cleanedSongs = loaded.songs.filter(::isAllowedUserFacingSong).map(::applyFavoriteFlag)
        val cleanedAlbums = loaded.albums.filter(::isAllowedUserFacingAlbum)
        val cleanedArtists = loaded.artists.filter(::isAllowedUserFacingArtist)
        val cleanedPlaylists = loaded.playlists.filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) }
        val loadedHasContent = cleanedSongs.isNotEmpty() || cleanedAlbums.isNotEmpty() || cleanedArtists.isNotEmpty() || cleanedPlaylists.isNotEmpty()

        // Preserve existing cached content on offline/empty transient refresh instead of blanking the section
        val cleaned = if (!loadedHasContent && prev != null && prev.hasContent) {
            prev.copy(isLoading = false, hasAttemptedLoad = true)
        } else {
            loaded.copy(
                songs = cleanedSongs,
                albums = cleanedAlbums,
                artists = cleanedArtists,
                playlists = cleanedPlaylists,
                isLoading = false,
                hasAttemptedLoad = true
            )
        }
        homeSectionsById[sectionId] = cleaned
        syncLegacyHomeSectionState(sectionId, cleaned.songs)
        scheduleHomeSnapshotRebuild()
    }

    private fun syncLegacyHomeSectionState(sectionId: String, songs: List<Song>) {
        when (sectionId) {
            "discovery_trending_now" -> _uiState.update { it.copy(onlineTrendingSongs = songs) }
            "telugu_top_hits" -> _uiState.update {
                it.copy(
                    onlineTeluguSongs = songs,
                    isTeluguLoading = false,
                    onlineTeluguError = if (songs.isEmpty()) "No Telugu tracks found" else null
                )
            }
            "hindi_top_hits" -> _uiState.update {
                it.copy(onlineBollywoodSongs = songs, onlineIndianSongs = songs)
            }
            "english_global_top_hits" -> _uiState.update { it.copy(onlineEnglishSongs = songs) }
            "discovery_new_releases" -> _uiState.update { it.copy(onlineGlobalSongs = songs) }
        }
    }

    private fun scheduleHomeSnapshotRebuild() {
        homeSnapshotJob?.cancel()
        homeSnapshotJob = viewModelScope.launch {
            delay(40L)
            rebuildHomeSectionsSnapshot()
        }
    }

    private fun definitionForDynamicOrCatalog(sectionId: String) =
        HomeDiscoveryCatalog.DEFINITION_BY_ID[sectionId]
            ?: HomeDiscoveryCatalog.definitionsForFilter(HomeLanguageFilter.TELUGU).find { it.id == sectionId }
            ?: HomeDiscoveryCatalog.definitionsForFilter(HomeLanguageFilter.TAMIL).find { it.id == sectionId }
            ?: HomeDiscoveryCatalog.definitionsForFilter(HomeLanguageFilter.ENGLISH).find { it.id == sectionId }
            ?: HomeDiscoveryCatalog.definitionsForFilter(HomeLanguageFilter.HINDI).find { it.id == sectionId }

    private fun rebuildHomeSectionsSnapshot() {
        val state = _uiState.value
        val activeLang = state.selectedHomeLanguage
        val defs = HomeDiscoveryCatalog.definitionsForFilter(activeLang)
        val orderedSections = defs.mapNotNull { def ->
            val resolved = homeSectionsById[def.id]
            when {
                resolved == null -> HomeDiscoverySection(
                    id = def.id,
                    title = def.title,
                    subtitle = def.subtitle,
                    language = def.language,
                    contentType = def.contentType,
                    isLoading = false,
                    hasAttemptedLoad = false,
                    isPersonalized = def.isPersonalized
                )
                // Skip completely empty non-loading sections so UI never shows broken empty shelves
                resolved.hasAttemptedLoad && !resolved.isLoading && !resolved.hasContent -> null
                else -> resolved.copy(songs = resolved.songs.map(::applyFavoriteFlag))
            }
        }

        val quickCards = homeDiscoveryRepository.buildQuickAccessCards(
            sectionsById = homeSectionsById,
            recentlyPlayed = state.recentlyPlayed,
            favoriteSongs = state.favoriteSongs
        )
        val hero = homeDiscoveryRepository.buildHeroFeature(
            activeLanguage = activeLang,
            sectionsById = homeSectionsById,
            recentlyPlayed = state.recentlyPlayed
        )
        val notifications = homeDiscoveryRepository.buildNotificationsFeed(
            sectionsById = homeSectionsById,
            activeDownloadCount = state.activeDownloads.size,
            completedDownloadCount = state.completedDownloads.size
        )

        _uiState.update {
            it.copy(
                homeSections = orderedSections,
                homeQuickCards = quickCards,
                homeHeroFeature = hero,
                homeNotifications = notifications
            )
        }
    }

    private fun refreshDerivedHomeUi() {
        val state = _uiState.value
        val quickCards = homeDiscoveryRepository.buildQuickAccessCards(
            sectionsById = homeSectionsById,
            recentlyPlayed = state.recentlyPlayed,
            favoriteSongs = state.favoriteSongs
        )
        val hero = homeDiscoveryRepository.buildHeroFeature(
            activeLanguage = state.selectedHomeLanguage,
            sectionsById = homeSectionsById,
            recentlyPlayed = state.recentlyPlayed
        )
        val notifications = homeDiscoveryRepository.buildNotificationsFeed(
            sectionsById = homeSectionsById,
            activeDownloadCount = state.activeDownloads.size,
            completedDownloadCount = state.completedDownloads.size
        )
        _uiState.update {
            it.copy(
                homeQuickCards = quickCards,
                homeHeroFeature = hero,
                homeNotifications = notifications
            )
        }
    }

    fun openHomeQuickCard(card: HomeQuickAccessCard) {
        homeCollectionJob?.cancel()
        when (card.type) {
            HomeQuickAccessType.RECENTLY_PLAYED -> {
                val recent = _uiState.value.recentlyPlayed.filter(::isAllowedUserFacingSong).map(::applyFavoriteFlag)
                _uiState.update {
                    it.copy(
                        activeHomeCollectionSheet = CuratedPlaylist(
                            id = card.id,
                            title = "Recently Played",
                            description = "Tracks you've streamed recently on USMAN MUSIC",
                            coverArtUri = card.artworkUri ?: recent.firstOrNull()?.albumArtUri,
                            songs = recent
                        ),
                        isHomeCollectionLoading = false
                    )
                }
            }
            HomeQuickAccessType.FAVORITE_SONGS -> {
                val favs = _uiState.value.favoriteSongs.filter(::isAllowedUserFacingSong).map { it.copy(isFavorite = true) }
                _uiState.update {
                    it.copy(
                        activeHomeCollectionSheet = CuratedPlaylist(
                            id = card.id,
                            title = "Your Favourite Songs",
                            description = "Your saved liked tracks",
                            coverArtUri = card.artworkUri ?: favs.firstOrNull()?.albumArtUri,
                            songs = favs
                        ),
                        isHomeCollectionLoading = false
                    )
                }
            }
            HomeQuickAccessType.TELUGU_HITS -> openQuickLanguageHitsPlaylist("telugu_top_hits", card)
            HomeQuickAccessType.TAMIL_HITS -> openQuickLanguageHitsPlaylist("tamil_top_hits", card)
            HomeQuickAccessType.ENGLISH_HITS -> openQuickLanguageHitsPlaylist("english_global_top_hits", card)
            HomeQuickAccessType.HINDI_HITS -> openQuickLanguageHitsPlaylist("hindi_top_hits", card)
        }
    }

    private fun openQuickLanguageHitsPlaylist(sectionId: String, card: HomeQuickAccessCard) {
        val cached = homeSectionsById[sectionId]?.songs.orEmpty()
        _uiState.update {
            it.copy(
                activeHomeCollectionSheet = CuratedPlaylist(
                    id = card.id,
                    title = card.title,
                    description = "Verified full-length ${card.languageFilter.displayName} hits",
                    coverArtUri = card.artworkUri ?: cached.firstOrNull()?.albumArtUri,
                    songs = cached
                ),
                isHomeCollectionLoading = cached.isEmpty()
            )
        }
        if (cached.isEmpty()) {
            homeCollectionJob = viewModelScope.launch {
                loadSectionInternal(sectionId, forceRefresh = true)
                val fresh = homeSectionsById[sectionId]?.songs.orEmpty()
                _uiState.update { current ->
                    current.copy(
                        activeHomeCollectionSheet = current.activeHomeCollectionSheet?.copy(
                            coverArtUri = fresh.firstOrNull()?.albumArtUri ?: current.activeHomeCollectionSheet.coverArtUri,
                            songs = fresh
                        ),
                        isHomeCollectionLoading = false
                    )
                }
            }
        }
    }

    fun openCuratedHomePlaylist(playlist: CuratedPlaylist) {
        homeCollectionJob?.cancel()
        val cleanExisting = playlist.songs.filter(::isAllowedUserFacingSong).map(::applyFavoriteFlag)
        _uiState.update {
            it.copy(
                activeHomeCollectionSheet = playlist.copy(songs = cleanExisting),
                isHomeCollectionLoading = cleanExisting.isEmpty()
            )
        }
        if (cleanExisting.isEmpty()) {
            homeCollectionJob = viewModelScope.launch {
                val hydrated = homeDiscoveryRepository.hydratePlaylistIfNeeded(playlist)
                val cleanSongs = hydrated.songs.filter(::isAllowedUserFacingSong).map(::applyFavoriteFlag)
                _uiState.update { current ->
                    current.copy(
                        activeHomeCollectionSheet = hydrated.copy(songs = cleanSongs),
                        isHomeCollectionLoading = false
                    )
                }
            }
        }
    }

    fun closeHomeCollectionSheet() {
        homeCollectionJob?.cancel()
        _uiState.update { it.copy(activeHomeCollectionSheet = null, isHomeCollectionLoading = false) }
    }

    fun openHomeSeeAll(sectionId: String) {
        _uiState.update { it.copy(selectedHomeSeeAllSectionId = sectionId) }
        requestHomeSectionLoad(sectionId)
    }

    fun closeHomeSeeAll() {
        _uiState.update { it.copy(selectedHomeSeeAllSectionId = null) }
    }

    fun playQueueShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playAlbum(songs, startIndex = 0, shuffle = true)
    }

    fun savePlaylistToLibrary(
        title: String,
        songs: List<Song>,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val playable = filterPlayableQueueTracks(songs)
        if (playable.isEmpty()) {
            onResult(false, "No playable tracks available to save")
            return
        }
        createPlaylistFromAlbum(title, playable, onResult)
    }

    private fun isSongFavorited(song: Song): Boolean {
        val key = HomeUserSignalsStore.songIdentityKey(song)
        return _uiState.value.favoriteSongs.any { HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id }
    }

    private fun applyFavoriteFlag(song: Song): Song {
        val fav = isSongFavorited(song)
        return if (song.isFavorite == fav) song else song.copy(isFavorite = fav)
    }

    fun retryLoadTeluguMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTeluguLoading = true, onlineTeluguError = null) }
            loadSectionInternal("telugu_top_hits", forceRefresh = true)
            buildCuratedPlaylists()
        }
    }



    private fun buildCuratedPlaylists() {
        val state = _uiState.value
        val list = mutableListOf<CuratedPlaylist>()

        val trending = state.onlineTrendingSongs.filter(::isAllowedUserFacingSong)
        val telugu = state.onlineTeluguSongs.filter(::isAllowedUserFacingSong)
        val tamil = homeSectionsById["tamil_top_hits"]?.songs.orEmpty().filter(::isAllowedUserFacingSong)
        val bollywood = state.onlineBollywoodSongs.filter(::isAllowedUserFacingSong)
        val english = state.onlineEnglishSongs.filter(::isAllowedUserFacingSong)

        if (trending.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_daily_mix",
                    title = "Daily Mix 1",
                    description = "Top chartbusters curated for you",
                    coverArtUri = trending.firstOrNull()?.albumArtUri,
                    songs = trending
                )
            )
        }

        if (telugu.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_telugu_mix",
                    title = "Tollywood & Chill",
                    description = "Best Telugu romantic & dance tracks",
                    coverArtUri = telugu.firstOrNull()?.albumArtUri,
                    songs = telugu
                )
            )
        }

        if (tamil.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_tamil_mix",
                    title = "Kollywood Spotlight",
                    description = "Biggest Tamil hits & Anirudh melodies",
                    coverArtUri = tamil.firstOrNull()?.albumArtUri,
                    songs = tamil
                )
            )
        }

        if (bollywood.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_bollywood_mix",
                    title = "Bollywood Romance",
                    description = "Hindi romantic hits and acoustic vibes",
                    coverArtUri = bollywood.firstOrNull()?.albumArtUri,
                    songs = bollywood
                )
            )
        }

        if (english.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_discovery",
                    title = "Discovery Station",
                    description = "Global English pop & hits",
                    coverArtUri = english.firstOrNull()?.albumArtUri,
                    songs = english
                )
            )
        }

        _uiState.update { it.copy(curatedPlaylists = list) }
    }

    fun selectCuratedPlaylist(playlist: CuratedPlaylist?) {
        _uiState.update { it.copy(selectedCuratedPlaylist = playlist) }
    }

    fun selectOnlineCategory(category: MusicGenreCategory) {
        categoryFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedCategory = category,
                isCategoryLoading = true,
                categorySongs = emptyList()
            )
        }
        categoryFetchJob = viewModelScope.launch {
            try {
                OnlineMusicRepository.fetchCategorySongs(category, limit = 30)
                    .onSuccess { songs ->
                        _uiState.update {
                            it.copy(
                                categorySongs = songs.filter(::isAllowedUserFacingSong),
                                isCategoryLoading = false
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(isCategoryLoading = false)
                        }
                    }
            } catch (ce: CancellationException) {
                throw ce
            }
        }
    }

    fun clearSelectedCategory() {
        categoryFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedCategory = null,
                categorySongs = emptyList(),
                isCategoryLoading = false
            )
        }
    }

    fun setActiveHomeGenreTab(tab: String) {
        _uiState.update { it.copy(activeHomeGenreTab = tab) }
    }

    fun setSearchSource(source: SearchSource) {
        _uiState.update { it.copy(searchSource = source) }
        executeSearch(_uiState.value.searchQuery)
    }

    fun setSearchFilterCategory(category: SearchFilterCategory) {
        _uiState.update { it.copy(searchFilterCategory = category) }
    }

    fun searchOnline(query: String, debounceMs: Long = SEARCH_DEBOUNCE_MS) {
        val trimmed = query.trim()
        latestSearchQuery = trimmed
        if (trimmed.isBlank()) {
            activeSearchRequestId.incrementAndGet()
            lastStartedSearchQuery = ""
            searchDebounceJob?.cancel()
            searchExecutionJob?.cancel()
            _uiState.update {
                it.copy(
                    onlineSearchResults = emptyList(),
                    onlineSearchAlbums = emptyList(),
                    onlineSearchArtists = emptyList(),
                    isOnlineSearching = false,
                    onlineSearchError = null
                )
            }
            return
        }

        // Skip redundant re-trigger when exact same query already actively executing
        if (searchExecutionJob?.isActive == true && lastStartedSearchQuery.equals(trimmed, ignoreCase = false)) {
            return
        }

        // Fast cache path: render cached results immediately with zero spinner
        val cachedSongs = musicRepository.peekCachedSongs(trimmed, limit = 35)
        val cachedSoundtrackAlbums = musicRepository.peekCachedSoundtrackAlbums(trimmed, limit = 20)
        val cachedAlbums = cachedSoundtrackAlbums ?: musicRepository.peekCachedAlbums(trimmed, limit = 20)
        val cachedArtists = musicRepository.peekCachedArtists(trimmed, limit = 15)
        if (cachedSongs != null && cachedAlbums != null && cachedArtists != null) {
            activeSearchRequestId.incrementAndGet()
            lastStartedSearchQuery = trimmed
            searchDebounceJob?.cancel()
            searchExecutionJob?.cancel()
            _uiState.update {
                it.copy(
                    onlineSearchResults = cachedSongs.filter(::isAllowedUserFacingSong),
                    onlineSearchAlbums = cachedAlbums.filter(::isAllowedUserFacingAlbum),
                    onlineSearchArtists = cachedArtists.filter(::isAllowedUserFacingArtist),
                    isOnlineSearching = false,
                    onlineSearchError = null
                )
            }
            return
        }

        searchDebounceJob?.cancel()
        searchExecutionJob?.cancel()
        val requestId = activeSearchRequestId.incrementAndGet()
        searchDebounceJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            if (requestId != activeSearchRequestId.get() || trimmed != latestSearchQuery) return@launch

            lastStartedSearchQuery = trimmed
            val partialCachedSongs = musicRepository.peekCachedSongs(trimmed, limit = 35)?.filter(::isAllowedUserFacingSong)
            _uiState.update {
                it.copy(
                    onlineSearchResults = partialCachedSongs ?: emptyList(),
                    onlineSearchAlbums = cachedAlbums?.filter(::isAllowedUserFacingAlbum) ?: emptyList(),
                    onlineSearchArtists = cachedArtists?.filter(::isAllowedUserFacingArtist) ?: emptyList(),
                    isOnlineSearching = partialCachedSongs.isNullOrEmpty(),
                    onlineSearchError = null
                )
            }
            val startTime = System.currentTimeMillis()

            searchExecutionJob = launch {
                try {
                    val songsDeferred = async { musicRepository.searchAllWithStatus(trimmed, limit = 35) }
                    val albumsDeferred = async { musicRepository.searchAlbums(trimmed, limit = 20) }
                    val artistsDeferred = async {
                        musicRepository.searchArtists(trimmed, limit = 15, fallbackToSongSearch = false)
                    }

                    // Stage 1: Emit primary Songs progressively as soon as song endpoint returns
                    val songsOutcome = songsDeferred.await()
                    val songs = songsOutcome.songs.filter(::isAllowedUserFacingSong)
                    if (requestId != activeSearchRequestId.get() || trimmed != latestSearchQuery) return@launch
                    if (songs.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                onlineSearchResults = songs,
                                isOnlineSearching = false,
                                onlineSearchError = null
                            )
                        }
                    }

                    // Stage 2: Await Albums + Artists without blocking initial songs
                    var albums = albumsDeferred.await().filter(::isAllowedUserFacingAlbum)
                    val directArtists = artistsDeferred.await().filter(::isAllowedUserFacingArtist)
                    val artists = if (directArtists.isNotEmpty()) {
                        directArtists
                    } else {
                        musicRepository.deriveArtistsFromSongs(trimmed, songs, limit = 15)
                            .filter(::isAllowedUserFacingArtist)
                    }

                    if (requestId != activeSearchRequestId.get() || trimmed != latestSearchQuery) return@launch

                    val totalElapsed = System.currentTimeMillis() - startTime
                    Log.d("SearchTiming", "Primary search '$trimmed' completed in ${totalElapsed}ms (songs=${songs.size}, albums=${albums.size}, artists=${artists.size})")

                    if (debounceMs == 0L || songs.isNotEmpty() || albums.isNotEmpty()) {
                        recordSearchQuery(trimmed)
                    }

                    val noResults = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
                    val errorMsg = when {
                        !noResults -> null
                        songsOutcome.hadNetworkError -> "Unable to reach music catalog. Check your internet connection."
                        else -> "No songs or albums found for \"$trimmed\""
                    }
                    _uiState.update {
                        it.copy(
                            onlineSearchResults = songs,
                            onlineSearchAlbums = albums,
                            onlineSearchArtists = artists,
                            isOnlineSearching = false,
                            onlineSearchError = errorMsg
                        )
                    }

                    // Stage 3: Asynchronous movie/soundtrack album enrichment only when useful, never delaying UI
                    val wordCount = trimmed.split(Regex("\\s+")).count { it.isNotBlank() }
                    val topIsCollection = albums.firstOrNull()?.isSoundtrackCollection == true
                    if (songs.isNotEmpty() && wordCount in 1..4 && (!topIsCollection || albums.size < 2)) {
                        val enriched = musicRepository.searchAlbumsAndSoundtracks(trimmed, limit = 20, seedSongs = songs)
                            .filter(::isAllowedUserFacingAlbum)
                        if (requestId == activeSearchRequestId.get() && trimmed == latestSearchQuery && enriched.isNotEmpty()) {
                            _uiState.update { it.copy(onlineSearchAlbums = enriched) }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w("MusicPlayerVM", "Search failed for '$trimmed': ${e.message}")
                    if (requestId == activeSearchRequestId.get() && trimmed == latestSearchQuery) {
                        _uiState.update {
                            it.copy(
                                isOnlineSearching = false,
                                onlineSearchError = "Unable to complete search. Check your connection and try again."
                            )
                        }
                    }
                }
            }
        }
    }

    fun openOnlineArtist(artist: OnlineArtistResult) {
        openArtistDetails(artist)
    }

    fun openArtistDetails(artist: OnlineArtistResult) {
        if (!isAllowedUserFacingArtist(artist)) {
            _uiState.update {
                it.copy(
                    isArtistDetailsLoading = false,
                    artistDetailsError = "This artist is currently unavailable."
                )
            }
            return
        }
        artistFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedArtistSummary = artist,
                selectedArtistDetails = null,
                isArtistDetailsLoading = true,
                artistDetailsError = null
            )
        }

        artistFetchJob = viewModelScope.launch {
            try {
                val loaded = homeDiscoveryRepository.loadArtistDetails(
                    artist = artist,
                    preferredLanguage = _uiState.value.selectedHomeLanguage
                )
                val favKeys = _uiState.value.favoriteSongs.map { HomeUserSignalsStore.songIdentityKey(it) }.toSet()
                val syncedPopular = loaded.popularSongs.map { s ->
                    if (HomeUserSignalsStore.songIdentityKey(s) in favKeys) s.copy(isFavorite = true) else s
                }
                val syncedLatest = loaded.latestReleases.map { s ->
                    if (HomeUserSignalsStore.songIdentityKey(s) in favKeys) s.copy(isFavorite = true) else s
                }
                val finalDetails = loaded.copy(
                    popularSongs = syncedPopular,
                    latestReleases = syncedLatest
                )
                val isEmpty = finalDetails.popularSongs.isEmpty() &&
                    finalDetails.latestReleases.isEmpty() &&
                    finalDetails.albums.isEmpty() &&
                    finalDetails.playlists.isEmpty()
                _uiState.update {
                    it.copy(
                        selectedArtistDetails = finalDetails,
                        isArtistDetailsLoading = false,
                        artistDetailsError = if (isEmpty) "No tracks or albums available for ${artist.name} right now." else null
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w("MusicPlayerVM", "Failed loading artist details for '${artist.name}': ${e.message}")
                _uiState.update {
                    it.copy(
                        isArtistDetailsLoading = false,
                        artistDetailsError = "Unable to load ${artist.name}. Please try again."
                    )
                }
            }
        }
    }

    fun closeArtistDetails() {
        artistFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedArtistSummary = null,
                selectedArtistDetails = null,
                isArtistDetailsLoading = false,
                artistDetailsError = null
            )
        }
    }

    fun openAlbum(album: AlbumSearchResult) {
        if (!isAllowedUserFacingAlbum(album) && !album.isSoundtrackCollection && !album.id.startsWith("soundtrack_")) {
            _uiState.update {
                it.copy(
                    isAlbumDetailsLoading = false,
                    albumDetailsError = "This album is currently unavailable."
                )
            }
            return
        }
        albumFetchJob?.cancel()
        val cachedSaved = _uiState.value.savedAlbums.find {
            it.id == album.id && it.sourceProvider.equals(album.sourceProvider, ignoreCase = true)
        }
        _uiState.update {
            it.copy(
                selectedAlbumSummary = album,
                selectedAlbumDetails = cachedSaved,
                isAlbumDetailsLoading = true,
                albumDetailsError = null
            )
        }

        albumFetchJob = viewModelScope.launch {
            val result = musicRepository.getAlbumDetails(album)
            val rawDetails = result.getOrNull()
            val details = rawDetails?.let {
                val allowedTracks = it.tracks.filter(::isAllowedUserFacingSong)
                it.copy(
                    sourceProvider = if (it.sourceProvider.equals("iTunes", true)) "JioSaavn" else it.sourceProvider,
                    tracks = allowedTracks,
                    discoveredCount = allowedTracks.size,
                    contributingProviders = it.contributingProviders.filter { p -> !p.equals("iTunes", true) }.ifEmpty { listOf("JioSaavn") }
                )
            }
            if (details != null && details.tracks.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        selectedAlbumDetails = details,
                        isAlbumDetailsLoading = false,
                        albumDetailsError = null
                    )
                }
            } else {
                val fallbackSaved = albumLibraryRepository.getSavedAlbumDetailsSync(album)?.let { saved ->
                    val clean = saved.tracks.filter(::isAllowedUserFacingSong)
                    if (clean.isEmpty()) null else saved.copy(tracks = clean)
                }
                if (fallbackSaved != null) {
                    _uiState.update {
                        it.copy(
                            selectedAlbumDetails = fallbackSaved,
                            isAlbumDetailsLoading = false,
                            albumDetailsError = null
                        )
                    }
                } else {
                    val matchingTracks = _uiState.value.onlineSearchResults
                        .filter(::isAllowedUserFacingSong)
                        .filter { song ->
                            (!song.albumId.isNullOrBlank() && song.albumId == album.id)
                        }
                    if (matchingTracks.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                selectedAlbumDetails = AlbumDetails(
                                    id = album.id,
                                    title = album.title,
                                    movieName = album.movieName,
                                    artist = album.artist,
                                    year = album.year,
                                    songCount = maxOf(album.songCount ?: matchingTracks.size, matchingTracks.size),
                                    artworkUri = album.artworkUri,
                                    sourceProvider = "JioSaavn",
                                    externalUrl = album.externalUrl,
                                    language = album.language,
                                    isComplete = false,
                                    tracks = matchingTracks
                                ),
                                isAlbumDetailsLoading = false,
                                albumDetailsError = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isAlbumDetailsLoading = false,
                                albumDetailsError = result.exceptionOrNull()?.message ?: "Could not load full tracks for \"${album.title}\""
                            )
                        }
                    }
                }
            }
        }
    }

    fun openSavedAlbum(details: AlbumDetails) {
        albumFetchJob?.cancel()
        val summary = AlbumSearchResult(
            id = details.id,
            title = details.title,
            movieName = details.movieName,
            artist = details.artist,
            year = details.year,
            songCount = details.songCount,
            artworkUri = details.artworkUri,
            sourceProvider = details.sourceProvider,
            externalUrl = details.externalUrl,
            language = details.language,
            isCompleteAlbum = details.isComplete
        )
        _uiState.update {
            it.copy(
                selectedAlbumSummary = summary,
                selectedAlbumDetails = details,
                isAlbumDetailsLoading = details.tracks.isEmpty(),
                albumDetailsError = null
            )
        }
        if (details.tracks.isEmpty()) {
            openAlbum(summary)
        }
    }

    fun openLocalAlbum(album: Album) {
        val tracks = _uiState.value.songs.filter { it.album == album.title }
        val details = AlbumDetails(
            id = album.id.toString(),
            title = album.title,
            movieName = null,
            artist = album.artist,
            year = tracks.firstOrNull { !it.releaseYear.isNullOrBlank() }?.releaseYear,
            songCount = maxOf(album.songCount, tracks.size),
            artworkUri = album.artworkUri ?: tracks.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
            sourceProvider = "Device Library",
            isComplete = true,
            tracks = tracks
        )
        openSavedAlbum(details)
    }

    fun closeAlbumDetails() {
        albumFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAlbumSummary = null,
                selectedAlbumDetails = null,
                isAlbumDetailsLoading = false,
                albumDetailsError = null
            )
        }
    }

    fun playAlbum(tracks: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val playableTracks = filterPlayableQueueTracks(tracks)
        if (playableTracks.isEmpty()) {
            _uiState.update { it.copy(onlineSearchError = "No playable full tracks available in this album") }
            return
        }
        val orderedTracks = if (shuffle) playableTracks.shuffled() else playableTracks
        val safeStart = startIndex.coerceIn(0, orderedTracks.lastIndex)
        val controller = mediaController
        controller?.shuffleModeEnabled = false
        _uiState.update { it.copy(isShuffleEnabled = shuffle) }
        playSong(orderedTracks[safeStart], explicitQueue = orderedTracks)
    }

    fun toggleSaveAlbumToLibrary(details: AlbumDetails, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val alreadySaved = albumLibraryRepository.isAlbumSaved(details.sourceProvider, details.id)
                if (alreadySaved) {
                    albumLibraryRepository.removeSavedAlbum(details.sourceProvider, details.id)
                    onResult(false, "Removed \"${details.title}\" from Library")
                } else {
                    albumLibraryRepository.saveAlbum(details)
                    onResult(true, "Saved \"${details.title}\" to Library")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onResult(false, "Failed to update Library: ${e.message}")
            }
        }
    }

    fun addAlbumToPlaylist(playlistId: Long, tracks: List<Song>, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (tracks.isEmpty()) {
            onResult(false, "No tracks available to add")
            return
        }
        viewModelScope.launch {
            try {
                val (added, total) = playlistRepository.addSongsToPlaylist(playlistId, tracks)
                val msg = if (added > 0) {
                    "Added $added of $total tracks to playlist"
                } else {
                    "All tracks are already in this playlist"
                }
                onResult(added > 0, msg)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onResult(false, "Failed to add album: ${e.message}")
            }
        }
    }

    fun createPlaylistFromAlbum(name: String, tracks: List<Song>, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Playlist name cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                val (_, added) = playlistRepository.createPlaylistWithSongs(trimmed, tracks)
                onResult(true, "Created \"$trimmed\" with $added tracks")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onResult(false, "Failed to create playlist: ${e.message}")
            }
        }
    }

    fun downloadAlbum(tracks: List<Song>, onFeedback: (String) -> Unit = {}) {
        val downloadable = tracks.filter { it.playbackType == PlaybackType.FULL && (it.isStreamable || !it.isOnline) }
        if (downloadable.isEmpty()) {
            onFeedback("Download is not supported for this album")
            return
        }
        viewModelScope.launch {
            var startedCount = 0
            for (track in downloadable) {
                val url = if (!track.contentUriString.isNullOrBlank()) {
                    track.contentUriString
                } else if (track.filePath.startsWith("http://") || track.filePath.startsWith("https://")) {
                    track.filePath
                } else {
                    musicRepository.getStreamUrl(track).getOrNull()
                }
                if (!url.isNullOrBlank()) {
                    downloadManager.startDownload(track, url)
                    startedCount++
                }
            }
            if (startedCount > 0) {
                onFeedback("Started downloading $startedCount tracks")
            } else {
                onFeedback("Could not resolve download streams")
            }
        }
    }

    fun openInOfficialService(context: Context, song: Song, serviceType: ExternalServiceType) {
        OnlineMusicRepository.openInOfficialService(context, song, serviceType)
    }



    // --- Local Device Scanner ---

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = isGranted) }
        if (isGranted) {
            scanMusicLibrary()
        }
    }

    fun scanMusicLibrary() {
        if (!_uiState.value.hasStoragePermission && _uiState.value.safFolders.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }

            val mediaStoreSongs = if (_uiState.value.hasStoragePermission) {
                scanner.scanLocalAudio()
            } else {
                emptyList()
            }

            val safSongs = mutableListOf<Song>()
            for (safFolder in _uiState.value.safFolders) {
                safFolder.treeUriString?.let { uriStr ->
                    val uri = Uri.parse(uriStr)
                    safSongs.addAll(scanner.scanSafFolder(uri))
                }
            }

            val allSongs = (mediaStoreSongs + safSongs)
                .distinctBy { if (it.contentUriString != null) it.contentUriString else it.filePath }
                .sortedBy { it.title.lowercase() }

            // Move heavy extraction and indexing to background thread to prevent UI freeze
            val (albums, artists, folders) = withContext(kotlinx.coroutines.Dispatchers.Default) {
                val albums = LocalMusicScanner.extractAlbums(allSongs)
                val artists = LocalMusicScanner.extractArtists(allSongs)
                val folders = LocalMusicScanner.extractFolders(allSongs, _uiState.value.safFolders)
                localSearchIndexer.updateIndex(allSongs, albums, artists, folders)
                Triple(albums, artists, folders)
            }

            _uiState.update { current ->
                current.copy(
                    songs = allSongs,
                    albums = albums,
                    artists = artists,
                    folders = folders,
                    isScanning = false,
                    currentSong = current.currentSong ?: allSongs.firstOrNull()
                )
            }

            if (_uiState.value.searchQuery.isNotBlank()) {
                executeSearch(_uiState.value.searchQuery)
            }
        }
    }

    fun addSafFolder(treeUri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Ignore if not supported
            }

            val folderName = try {
                treeUri.lastPathSegment?.substringAfterLast(':') ?: "Custom Music Folder"
            } catch (e: Exception) {
                "Custom Music Folder"
            }

            val safFolder = MusicFolder(
                name = folderName,
                path = treeUri.toString(),
                songCount = 0,
                isSafFolder = true,
                treeUriString = treeUri.toString()
            )

            _uiState.update { current ->
                val updatedSaf = (current.safFolders + safFolder).distinctBy { it.treeUriString }
                current.copy(safFolders = updatedSaf)
            }

            scanMusicLibrary()
        }
    }

    fun playSong(song: Song, explicitQueue: List<Song>? = null) {
        // [Stage 3: playSong() input]
        Log.d("USMAN_PLAYBACK", "[Stage 3: playSong() input] ID: ${song.id}, Title: '${song.title}', Artist: '${song.artist}', Album: '${song.album}', Artwork: '${song.albumArtUri}', Duration: ${song.durationMs}")

        // Block preview-only or iTunes tracks from entering playback or recently played
        if (!isAllowedUserFacingSong(song)) {
            Log.w("USMAN_PLAYBACK", "Blocked preview or non-JioSaavn online track from playback: '${song.title}' (${song.sourceProvider}, ${song.playbackType})")
            _uiState.update { it.copy(onlineSearchError = "Preview tracks are disabled; only full-length streams can play.") }
            return
        }

        // Immediately update UI state to avoid placeholders
        _uiState.update {
            it.copy(
                currentSong = song,
                isPlaying = true,
                onlineSearchError = null
            )
        }

        playJob?.cancel()
        playJob = viewModelScope.launch {
            val controller = mediaController

            // If song requires external playback only
            if (song.playbackType == PlaybackType.EXTERNAL_SERVICE && song.contentUriString.isNullOrBlank()) {
                Log.w("USMAN_PLAYBACK", "Song requires external service playback handoff without direct stream URL")
                return@launch
            }

            // Identify the active playlist queue for next/previous navigation
            val albumTracks = _uiState.value.selectedAlbumDetails?.tracks.orEmpty()
            val homeSectionMatchingQueue = _uiState.value.homeSections
                .firstOrNull { sec -> sec.songs.any { it.id == song.id } }
                ?.songs
                .orEmpty()
            val collectionTracks = _uiState.value.activeHomeCollectionSheet?.songs.orEmpty()
            val rawQueue = when {
                !explicitQueue.isNullOrEmpty() -> explicitQueue
                albumTracks.any { it.id == song.id } -> albumTracks
                collectionTracks.any { it.id == song.id } -> collectionTracks
                _uiState.value.selectedPlaylistSongs.any { it.id == song.id } -> _uiState.value.selectedPlaylistSongs
                homeSectionMatchingQueue.isNotEmpty() -> homeSectionMatchingQueue
                _uiState.value.onlineSearchResults.any { it.id == song.id } -> _uiState.value.onlineSearchResults
                _uiState.value.categorySongs.any { it.id == song.id } -> _uiState.value.categorySongs
                _uiState.value.onlineTrendingSongs.any { it.id == song.id } -> _uiState.value.onlineTrendingSongs
                _uiState.value.onlineTeluguSongs.any { it.id == song.id } -> _uiState.value.onlineTeluguSongs
                _uiState.value.onlineIndianSongs.any { it.id == song.id } -> _uiState.value.onlineIndianSongs
                _uiState.value.onlineEnglishSongs.any { it.id == song.id } -> _uiState.value.onlineEnglishSongs
                _uiState.value.onlineGlobalSongs.any { it.id == song.id } -> _uiState.value.onlineGlobalSongs
                _uiState.value.songs.any { it.id == song.id } -> _uiState.value.songs
                else -> listOf(song)
            }
            val currentList = filterPlayableQueueTracks(rawQueue)

            suspend fun resolveSingleTrack(candidate: Song): Song {
                if (candidate.isOnline && candidate.contentUriString.isNullOrBlank() && candidate.playbackType == PlaybackType.FULL) {
                    val streamResult = musicRepository.getStreamUrl(candidate)
                    if (streamResult.isSuccess) {
                        val resolvedUrl = streamResult.getOrNull().orEmpty()
                        if (resolvedUrl.isNotBlank()) {
                            val sanitizedHost = runCatching { Uri.parse(resolvedUrl).host }.getOrNull() ?: "unknown"
                            Log.d("USMAN_PLAYBACK", "[Stage 4: After stream URL resolution] ID: ${candidate.id}, Title: '${candidate.title}', StreamUrl: $resolvedUrl (host: $sanitizedHost)")
                            return candidate.copy(contentUriString = resolvedUrl)
                        }
                    } else {
                        Log.e("USMAN_PLAYBACK", "Stream resolution failed for '${candidate.title}': ${streamResult.exceptionOrNull()?.message}")
                    }
                }
                return candidate
            }

            // Fast-path: resolve the clicked song first so playback starts immediately without waiting on 20+ queue siblings
            val unresolvedCount = currentList.count {
                it.isOnline && it.contentUriString.isNullOrBlank() && it.playbackType == PlaybackType.FULL
            }
            val resolvedTarget = withContext(Dispatchers.IO) { resolveSingleTrack(song) }
            val canStartTargetImmediately = isAllowedUserFacingSong(resolvedTarget) &&
                (!resolvedTarget.isOnline || !resolvedTarget.contentUriString.isNullOrBlank())

            if (unresolvedCount <= 1 || !canStartTargetImmediately) {
                val resolvedList = withContext(Dispatchers.IO) {
                    currentList.map { s ->
                        async { if (s.id == song.id) resolvedTarget else resolveSingleTrack(s) }
                    }.awaitAll()
                }.filter { s ->
                    if (!isAllowedUserFacingSong(s) || (s.isOnline && s.contentUriString.isNullOrBlank())) {
                        Log.w("USMAN_PLAYBACK", "Dropping unavailable or preview online song from queue: '${s.title}'")
                        false
                    } else {
                        true
                    }
                }

                val playableSong = resolvedList.find { it.id == song.id } ?: resolvedList.firstOrNull()
                if (playableSong == null || !isAllowedUserFacingSong(playableSong) || (playableSong.isOnline && playableSong.contentUriString.isNullOrBlank())) {
                    Log.e("USMAN_PLAYBACK", "Playback failure: Could not resolve playable stream for '${song.title}'")
                    _uiState.update { it.copy(onlineSearchError = "Unable to play \"${song.title}\": Stream unavailable") }
                    return@launch
                }

                val songWithFav = applyFavoriteFlag(playableSong)
                val updatedRecent = (_uiState.value.recentlyPlayed
                    .filter(::isAllowedUserFacingSong)
                    .filter { HomeUserSignalsStore.songIdentityKey(it) != HomeUserSignalsStore.songIdentityKey(songWithFav) }
                    .toMutableList()).apply { add(0, songWithFav) }.take(20)

                _uiState.update {
                    it.copy(
                        currentSong = songWithFav,
                        activePlaybackQueue = resolvedList,
                        isPlaying = true,
                        durationMs = songWithFav.durationMs,
                        currentPositionMs = 0L,
                        isFavorite = songWithFav.isFavorite,
                        recentlyPlayed = updatedRecent,
                        onlineSearchError = null
                    )
                }
                refreshDerivedHomeUi()
                viewModelScope.launch(Dispatchers.IO) {
                    signalsStore.saveRecentlyPlayed(updatedRecent)
                    persistedPlayStats = signalsStore.recordSongPlay(songWithFav)
                }

                if (controller != null) {
                    val queue = resolvedList.map { s -> createMediaItem(s) }
                    val startIndex = queue.indexOfFirst { it.mediaId == playableSong.id.toString() }
                    controller.setMediaItems(queue, startIndex.coerceAtLeast(0), 0L)
                    controller.prepare()
                    controller.play()
                    controller.volume = _uiState.value.volume
                }

                startProgressTracker()
            } else {
                // Start target song immediately in 1 roundtrip
                val songWithFav = applyFavoriteFlag(resolvedTarget)
                val updatedRecent = (_uiState.value.recentlyPlayed
                    .filter(::isAllowedUserFacingSong)
                    .filter { HomeUserSignalsStore.songIdentityKey(it) != HomeUserSignalsStore.songIdentityKey(songWithFav) }
                    .toMutableList()).apply { add(0, songWithFav) }.take(20)

                _uiState.update {
                    it.copy(
                        currentSong = songWithFav,
                        activePlaybackQueue = currentList,
                        isPlaying = true,
                        durationMs = songWithFav.durationMs,
                        currentPositionMs = 0L,
                        isFavorite = songWithFav.isFavorite,
                        recentlyPlayed = updatedRecent,
                        onlineSearchError = null
                    )
                }
                refreshDerivedHomeUi()
                viewModelScope.launch(Dispatchers.IO) {
                    signalsStore.saveRecentlyPlayed(updatedRecent)
                    persistedPlayStats = signalsStore.recordSongPlay(songWithFav)
                }

                if (controller != null) {
                    controller.setMediaItems(listOf(createMediaItem(songWithFav)), 0, 0L)
                    controller.prepare()
                    controller.play()
                    controller.volume = _uiState.value.volume
                }
                startProgressTracker()

                // Resolve sibling queue tracks concurrently in background and splice them seamlessly around the playing track
                val resolvedList = withContext(Dispatchers.IO) {
                    currentList.map { s ->
                        async { if (s.id == songWithFav.id) songWithFav else resolveSingleTrack(s) }
                    }.awaitAll()
                }.filter { s ->
                    isAllowedUserFacingSong(s) && (!s.isOnline || !s.contentUriString.isNullOrBlank())
                }

                if (!isActive || _uiState.value.currentSong?.id != songWithFav.id) return@launch
                _uiState.update { it.copy(activePlaybackQueue = resolvedList) }

                if (controller != null && controller.currentMediaItem?.mediaId == songWithFav.id.toString()) {
                    val idx = resolvedList.indexOfFirst { it.id == songWithFav.id }
                    if (idx >= 0) {
                        val beforeItems = resolvedList.subList(0, idx).map { createMediaItem(it) }
                        val afterItems = resolvedList.subList(idx + 1, resolvedList.size).map { createMediaItem(it) }
                        if (beforeItems.isNotEmpty()) {
                            controller.addMediaItems(0, beforeItems)
                        }
                        if (afterItems.isNotEmpty()) {
                            controller.addMediaItems(afterItems)
                        }
                    }
                }
            }
        }
    }

    private fun onSongTransitioned(song: Song) {
        if (!isAllowedUserFacingSong(song)) return
        val songWithFav = applyFavoriteFlag(song)
        val updatedRecent = (_uiState.value.recentlyPlayed
            .filter(::isAllowedUserFacingSong)
            .filter { HomeUserSignalsStore.songIdentityKey(it) != HomeUserSignalsStore.songIdentityKey(songWithFav) }
            .toMutableList()).apply { add(0, songWithFav) }.take(20)

        _uiState.update {
            it.copy(
                currentSong = songWithFav,
                durationMs = songWithFav.durationMs,
                currentPositionMs = 0L,
                isFavorite = songWithFav.isFavorite,
                recentlyPlayed = updatedRecent
            )
        }
        refreshDerivedHomeUi()
        viewModelScope.launch(Dispatchers.IO) {
            signalsStore.saveRecentlyPlayed(updatedRecent)
            persistedPlayStats = signalsStore.recordSongPlay(songWithFav)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            _uiState.update { it.copy(isPlaying = false) }
            _playbackProgress.update {
                it.copy(
                    isPlaying = false,
                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                    updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime()
                )
            }
            AudioSpectrumManager.clearSpectrum()
        } else {
            controller.play()
            _uiState.update { it.copy(isPlaying = true) }
            _playbackProgress.update {
                it.copy(
                    isPlaying = true,
                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                    updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime()
                )
            }
            startProgressTracker()
            val sId = UsmanPlaybackService.currentAudioSessionId
            if (sId > 0) {
                AudioSpectrumManager.attachAudioSession(getApplication(), sId)
                AudioEffectsManager.attachAudioSession(getApplication(), sId)
            }
        }
    }

    fun skipToNext() {
        val controller = mediaController
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            return
        }

        // Fallback to active queue or library list
        val current = _uiState.value
        val list = current.activePlaybackQueue.ifEmpty { current.songs }
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.currentSong?.id }
        val nextIndex = if (current.isShuffleEnabled) {
            (list.indices).filter { it != currentIndex }.randomOrNull() ?: 0
        } else {
            (currentIndex + 1) % list.size
        }
        playSong(list[nextIndex], explicitQueue = list)
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            return
        }

        val current = _uiState.value
        val list = current.activePlaybackQueue.ifEmpty { current.songs }
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.currentSong?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playSong(list[prevIndex], explicitQueue = list)
    }

    fun seekTo(positionMs: Long) {
        val validPos = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(validPos)
        _uiState.update { it.copy(currentPositionMs = validPos) }
        _playbackProgress.update {
            it.copy(
                currentPositionMs = validPos,
                updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                updateTimeRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            )
        }
    }

    fun seekForward(amountMs: Long = 10000L) {
        val current = _playbackProgress.value.currentPositionMs
        val duration = _playbackProgress.value.durationMs
        val maxPos = if (duration > 0) duration else Long.MAX_VALUE
        val target = (current + amountMs).coerceAtMost(maxPos)
        seekTo(target)
    }

    fun seekBackward(amountMs: Long = 10000L) {
        val current = _playbackProgress.value.currentPositionMs
        val target = (current - amountMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun toggleShuffle() {
        val controller = mediaController
        val newShuffle = !_uiState.value.isShuffleEnabled
        controller?.shuffleModeEnabled = newShuffle
        _uiState.update { it.copy(isShuffleEnabled = newShuffle) }
    }

    fun toggleRepeat() {
        val controller = mediaController
        val nextMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val media3Repeat = when (nextMode) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = media3Repeat
        _uiState.update { it.copy(repeatMode = nextMode) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        mediaController?.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
    }

    fun toggleFavorite(song: Song) {
        if (!isAllowedUserFacingSong(song)) return
        val key = HomeUserSignalsStore.songIdentityKey(song)
        val currentlyFav = isSongFavorited(song) || song.isFavorite
        val newFav = !currentlyFav

        val currentFavList = _uiState.value.favoriteSongs.toMutableList()
        if (newFav) {
            if (currentFavList.none { HomeUserSignalsStore.songIdentityKey(it) == key }) {
                currentFavList.add(0, song.copy(isFavorite = true))
            }
        } else {
            currentFavList.removeAll { HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id }
        }
        signalsStore.saveFavorites(currentFavList)

        val updatedSongs = _uiState.value.songs.map {
            if (HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id) it.copy(isFavorite = newFav) else it
        }
        val updatedRecent = _uiState.value.recentlyPlayed.map {
            if (HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id) it.copy(isFavorite = newFav) else it
        }
        val isCurrent = _uiState.value.currentSong?.let {
            HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id
        } == true

        val updatedSheet = _uiState.value.activeHomeCollectionSheet?.let { sheet ->
            if (sheet.id == "qa_favorite_songs") {
                sheet.copy(songs = currentFavList, coverArtUri = currentFavList.firstOrNull()?.albumArtUri)
            } else {
                sheet.copy(songs = sheet.songs.map {
                    if (HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id) it.copy(isFavorite = newFav) else it
                })
            }
        }

        val updatedArtistDetails = _uiState.value.selectedArtistDetails?.let { details ->
            details.copy(
                popularSongs = details.popularSongs.map {
                    if (HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id) it.copy(isFavorite = newFav) else it
                },
                latestReleases = details.latestReleases.map {
                    if (HomeUserSignalsStore.songIdentityKey(it) == key || it.id == song.id) it.copy(isFavorite = newFav) else it
                }
            )
        }

        _uiState.update {
            it.copy(
                songs = updatedSongs,
                recentlyPlayed = updatedRecent,
                favoriteSongs = currentFavList,
                currentSong = if (isCurrent) it.currentSong?.copy(isFavorite = newFav) else it.currentSong,
                isFavorite = if (isCurrent) newFav else it.isFavorite,
                activeHomeCollectionSheet = updatedSheet,
                selectedArtistDetails = updatedArtistDetails
            )
        }
        rebuildHomeSectionsSnapshot()
    }

    private fun setupSearchPipeline() {
        viewModelScope.launch {
            searchQueryStateFlow
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    executeSearch(query)
                }
        }
    }

    private fun executeSearch(query: String) {
        val trimmed = query.trim()
        val currentSource = _uiState.value.searchSource
        
        if (currentSource == SearchSource.DEVICE) {
            if (trimmed.isEmpty()) {
                _uiState.update {
                    it.copy(
                        deviceMatchingSongs = emptyList(),
                        deviceMatchingAlbums = emptyList(),
                        deviceMatchingArtists = emptyList(),
                        deviceMatchingFolders = emptyList(),
                        isLocalSearching = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLocalSearching = true) }
                viewModelScope.launch {
                    val results = localSearchIndexer.search(trimmed)
                    _uiState.update {
                        it.copy(
                            deviceMatchingSongs = results.songs,
                            deviceMatchingAlbums = results.albums,
                            deviceMatchingArtists = results.artists,
                            deviceMatchingFolders = results.folders,
                            isLocalSearching = false
                        )
                    }
                }
            }
        } else {
            searchOnline(trimmed, debounceMs = 0L)
        }
    }

    fun updateSearchQuery(query: String) {
        latestSearchQuery = query.trim()
        if (latestSearchQuery.isBlank() && _uiState.value.searchSource == SearchSource.ONLINE) {
            searchOnline("", debounceMs = 0L)
        }
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryStateFlow.value = query
    }

    fun setLibraryTab(tab: LibraryTab) {
        _uiState.update { it.copy(activeLibraryTab = tab) }
    }

    fun filterByAlbum(albumTitle: String?) {
        _uiState.update { it.copy(selectedAlbumFilter = albumTitle) }
    }

    fun filterByArtist(artistName: String?) {
        _uiState.update { it.copy(selectedArtistFilter = artistName) }
    }

    fun filterByFolder(folderName: String?) {
        _uiState.update { it.copy(selectedFolderFilter = folderName) }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedAlbumFilter = null,
                selectedArtistFilter = null,
                selectedFolderFilter = null
            )
        }
    }

    // --- Playlist & Queue Management ---

    fun createPlaylist(name: String, initialSong: Song? = null, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Playlist name cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(trimmed, initialSong)
                onResult(true, "Created playlist \"$trimmed\"")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onResult(false, "Failed to create playlist: ${e.message}")
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song, allowDuplicate: Boolean = false, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val (success, message) = playlistRepository.addSongToPlaylist(playlistId, song, allowDuplicate)
                onResult(success, message)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onResult(false, "Failed to add song: ${e.message}")
            }
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        playlistSongsJob?.cancel()
        if (playlist == null) {
            _uiState.update { it.copy(selectedPlaylist = null, selectedPlaylistSongs = emptyList()) }
            return
        }
        _uiState.update { it.copy(selectedPlaylist = playlist) }
        playlistSongsJob = viewModelScope.launch {
            playlistRepository.getSongsForPlaylist(playlist.id).collect { songs ->
                _uiState.update { it.copy(selectedPlaylistSongs = songs) }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                selectPlaylist(null)
            }
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, trimmed)
        }
    }

    fun playNext(song: Song, onFeedback: (String) -> Unit = {}) {
        if (!isAllowedUserFacingSong(song)) {
            onFeedback("Preview tracks cannot be queued")
            return
        }
        val controller = mediaController
        if (controller != null) {
            val item = createMediaItem(song)
            val nextIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(nextIndex, item)
            onFeedback("Playing next: ${song.title}")
        } else {
            onFeedback("Added to queue")
        }
    }

    fun addToQueue(song: Song, onFeedback: (String) -> Unit = {}) {
        if (!isAllowedUserFacingSong(song)) {
            onFeedback("Preview tracks cannot be queued")
            return
        }
        val controller = mediaController
        if (controller != null) {
            val item = createMediaItem(song)
            controller.addMediaItem(item)
            onFeedback("Added to queue: ${song.title}")
        } else {
            onFeedback("Added to queue")
        }
    }


    private fun onTrackEnded() {
        when (_uiState.value.repeatMode) {
            RepeatMode.ONE -> {
                mediaController?.seekTo(0)
                mediaController?.play()
            }
            RepeatMode.ALL -> {
                skipToNext()
            }
            RepeatMode.OFF -> {
                val current = _uiState.value
                val list = current.activePlaybackQueue.ifEmpty { current.songs }
                val currentIndex = list.indexOfFirst { it.id == current.currentSong?.id }
                if (currentIndex < list.size - 1 && currentIndex >= 0) {
                    skipToNext()
                } else {
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            while (isActive && _uiState.value.isPlaying) {
                val controller = mediaController
                val currentPos = controller?.currentPosition ?: _uiState.value.currentPositionMs
                val duration = controller?.duration?.takeIf { it > 0 } ?: _uiState.value.durationMs
                val buffered = controller?.bufferedPosition ?: _uiState.value.bufferedPositionMs
                val isPlaying = controller?.isPlaying ?: _uiState.value.isPlaying

                val isBuffering = controller?.playbackState == Player.STATE_BUFFERING
                val speed = controller?.playbackParameters?.speed ?: 1.0f

                // Update specialized progress flow (high-frequency)
                _playbackProgress.update {
                    it.copy(
                        currentPositionMs = currentPos.coerceAtLeast(0L),
                        durationMs = duration.coerceAtLeast(0L),
                        bufferedPositionMs = buffered.coerceAtLeast(0L),
                        isPlaying = isPlaying,
                        playbackSpeed = speed,
                        isBuffering = isBuffering,
                        updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                        updateTimeRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    )
                }

                delay(50)
            }
        }
    }

    private fun createMediaItem(s: Song): MediaItem {
        val rawUriString = s.contentUriString?.takeIf { it.isNotBlank() } ?: s.filePath
        val uriString = if (s.isOnline && rawUriString.startsWith("http://")) {
            rawUriString.replace("http://", "https://")
        } else {
            rawUriString
        }
        val uri = when {
            uriString.startsWith("content://") || uriString.startsWith("http://") || uriString.startsWith("https://") -> Uri.parse(uriString)
            else -> Uri.parse("file://$uriString")
        }

        val mimeType = when {
            s.isOnline -> {
                val path = uri.path?.lowercase().orEmpty()
                when {
                    path.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
                    path.endsWith(".flac") -> MimeTypes.AUDIO_FLAC
                    path.endsWith(".ogg") -> MimeTypes.AUDIO_OGG
                    path.endsWith(".aac") -> MimeTypes.AUDIO_AAC
                    path.endsWith(".mp4") || path.endsWith(".m4a") -> MimeTypes.AUDIO_MP4
                    else -> MimeTypes.AUDIO_MP4
                }
            }
            else -> null
        }

        val builder = MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(s.albumArtUri?.let { Uri.parse(it) })
                    .build()
            )

        if (!mimeType.isNullOrBlank()) {
            builder.setMimeType(mimeType)
        }

        return builder.build()
    }

    override fun onCleared() {

        super.onCleared()
        authStateListener?.let { listener ->
            firebaseAuth?.removeAuthStateListener(listener)
        }
        profileObserverJob?.cancel()
        profileSyncJob?.cancel()
        progressTrackingJob?.cancel()
        searchDebounceJob?.cancel()
        searchExecutionJob?.cancel()
        homeLoadJob?.cancel()
        categoryFetchJob?.cancel()
        playlistSongsJob?.cancel()
        homeSnapshotJob?.cancel()
        playJob?.cancel()
        AudioSpectrumManager.release()
        if (UsmanPlaybackService.currentAudioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            AudioEffectsManager.releaseEffects()
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    companion object {
        private const val SEARCH_PREFS_NAME = "usman_search_prefs"
        private const val KEY_SEARCH_HISTORY = "search_history_queries"
        private const val MAX_SEARCH_HISTORY = 12
        internal const val SEARCH_DEBOUNCE_MS = 280L

        internal fun isAllowedUserFacingSong(song: Song): Boolean {
            if (!song.isOnline) return true
            val provider = song.sourceProvider.orEmpty().trim()
            val isItunes = provider.equals("iTunes", ignoreCase = true) ||
                song.folderName.equals("iTunes Discovery", ignoreCase = true) ||
                song.folderName.equals("iTunes", ignoreCase = true) ||
                song.contentUriString?.contains("itunes.apple.com", ignoreCase = true) == true ||
                song.contentUriString?.contains("mzstatic.com", ignoreCase = true) == true
            if (isItunes) return false
            if (song.playbackType != PlaybackType.FULL) return false
            if (!song.isStreamable && song.contentUriString.isNullOrBlank()) return false
            return provider.equals("JioSaavn", ignoreCase = true)
        }

        internal fun isAllowedUserFacingAlbum(album: AlbumSearchResult): Boolean {
            val provider = album.sourceProvider.trim()
            if (provider.equals("iTunes", ignoreCase = true)) return false
            return provider.equals("JioSaavn", ignoreCase = true) || provider.equals("Device Library", ignoreCase = true)
        }

        internal fun isAllowedUserFacingArtist(artist: OnlineArtistResult): Boolean {
            val provider = artist.sourceProvider.trim()
            return !provider.equals("iTunes", ignoreCase = true) && provider.equals("JioSaavn", ignoreCase = true)
        }

        internal fun filterPlayableQueueTracks(tracks: List<Song>): List<Song> {
            return tracks.filter {
                isAllowedUserFacingSong(it) &&
                    (!it.isOnline || !it.contentUriString.isNullOrBlank() || it.isStreamable)
            }
        }
    }
}

