package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.data.local.DownloadEntity
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.AlbumDetails
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AlbumArtThumbnail
import com.usman.musicplayer.ui.components.GlassButtonSurface
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.components.UsmanHeader
import com.usman.musicplayer.ui.theme.*
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Composable
fun LibraryScreen(
    uiState: MusicPlayerUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onPlaySong: (Song) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onFolderSelected: (MusicFolder) -> Unit,
    onSavedAlbumSelected: (AlbumDetails) -> Unit = {},
    onSelectPlaylist: (Playlist?) -> Unit = {},
    onCreatePlaylistClick: () -> Unit = {},
    onDeletePlaylist: (Long) -> Unit = {},
    onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
    onRemoveSongFromPlaylist: (Long, Long) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onRescanLibrary: () -> Unit,
    onClearFilter: () -> Unit,
    onRemoveDownload: (Long) -> Unit = {},
    onPauseDownload: (Long) -> Unit = {},
    onResumeDownload: (Long) -> Unit = {},
    onCancelDownload: (Long) -> Unit = {},
    onRetryDownload: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val activeFilterTitle = when {
        uiState.selectedAlbumFilter != null -> "Album: ${uiState.selectedAlbumFilter}"
        uiState.selectedArtistFilter != null -> "Artist: ${uiState.selectedArtistFilter}"
        uiState.selectedFolderFilter != null -> "Folder: ${uiState.selectedFolderFilter}"
        else -> null
    }

    val displayedSongs = remember(
        uiState.songs,
        uiState.selectedAlbumFilter,
        uiState.selectedArtistFilter,
        uiState.selectedFolderFilter
    ) {
        when {
            uiState.selectedAlbumFilter != null -> uiState.songs.filter { it.album == uiState.selectedAlbumFilter }
            uiState.selectedArtistFilter != null -> uiState.songs.filter { it.artist == uiState.selectedArtistFilter }
            uiState.selectedFolderFilter != null -> uiState.songs.filter { it.folderName == uiState.selectedFolderFilter }
            else -> uiState.songs
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.28f),
                            ElectricBlue.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            UsmanHeader(
                onSearchClick = onNavigateToSearch,
                onMenuClick = onRescanLibrary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Your Library",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isScanning) "Scanning local storage..." else "Your music. Your vibe.",
                        color = if (uiState.isScanning) NeonCyan else TextSecondary,
                        fontSize = 14.sp
                    )
                }

                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    GlassButtonSurface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0x331E2338)
                    ) {
                        IconButton(
                            onClick = onRescanLibrary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Library",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selector Tabs
            LibraryTabsRow(
                activeTab = uiState.activeLibraryTab,
                onTabSelected = {
                    onClearFilter()
                    onTabSelected(it)
                }
            )

            // Active Filter Chip
            if (activeFilterTitle != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassPanel(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0x401E2544)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeFilterTitle,
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear filter",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(onClick = onClearFilter)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState.activeLibraryTab) {
                    LibraryTab.ALL_SONGS -> {
                        AllSongsList(
                            songs = displayedSongs,
                            currentSong = uiState.currentSong,
                            isPlaying = uiState.isPlaying,
                            hasPermission = uiState.hasStoragePermission,
                            onSongClick = onPlaySong,
                            onRescan = onRescanLibrary,
                            onOpenFolderPicker = onOpenFolderPicker
                        )
                    }
                    LibraryTab.PLAYLISTS -> {
                        if (uiState.selectedPlaylist == null) {
                            PlaylistsGrid(
                                playlists = uiState.playlists,
                                onCreatePlaylistClick = { showCreateDialog = true },
                                onPlaylistClick = { onSelectPlaylist(it) },
                                onDeletePlaylist = onDeletePlaylist,
                                onRenamePlaylist = { pl ->
                                    playlistToRename = pl
                                    renameInput = pl.name
                                }
                            )
                        } else {
                            PlaylistDetailView(
                                playlist = uiState.selectedPlaylist,
                                songs = uiState.selectedPlaylistSongs,
                                currentSong = uiState.currentSong,
                                isPlaying = uiState.isPlaying,
                                onBackClick = { onSelectPlaylist(null) },
                                onSongClick = onPlaySong,
                                onRemoveSong = { songId ->
                                    onRemoveSongFromPlaylist(uiState.selectedPlaylist.id, songId)
                                }
                            )
                        }
                    }
                    LibraryTab.ALBUMS -> {
                        AlbumsGrid(
                            albums = uiState.albums,
                            savedAlbums = uiState.savedAlbums,
                            onAlbumClick = onAlbumSelected,
                            onSavedAlbumClick = onSavedAlbumSelected
                        )
                    }
                    LibraryTab.ARTISTS -> {
                        ArtistsList(
                            artists = uiState.artists,
                            onArtistClick = onArtistSelected
                        )
                    }
                    LibraryTab.FOLDERS -> {
                        FoldersList(
                            folders = uiState.folders,
                            onFolderClick = onFolderSelected,
                            onAddSafFolder = onOpenFolderPicker
                        )
                    }
                    LibraryTab.DOWNLOADS -> {
                        DownloadsSection(
                            downloadedSongs = uiState.downloadedSongs,
                            currentSong = uiState.currentSong,
                            isPlaying = uiState.isPlaying,
                            onSongClick = onPlaySong,
                            onRemoveDownload = onRemoveDownload
                        )
                    }
                    LibraryTab.DOWNLOAD_CENTRE -> {
                        DownloadCentreSection(
                            activeDownloads = uiState.activeDownloads,
                            completedDownloads = uiState.completedDownloads,
                            onPause = onPauseDownload,
                            onResume = onResumeDownload,
                            onCancel = onCancelDownload,
                            onRetry = onRetryDownload,
                            onRemove = onRemoveDownload
                        )
                    }
                }
            }
        }

        // Create Playlist Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = Color(0xF10F1322),
                title = { Text("Create Playlist", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter playlist name:", color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            placeholder = { Text("Workout Hits", color = TextMuted) },
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
                            if (playlistNameInput.isNotBlank()) {
                                onCreatePlaylistClick()
                                playlistNameInput = ""
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White)
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextMuted) }
                }
            )
        }

        // Rename Playlist Dialog
        if (playlistToRename != null) {
            AlertDialog(
                onDismissRequest = { playlistToRename = null },
                containerColor = Color(0xF10F1322),
                title = { Text("Rename Playlist", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color(0x33FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            playlistToRename?.let { pl ->
                                onRenamePlaylist(pl.id, renameInput)
                            }
                            playlistToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playlistToRename = null }) { Text("Cancel", color = TextMuted) }
                }
            )
        }
    }
}

