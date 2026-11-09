package com.usman.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.usman.musicplayer.data.repository.ExternalServiceType
import com.usman.musicplayer.data.repository.MusicGenreCategory
import com.usman.musicplayer.data.repository.OnlineMusicRepository
import com.usman.musicplayer.data.repository.PlaylistRepository
import com.usman.musicplayer.data.repository.UnifiedMusicRepository

import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.RepeatMode
import com.usman.musicplayer.model.Song
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchSource {
    ONLINE,
    DEVICE
}

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
    val onlineSearchResults: List<Song> = emptyList(),
    val isOnlineSearching: Boolean = false,
    val onlineSearchError: String? = null,
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

    // Music Sources & Providers
    val musicProviders: List<com.usman.musicplayer.sdk.MusicProvider> = emptyList(),
    val enabledProviders: Map<String, Boolean> = emptyMap(),

    // Downloads
    val downloadedSongs: List<Song> = emptyList(),
    val activeDownloads: List<DownloadEntity> = emptyList(),
    val completedDownloads: List<DownloadEntity> = emptyList()
)

@UnstableApi
class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = LocalMusicScanner(application)
    private val playlistRepository = PlaylistRepository(application)
    private val musicRepository = UnifiedMusicRepository(application)
    private val downloadManager = SongDownloadManager(application)
    val localSearchIndexer = LocalSearchIndexer()
    private val searchQueryStateFlow = MutableStateFlow("")
    private var progressTrackingJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var searchExecutionJob: Job? = null
    private var latestSearchQuery: String = ""
    private var categoryFetchJob: Job? = null
    private var playlistSongsJob: Job? = null

    // Media3 MediaController connected to UsmanPlaybackService
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    // High-frequency playback progress flow to avoid root recomposition of entire app
    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    init {
        initializeMedia3Controller()
        loadInitialOnlineMusic()
        observePlaylists()
        observeDownloads()
        updateProviderStates()
        setupSearchPipeline()
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
            }
        }
    }

    fun downloadSong(song: Song) {
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
        val providers = com.usman.musicplayer.sdk.ProviderRegistry.providers
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
                            if (isPlaying) {
                                startProgressTracker()
                                val sessionId = UsmanPlaybackService.currentAudioSessionId
                                if (sessionId > 0) {
                                    AudioSpectrumManager.attachAudioSession(context, sessionId)
                                    AudioEffectsManager.attachAudioSession(context, sessionId)
                                }
                            } else {
                                AudioSpectrumManager.clearSpectrum()
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
                                
                                // Efficient lookup through specific lists to avoid giant flattened list construction
                                val song = state.songs.find { it.id == songId }
                                    ?: state.onlineSearchResults.find { it.id == songId }
                                    ?: state.categorySongs.find { it.id == songId }
                                    ?: state.recentlyPlayed.find { it.id == songId }
                                    ?: state.onlineTrendingSongs.find { it.id == songId }
                                    ?: state.onlineIndianSongs.find { it.id == songId }
                                    ?: state.onlinePhonkSongs.find { it.id == songId }
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
                        AudioSpectrumManager.attachAudioSession(context, sessionId)
                        AudioEffectsManager.attachAudioSession(context, sessionId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    // --- Online Music Loading & Discovery ---

    fun loadInitialOnlineMusic() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOnlineLoading = true,
                    isTeluguLoading = true,
                    onlineTeluguError = null
                )
            }

            // 1. Trending Worldwide
            val trendingJob = launch {
                OnlineMusicRepository.searchOnline("top global hits", limit = 20)
                    .onSuccess { list ->
                        _uiState.update { it.copy(onlineTrendingSongs = list) }
                    }
            }

            // 2. Telugu Music Hits
            val teluguJob = launch {
                OnlineMusicRepository.searchOnline("telugu hits tollywood", country = "in", limit = 25)
                    .onSuccess { list ->
                        _uiState.update {
                            it.copy(
                                onlineTeluguSongs = list,
                                isTeluguLoading = false,
                                onlineTeluguError = if (list.isEmpty()) "No Telugu tracks found" else null
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isTeluguLoading = false,
                                onlineTeluguError = "Failed to load Telugu music. Tap to retry."
                            )
                        }
                    }
            }

            // 3. Bollywood / Hindi Hits
            val bollywoodJob = launch {
                OnlineMusicRepository.searchOnline("bollywood hindi hits", country = "in", limit = 20)
                    .onSuccess { list ->
                        _uiState.update { it.copy(onlineBollywoodSongs = list, onlineIndianSongs = list) }
                    }
            }

            // 4. Phonk Station
            val phonkJob = launch {
                OnlineMusicRepository.searchOnline("phonk drift brazilian", limit = 20)
                    .onSuccess { list ->
                        _uiState.update { it.copy(onlinePhonkSongs = list) }
                    }
            }

            // 5. English Hits
            val englishJob = launch {
                OnlineMusicRepository.searchOnline("english pop hits top 40", limit = 20)
                    .onSuccess { list ->
                        _uiState.update { it.copy(onlineEnglishSongs = list) }
                    }
            }

            // 6. Global New Releases
            val globalJob = launch {
                OnlineMusicRepository.searchOnline("new music releases 2026", limit = 25)
                    .onSuccess { list ->
                        _uiState.update { it.copy(onlineGlobalSongs = list) }
                    }
            }

            joinAll(trendingJob, teluguJob, bollywoodJob, phonkJob, englishJob, globalJob)

            buildCuratedPlaylists()
            _uiState.update { it.copy(isOnlineLoading = false) }
        }
    }

    fun retryLoadTeluguMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTeluguLoading = true, onlineTeluguError = null) }
            OnlineMusicRepository.searchOnline("telugu hits tollywood", country = "in", limit = 25)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            onlineTeluguSongs = list,
                            isTeluguLoading = false,
                            onlineTeluguError = if (list.isEmpty()) "No Telugu tracks found" else null
                        )
                    }
                    buildCuratedPlaylists()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isTeluguLoading = false,
                            onlineTeluguError = "Failed to load Telugu music. Tap to retry."
                        )
                    }
                }
        }
    }



    private fun buildCuratedPlaylists() {
        val state = _uiState.value
        val list = mutableListOf<CuratedPlaylist>()



        if (state.onlineTrendingSongs.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_daily_mix",
                    title = "Daily Mix 1",
                    description = "Top global chartbusters curated for you",
                    coverArtUri = state.onlineTrendingSongs.firstOrNull()?.albumArtUri,
                    songs = state.onlineTrendingSongs
                )
            )
        }

        if (state.onlineTeluguSongs.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_telugu_mix",
                    title = "Tollywood & Chill",
                    description = "Best Telugu romantic & dance tracks",
                    coverArtUri = state.onlineTeluguSongs.firstOrNull()?.albumArtUri,
                    songs = state.onlineTeluguSongs
                )
            )
        }

        if (state.onlineBollywoodSongs.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_bollywood_mix",
                    title = "Bollywood Romance",
                    description = "Hindi romantic hits and acoustic vibes",
                    coverArtUri = state.onlineBollywoodSongs.firstOrNull()?.albumArtUri,
                    songs = state.onlineBollywoodSongs
                )
            )
        }

        if (state.onlinePhonkSongs.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_phonk_mix",
                    title = "Phonk Night Drive",
                    description = "Drift, Brazilian & Aggressive bass",
                    coverArtUri = state.onlinePhonkSongs.firstOrNull()?.albumArtUri,
                    songs = state.onlinePhonkSongs
                )
            )
        }

        if (state.onlineEnglishSongs.isNotEmpty()) {
            list.add(
                CuratedPlaylist(
                    id = "curated_discovery",
                    title = "Discovery Station",
                    description = "Find your new favorite sound",
                    coverArtUri = state.onlineEnglishSongs.firstOrNull()?.albumArtUri,
                    songs = state.onlineEnglishSongs
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
                                categorySongs = songs,
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

    fun searchOnline(query: String, debounceMs: Long = 300L) {
        val trimmed = query.trim()
        latestSearchQuery = trimmed
        if (trimmed.isBlank()) {
            searchDebounceJob?.cancel()
            searchExecutionJob?.cancel()
            _uiState.update {
                it.copy(
                    onlineSearchResults = emptyList(),
                    isOnlineSearching = false,
                    onlineSearchError = null
                )
            }
            return
        }

        searchDebounceJob?.cancel()
        searchExecutionJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            if (trimmed != latestSearchQuery) return@launch

            _uiState.update { it.copy(isOnlineSearching = true, onlineSearchError = null) }
            val startTime = System.currentTimeMillis()
            
            searchExecutionJob = launch {
                try {
                    val songs = musicRepository.searchAll(trimmed, limit = 35)
                    val totalElapsed = System.currentTimeMillis() - startTime
                    Log.d("SearchTiming", "Total search query '$trimmed' completed in ${totalElapsed}ms, returning ${songs.size} tracks")

                    if (trimmed != latestSearchQuery) return@launch

                    _uiState.update {
                        it.copy(
                            onlineSearchResults = songs,
                            isOnlineSearching = false,
                            onlineSearchError = if (songs.isEmpty()) "No tracks found for \"$trimmed\"" else null
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w("MusicPlayerVM", "Search failed for '$trimmed': ${e.message}")
                    if (trimmed == latestSearchQuery) {
                        _uiState.update {
                            it.copy(
                                isOnlineSearching = false,
                                onlineSearchError = "Search failed: ${e.localizedMessage ?: "Unknown error"}"
                            )
                        }
                    }
                }
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

    fun playSong(song: Song) {
        // [Stage 3: playSong() input]
        Log.d("USMAN_PLAYBACK", "[Stage 3: playSong() input] ID: ${song.id}, Title: '${song.title}', Artist: '${song.artist}', Album: '${song.album}', Artwork: '${song.albumArtUri}', Duration: ${song.durationMs}")

        // Immediately update UI state to avoid placeholders
        _uiState.update {
            it.copy(
                currentSong = song,
                isPlaying = true,
                onlineSearchError = null
            )
        }

        viewModelScope.launch {
            val controller = mediaController

            // If song requires external playback only
            if (song.playbackType == PlaybackType.EXTERNAL_SERVICE && song.contentUriString.isNullOrBlank()) {
                Log.w("USMAN_PLAYBACK", "Song requires external service playback handoff without direct stream URL")
                return@launch
            }

            // Identify the active playlist queue for next/previous navigation
            val currentList = when {
                _uiState.value.onlineSearchResults.any { it.id == song.id } -> _uiState.value.onlineSearchResults
                _uiState.value.categorySongs.any { it.id == song.id } -> _uiState.value.categorySongs
                _uiState.value.onlineTrendingSongs.any { it.id == song.id } -> _uiState.value.onlineTrendingSongs
                _uiState.value.onlineIndianSongs.any { it.id == song.id } -> _uiState.value.onlineIndianSongs
                _uiState.value.onlinePhonkSongs.any { it.id == song.id } -> _uiState.value.onlinePhonkSongs
                _uiState.value.onlineEnglishSongs.any { it.id == song.id } -> _uiState.value.onlineEnglishSongs
                _uiState.value.onlineGlobalSongs.any { it.id == song.id } -> _uiState.value.onlineGlobalSongs
                _uiState.value.songs.any { it.id == song.id } -> _uiState.value.songs
                else -> listOf(song)
            }

            // Resolve stream URLs concurrently for online songs in queue
            val resolvedList = currentList.map { s ->
                async {
                    var updated = s
                    if (updated.isOnline && updated.contentUriString.isNullOrBlank() && updated.playbackType == PlaybackType.FULL) {
                        val streamResult = musicRepository.getStreamUrl(updated)
                        if (streamResult.isSuccess) {
                            val resolvedUrl = streamResult.getOrNull().orEmpty()
                            if (resolvedUrl.isNotBlank()) {
                                val sanitizedHost = runCatching { Uri.parse(resolvedUrl).host }.getOrNull() ?: "unknown"
                                Log.d("USMAN_PLAYBACK", "[Stage 4: After stream URL resolution] ID: ${updated.id}, Title: '${updated.title}', StreamUrl: $resolvedUrl (host: $sanitizedHost)")
                                updated = updated.copy(contentUriString = resolvedUrl)
                            } else {
                                Log.w("USMAN_PLAYBACK", "Stream resolution returned blank URL for '${updated.title}'")
                            }
                        } else {
                            Log.e("USMAN_PLAYBACK", "Stream resolution failed for '${updated.title}': ${streamResult.exceptionOrNull()?.message}")
                        }
                    }
                    updated
                }
            }.awaitAll().filter { s ->
                if (s.isOnline && s.contentUriString.isNullOrBlank()) {
                    Log.w("USMAN_PLAYBACK", "Dropping unavailable online song from queue: '${s.title}'")
                    false
                } else {
                    true
                }
            }

            val playableSong = resolvedList.find { it.id == song.id } ?: resolvedList.firstOrNull()
            if (playableSong == null || (playableSong.isOnline && playableSong.contentUriString.isNullOrBlank())) {
                Log.e("USMAN_PLAYBACK", "Playback failure: Could not resolve playable stream for '${song.title}'")
                _uiState.update { it.copy(onlineSearchError = "Unable to play \"${song.title}\": Stream unavailable") }
                return@launch
            }

            val updatedRecent = (_uiState.value.recentlyPlayed.filter { it.id != playableSong.id }
                .toMutableList()).apply { add(0, playableSong) }.take(15)

            _uiState.update {
                it.copy(
                    currentSong = playableSong,
                    isPlaying = true,
                    durationMs = playableSong.durationMs,
                    currentPositionMs = 0L,
                    isFavorite = playableSong.isFavorite,
                    recentlyPlayed = updatedRecent,
                    onlineSearchError = null
                )
            }

            if (controller != null) {
                val queue = resolvedList.map { s ->
                    Log.d("USMAN_PLAYBACK", "[Stage 5: Active queue item] ID: ${s.id}, Title: '${s.title}', Artist: '${s.artist}', Album: '${s.album}', ContentUri: ${s.contentUriString}")
                    createMediaItem(s)
                }


                val startIndex = queue.indexOfFirst { it.mediaId == playableSong.id.toString() }
                controller.setMediaItems(queue, startIndex.coerceAtLeast(0), 0L)
                controller.prepare()
                controller.play()
                controller.volume = _uiState.value.volume
            }

            startProgressTracker()
        }
    }

    private fun onSongTransitioned(song: Song) {
        val updatedRecent = (_uiState.value.recentlyPlayed.filter { it.id != song.id }
            .toMutableList()).apply { add(0, song) }.take(15)

        _uiState.update {
            it.copy(
                currentSong = song,
                durationMs = song.durationMs,
                currentPositionMs = 0L,
                isFavorite = song.isFavorite,
                recentlyPlayed = updatedRecent
            )
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

        // Fallback to in-memory list
        val current = _uiState.value
        val list = current.songs
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.currentSong?.id }
        val nextIndex = if (current.isShuffleEnabled) {
            (list.indices).filter { it != currentIndex }.randomOrNull() ?: 0
        } else {
            (currentIndex + 1) % list.size
        }
        playSong(list[nextIndex])
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            return
        }

        val current = _uiState.value
        val list = current.songs
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.currentSong?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playSong(list[prevIndex])
    }

    fun seekTo(positionMs: Long) {
        val validPos = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(validPos)
        _uiState.update { it.copy(currentPositionMs = validPos) }
        _playbackProgress.update {
            it.copy(
                currentPositionMs = validPos,
                updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime()
            )
        }
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
        val newFav = !song.isFavorite
        val updatedSongs = _uiState.value.songs.map {
            if (it.id == song.id) it.copy(isFavorite = newFav) else it
        }
        val isCurrent = _uiState.value.currentSong?.id == song.id
        _uiState.update {
            it.copy(
                songs = updatedSongs,
                currentSong = if (isCurrent) it.currentSong?.copy(isFavorite = newFav) else it.currentSong,
                isFavorite = if (isCurrent) newFav else it.isFavorite
            )
        }
    }

    private fun setupSearchPipeline() {
        viewModelScope.launch {
            searchQueryStateFlow
                .debounce(150L)
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
                val currentIndex = current.songs.indexOfFirst { it.id == current.currentSong?.id }
                if (currentIndex < current.songs.size - 1 && currentIndex >= 0) {
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

                // Update specialized progress flow (high-frequency)
                _playbackProgress.update {
                    it.copy(
                        currentPositionMs = currentPos.coerceAtLeast(0L),
                        durationMs = duration.coerceAtLeast(0L),
                        bufferedPositionMs = buffered.coerceAtLeast(0L),
                        isPlaying = isPlaying,
                        updateTimeRealtimeMs = android.os.SystemClock.elapsedRealtime()
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
        progressTrackingJob?.cancel()
        searchDebounceJob?.cancel()
        categoryFetchJob?.cancel()
        playlistSongsJob?.cancel()
        AudioSpectrumManager.release()
        AudioEffectsManager.releaseEffects()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}

