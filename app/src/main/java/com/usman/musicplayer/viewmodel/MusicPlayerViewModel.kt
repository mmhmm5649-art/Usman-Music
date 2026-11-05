package com.usman.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.usman.musicplayer.data.LocalMusicScanner
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.RepeatMode
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.playback.AudioSpectrumManager
import com.usman.musicplayer.playback.UsmanPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicPlayerUiState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<MusicFolder> = emptyList(),
    val safFolders: List<MusicFolder> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
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
    val selectedFolderFilter: String? = null
)

@UnstableApi
class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = LocalMusicScanner(application)
    private var progressTrackingJob: Job? = null

    // Media3 MediaController connected to UsmanPlaybackService
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    init {
        initializeMedia3Controller()
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
                                val songId = mediaItem.mediaId.toLongOrNull()
                                val song = _uiState.value.songs.find { it.id == songId }
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
                            } else if (playbackState == Player.STATE_BUFFERING) {
                                _uiState.update { it.copy(bufferedPositionMs = buffered) }
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
                    })

                    // Attach audio session if already playing
                    val sessionId = UsmanPlaybackService.currentAudioSessionId
                    if (sessionId > 0) {
                        AudioSpectrumManager.attachAudioSession(context, sessionId)
                    }
                } catch (e: Exception) {
                    // Service binding error or interrupted
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

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

            // 1. Scan device MediaStore (Internal storage + SD card)
            val mediaStoreSongs = if (_uiState.value.hasStoragePermission) {
                scanner.scanLocalAudio()
            } else {
                emptyList()
            }

            // 2. Scan SAF picked folders
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

            val albums = LocalMusicScanner.extractAlbums(allSongs)
            val artists = LocalMusicScanner.extractArtists(allSongs)
            val folders = LocalMusicScanner.extractFolders(allSongs, _uiState.value.safFolders)

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
        viewModelScope.launch {
            val controller = mediaController
            val targetUri: Uri = when {
                !song.contentUriString.isNullOrBlank() -> Uri.parse(song.contentUriString)
                song.filePath.startsWith("content://") -> Uri.parse(song.filePath)
                else -> Uri.parse("file://${song.filePath}")
            }

            // Build MediaItem with rich metadata for notification & lockscreen
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(targetUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()

            val updatedRecent = (_uiState.value.recentlyPlayed.filter { it.id != song.id }
                .toMutableList()).apply { add(0, song) }.take(10)

            _uiState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = true,
                    durationMs = song.durationMs,
                    currentPositionMs = 0L,
                    isFavorite = song.isFavorite,
                    recentlyPlayed = updatedRecent
                )
            }

            if (controller != null) {
                // Populate playback queue starting from clicked song
                val queue = _uiState.value.songs.map { s ->
                    val uri = when {
                        !s.contentUriString.isNullOrBlank() -> Uri.parse(s.contentUriString)
                        s.filePath.startsWith("content://") -> Uri.parse(s.filePath)
                        else -> Uri.parse("file://${s.filePath}")
                    }
                    MediaItem.Builder()
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
                        .build()
                }

                val startIndex = queue.indexOfFirst { it.mediaId == song.id.toString() }
                controller.setMediaItems(queue, startIndex.coerceAtLeast(0), 0L)
                controller.prepare()
                controller.play()
                controller.volume = _uiState.value.volume

                AudioSpectrumManager.attachAudioSession(getApplication(), UsmanPlaybackService.currentAudioSessionId)
            }

            startProgressTracker()
        }
    }

    private fun onSongTransitioned(song: Song) {
        val updatedRecent = (_uiState.value.recentlyPlayed.filter { it.id != song.id }
            .toMutableList()).apply { add(0, song) }.take(10)

        _uiState.update {
            it.copy(
                currentSong = song,
                isPlaying = mediaController?.isPlaying ?: true,
                durationMs = song.durationMs,
                currentPositionMs = 0L,
                isFavorite = song.isFavorite,
                recentlyPlayed = updatedRecent
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller == null) return

        if (_uiState.value.currentSong == null) {
            _uiState.value.songs.firstOrNull()?.let { playSong(it) }
            return
        }

        if (controller.isPlaying) {
            controller.pause()
            _uiState.update { it.copy(isPlaying = false) }
            AudioSpectrumManager.clearSpectrum()
        } else {
            controller.play()
            _uiState.update { it.copy(isPlaying = true) }
            AudioSpectrumManager.attachAudioSession(getApplication(), UsmanPlaybackService.currentAudioSessionId)
            startProgressTracker()
        }
    }

    fun skipToNext() {
        val controller = mediaController
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else {
            // Manual fallback
            val songsList = _uiState.value.songs
            if (songsList.isEmpty()) return
            val currentIndex = songsList.indexOfFirst { it.id == _uiState.value.currentSong?.id }
            val nextIndex = if (_uiState.value.isShuffleEnabled) {
                songsList.indices.random()
            } else {
                if (currentIndex == -1 || currentIndex == songsList.size - 1) 0 else currentIndex + 1
            }
            playSong(songsList[nextIndex])
        }
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            val songsList = _uiState.value.songs
            if (songsList.isEmpty()) return
            val currentIndex = songsList.indexOfFirst { it.id == _uiState.value.currentSong?.id }
            val prevIndex = if (currentIndex <= 0) songsList.size - 1 else currentIndex - 1
            playSong(songsList[prevIndex])
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun toggleShuffle() {
        val controller = mediaController
        val newShuffle = !_uiState.value.isShuffleEnabled
        controller?.shuffleModeEnabled = newShuffle
        _uiState.update { it.copy(isShuffleEnabled = newShuffle) }
    }

    fun toggleRepeat() {
        val controller = mediaController
        val nextRepeat = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        val exoRepeatMode = when (nextRepeat) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }

        controller?.repeatMode = exoRepeatMode
        _uiState.update { it.copy(repeatMode = nextRepeat) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        mediaController?.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
    }

    fun toggleFavorite(song: Song) {
        _uiState.update { current ->
            val updatedSongs = current.songs.map {
                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
            }
            current.copy(
                songs = updatedSongs,
                isFavorite = if (current.currentSong?.id == song.id) !current.isFavorite else current.isFavorite
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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
                if (currentIndex < current.songs.size - 1) {
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

                _uiState.update {
                    it.copy(
                        currentPositionMs = currentPos.coerceAtLeast(0L),
                        durationMs = duration.coerceAtLeast(0L),
                        bufferedPositionMs = buffered.coerceAtLeast(0L),
                        isPlaying = isPlaying
                    )
                }
                delay(250)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackingJob?.cancel()
        AudioSpectrumManager.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