@Composable
private fun LibraryTabsRow(
    activeTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = LibraryTab.entries,
            key = { it.name },
            contentType = { "library_tab" }
        ) { tab ->
            val isSelected = tab == activeTab
            val shape = RoundedCornerShape(22.dp)

            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(ActivePillBrush, shape)
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)),
                                    shape
                                )
                        } else {
                            Modifier
                                .background(Color(0x3314182E), shape)
                                .border(1.dp, GlassBorderSubtle, shape)
                        }
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("library_tab_${tab.name}"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (tab) {
                        LibraryTab.ALL_SONGS -> Icons.Default.MusicNote
                        LibraryTab.PLAYLISTS -> Icons.Default.QueueMusic
                        LibraryTab.ALBUMS -> Icons.Default.Album
                        LibraryTab.ARTISTS -> Icons.Default.Person
                        LibraryTab.FOLDERS -> Icons.Default.Folder
                        LibraryTab.DOWNLOADS -> Icons.Default.Download
                        LibraryTab.DOWNLOAD_CENTRE -> Icons.Default.CloudDownload
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsGrid(
    playlists: List<Playlist>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Create Playlist Action Card
        item {
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onCreatePlaylistClick),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x331E2544),
                borderBrush = Brush.linearGradient(listOf(NeonCyan, ElectricBlue)),
                borderWidth = 1.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(NeonCyan, ElectricBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Create Playlist", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Build custom vibe", color = SoftCyan, fontSize = 11.sp)
                }
            }
        }

        items(playlists, key = { "playlist_${it.id}" }) { playlist ->
            var showMenu by remember { mutableStateOf(false) }

            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onPlaylistClick(playlist) },
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x2614182E)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playlist.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlist.songCount} tracks",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF14182E))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = NeonCyan) },
                                onClick = {
                                    showMenu = false
                                    onRenamePlaylist(playlist)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    onDeletePlaylist(playlist.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = playlist.name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "${songs.size} tracks", color = SoftCyan, fontSize = 12.sp)
            }
            if (songs.isNotEmpty()) {
                Button(
                    onClick = { onSongClick(songs.first()) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play All", fontSize = 12.sp)
                }
            }
        }

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QueueMusic, null, tint = SoftCyan, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No songs in this playlist yet.", color = TextSecondary, fontSize = 14.sp)
                    Text("Tap three-dot menu on any song to add it here!", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
            ) {
                items(songs, key = { "pl_song_${it.id}_${it.filePath}" }) { song ->
                    val isCurrent = currentSong?.id == song.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSongClick(song) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongAlbumArtwork(song = song, modifier = Modifier.size(48.dp), cornerRadius = 12.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isCurrent) NeonCyan else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveSong(song.id) }) {
                            Icon(Icons.Default.Delete, "Remove", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllSongsList(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    hasPermission: Boolean,
    onSongClick: (Song) -> Unit,
    onRescan: () -> Unit,
    onOpenFolderPicker: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyLibraryState(
            message = if (!hasPermission) "Storage permission is required to discover audio files." else "No audio files found. Supported formats include MP3, AAC, M4A, FLAC, WAV, and OGG.",
            showRescan = hasPermission,
            onRescan = onRescan,
            onOpenFolderPicker = onOpenFolderPicker
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
        ) {
            items(songs, key = { "all_song_${it.id}_${it.filePath}" }) { song ->
                val isCurrent = currentSong?.id == song.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSongClick(song) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("song_item_${song.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongAlbumArtwork(
                        song = song,
                        modifier = Modifier.size(52.dp),
                        cornerRadius = 14.dp,
                        showGlowBorder = isCurrent
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = if (isCurrent) NeonCyan else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${song.artist} • ${song.album}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isCurrent && isPlaying) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Playing",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = song.formattedDuration,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    savedAlbums: List<AlbumDetails> = emptyList(),
    onAlbumClick: (Album) -> Unit,
    onSavedAlbumClick: (AlbumDetails) -> Unit = {}
) {
    if (albums.isEmpty() && savedAlbums.isEmpty()) {
        EmptyLibraryState(message = "No albums saved or found in local storage.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(savedAlbums, key = { "saved_album_${it.sourceProvider}_${it.id}" }) { saved ->
                LiquidGlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSavedAlbumClick(saved) }
                        .testTag("saved_album_card_${saved.id}"),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x3314182E)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            AlbumArtThumbnail(
                                artworkUri = saved.artworkUri,
                                title = saved.title,
                                artist = saved.artist,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 14.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = saved.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${saved.artist} • ${saved.tracks.size.coerceAtLeast(saved.songCount)} tracks",
                            color = SoftCyan,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            items(albums, key = { "album_${it.id}_${it.title}" }) { album ->
                LiquidGlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onAlbumClick(album) }
                        .testTag("album_card_${album.id}"),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x2614182E)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF101326)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = album.title,
                                tint = NeonPurple.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = album.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${album.artist} • ${album.songCount} songs", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsList(artists: List<Artist>, onArtistClick: (Artist) -> Unit) {
    if (artists.isEmpty()) {
        EmptyLibraryState(message = "No artists found in local storage.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
        ) {
            items(artists, key = { "artist_${it.name}" }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onArtistClick(artist) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("artist_row_${artist.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0x331C2036))
                            .border(1.dp, NeonMagenta.copy(alpha = 0.5f), RoundedCornerShape(26.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = artist.name, tint = NeonMagenta, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = artist.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${artist.songCount} songs • ${artist.albumCount} albums", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersList(folders: List<MusicFolder>, onFolderClick: (MusicFolder) -> Unit, onAddSafFolder: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
    ) {
        item {
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onAddSafFolder)
                    .testTag("add_saf_folder_btn"),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = Color(0x33142038),
                borderBrush = Brush.horizontalGradient(listOf(NeonCyan, ElectricBlue)),
                borderWidth = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Browse Folder", tint = NeonCyan, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Browse Device Folder (SAF)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Pick specific music folder or SD card directory", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (folders.isEmpty()) {
            item { EmptyLibraryState(message = "No music folders detected.") }
        } else {
            items(folders, key = { "folder_${it.path}_${it.name}" }) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onFolderClick(folder) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("folder_row_${folder.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x3314182E))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = folder.name, tint = if (folder.isSafFolder) NeonCyan else ElectricBlue, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = folder.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${folder.songCount} songs • ${folder.path}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    message: String,
    showRescan: Boolean = false,
    onRescan: () -> Unit = {},
    onOpenFolderPicker: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = SoftCyan, modifier = Modifier.size(52.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = message, color = TextSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showRescan) {
                    Button(
                        onClick = onRescan,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Again")
                    }
                }
                if (onOpenFolderPicker != null) {
                    Button(
                        onClick = onOpenFolderPicker,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Folder (SAF)")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsSection(
    downloadedSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onRemoveDownload: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Offline Downloads",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${downloadedSongs.size} songs available offline",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (downloadedSongs.isNotEmpty()) {
                Button(
                    onClick = { onSongClick(downloadedSongs.random()) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Shuffle", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (downloadedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No downloaded songs yet",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Songs you download for offline playback will appear here and remain available after app restarts.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloadedSongs, key = { "dl_song_${it.id}" }) { song ->
                    val isCurrent = currentSong?.id == song.id
                    LiquidGlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSongClick(song) },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isCurrent) Color(0x331E2544) else Color(0x1A14182E),
                        borderBrush = if (isCurrent) Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)) else LiquidGlassBorderBrush,
                        borderWidth = if (isCurrent) 1.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                SongAlbumArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 12.dp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (isCurrent) NeonCyan else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${song.artist} • ${song.formattedDuration}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onRemoveDownload(song.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove download", tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCentreSection(
    activeDownloads: List<DownloadEntity>,
    completedDownloads: List<DownloadEntity>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit
) {
    var subTab by remember { mutableStateOf(0) } // 0: Active, 1: Completed

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Download Centre", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "${activeDownloads.size} active, ${completedDownloads.size} completed", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val activeCount = activeDownloads.size
            val completedCount = completedDownloads.size

            TabButton(title = "Active ($activeCount)", isSelected = subTab == 0, onClick = { subTab = 0 })
            TabButton(title = "Completed ($completedCount)", isSelected = subTab == 1, onClick = { subTab = 1 })
        }

        Spacer(modifier = Modifier.height(12.dp))

        val currentList = if (subTab == 0) activeDownloads else completedDownloads

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (subTab == 0) Icons.Default.CloudDownload else Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (subTab == 0) "No active downloads" else "No completed downloads",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { "dl_task_${it.songId}" }) { task ->
                    LiquidGlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x1A14182E)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    SongAlbumArtwork(
                                        song = Song(
                                            id = task.songId,
                                            title = task.title,
                                            artist = task.artist,
                                            album = task.album,
                                            durationMs = task.durationMs,
                                            filePath = task.filePath,
                                            albumArtUri = task.albumArtUri
                                        ),
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 10.dp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = task.artist,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when (task.status) {
                                                "COMPLETED" -> Color(0x3300E676)
                                                "DOWNLOADING" -> Color(0x3300E5FF)
                                                "PAUSED" -> Color(0x33F59E0B)
                                                "FAILED" -> Color(0x33F43F5E)
                                                else -> Color(0x3394A3B8)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = task.status,
                                        color = when (task.status) {
                                            "COMPLETED" -> Color(0xFF00E676)
                                            "DOWNLOADING" -> Color(0xFF00E5FF)
                                            "PAUSED" -> Color(0xFFFDE047)
                                            "FAILED" -> Color(0xFFF43F5E)
                                            else -> TextSecondary
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (task.status == "DOWNLOADING" || task.status == "PAUSED") {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { task.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = NeonCyan,
                                    trackColor = Color(0x33FFFFFF)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${task.progress}% (${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)})",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (task.status) {
                                    "DOWNLOADING" -> {
                                        TextButton(onClick = { onPause(task.songId) }) {
                                            Icon(Icons.Default.Pause, null, tint = Color(0xFFFDE047), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pause", color = Color(0xFFFDE047), fontSize = 12.sp)
                                        }
                                    }
                                    "PAUSED", "FAILED" -> {
                                        TextButton(onClick = { onResume(task.songId) }) {
                                            Icon(Icons.Default.PlayArrow, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Resume", color = NeonCyan, fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (task.status != "COMPLETED") {
                                    TextButton(onClick = { onCancel(task.songId) }) {
                                        Icon(Icons.Default.Close, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cancel", color = Color(0xFFF43F5E), fontSize = 12.sp)
                                    }
                                } else {
                                    TextButton(onClick = { onRemove(task.songId) }) {
                                        Icon(Icons.Default.Delete, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remove", color = Color(0xFFF43F5E), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) ActivePillBrush else Brush.linearGradient(listOf(Color(0x331E2338), Color(0x331E2338))), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format("%.1f MB", mb)
}
